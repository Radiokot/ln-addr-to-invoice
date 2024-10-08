package ua.com.radiokot.lnaddr2invoice.view

import android.content.ClipData
import android.content.ClipboardManager
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf
import ua.com.radiokot.lnaddr2invoice.base.extension.checkNotNull
import ua.com.radiokot.lnaddr2invoice.base.extension.kLogger
import ua.com.radiokot.lnaddr2invoice.data.CreatedInvoicesCounter
import ua.com.radiokot.lnaddr2invoice.data.QuickAmountsStorage
import ua.com.radiokot.lnaddr2invoice.data.TipStateStorage
import ua.com.radiokot.lnaddr2invoice.logic.GetBolt11InvoiceUseCase
import ua.com.radiokot.lnaddr2invoice.logic.GetUsernameInfoUseCase
import ua.com.radiokot.lnaddr2invoice.model.UsernameInfo
import java.math.BigInteger
import java.util.concurrent.TimeUnit

class MainViewModel(
    private val authorTipAddress: String,
    private val createdInvoicesCounter: CreatedInvoicesCounter,
    private val tipStateStorage: TipStateStorage,
    private val quickAmountsStorage: QuickAmountsStorage,
    private val tipEveryNthInvoice: Int,
    private val clipboardManager: ClipboardManager?,
) : ViewModel(), KoinComponent {
    init {
        require(tipEveryNthInvoice > 0) {
            "The value must be positive"
        }
    }

    private val compositeDisposable = CompositeDisposable()
    private val log = kLogger("MainVM")

    val state = MutableLiveData<State>()
    val enteredAmount = MutableLiveData<String>()
    val enteredAmountError = MutableLiveData<EnteredAmountError>(EnteredAmountError.None)
    val isCopyInvoiceChecked = MutableLiveData(true)
    val canPay = MutableLiveData(false)

    /**
     * A list of 3 amount options for quick input.
     */
    val quickAmounts = MutableLiveData(
        quickAmountsStorage.quickAmounts ?: listOf(200L, 500L, 1000L)
    )

    private var isTipping = false
    private var suggestedTipping = false
    private val parsedAmount: BigInteger
        get() = enteredAmount.value?.toString()?.toBigIntegerOrNull() ?: BigInteger.ZERO
    private var lastExplicitlyLoadedUsernameInfo: UsernameInfo? = null

    sealed interface State {
        sealed interface Loading : State {
            class LoadingUsernameInfo(val address: String) : Loading
            object CreatingInvoice : Loading
        }

        sealed interface Final : State {
            object FailedCreatingInvoice : Final
            class FailedLoadingUsernameInfo(
                val error: Throwable,
                val invoiceStringToLaunch: String?,
            ) : Final

            object Finish : Final
        }

        class DoneLoadingUsernameInfo(val usernameInfo: UsernameInfo) : State
        class DoneCreatingInvoice(val invoiceString: String) : State
        object Tip : State
    }

    sealed class EnteredAmountError {
        class TooSmall(val minAmount: BigInteger) : EnteredAmountError()
        class TooBig(val maxAmount: BigInteger) : EnteredAmountError()
        object None : EnteredAmountError()
    }

    init {
        initEnteredAmountValidation()
    }

    private fun initEnteredAmountValidation() {
        enteredAmount.observeForever { enteredAmount ->
            val usernameInfo = (state.value as? State.DoneLoadingUsernameInfo)?.usernameInfo
                ?: return@observeForever
            val parsedAmount = this.parsedAmount

            when {
                enteredAmount.isNullOrEmpty() -> {
                    enteredAmountError.value = EnteredAmountError.None
                }

                parsedAmount < usernameInfo.minSendableSat -> {
                    enteredAmountError.value =
                        EnteredAmountError.TooSmall(usernameInfo.minSendableSat)
                }

                parsedAmount > usernameInfo.maxSendableSat -> {
                    enteredAmountError.value =
                        EnteredAmountError.TooBig(usernameInfo.maxSendableSat)
                }

                else -> {
                    enteredAmountError.value = EnteredAmountError.None
                }
            }

            canPay.value =
                parsedAmount > BigInteger.ZERO
                        && enteredAmountError.value == EnteredAmountError.None
        }
    }

    fun onCancel() {
        val fallbackUsernameInfo = lastExplicitlyLoadedUsernameInfo

        when {
            (state.value is State.Tip || isTipping)
                    && !suggestedTipping
                    && fallbackUsernameInfo != null -> {
                isTipping = false
                cancelUsernameInfoLoading()
                cancelInvoiceCreation()
                onDoneLoadingUsernameInfo(fallbackUsernameInfo)
            }

            else -> {
                state.value = State.Final.Finish
            }
        }
    }

    private var loadUsernameInfoDisposable: Disposable? = null
    fun loadUsernameInfo(rawAddress: String) {
        val address = rawAddress
            .trim()
            .trim('/')

        log.debug {
            "loadUsernameInfo(): begin_loading:" +
                    "\naddress=$address"
        }

        val useCase: GetUsernameInfoUseCase = get {
            parametersOf(address)
        }

        loadUsernameInfoDisposable?.dispose()
        loadUsernameInfoDisposable = Single.zip(
            useCase.perform(),
            Single.timer(500, TimeUnit.MILLISECONDS)
        ) { usernameInfo, _ -> usernameInfo }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { state.value = State.Loading.LoadingUsernameInfo(address) }
            .subscribeBy(
                onSuccess = { usernameInfo ->
                    log.debug {
                        "loadUsernameInfo(): loaded:" +
                                "\ninfo=$usernameInfo"
                    }

                    onDoneLoadingUsernameInfo(usernameInfo)
                },
                onError = { error ->
                    log.error(error) {
                        "loadUsernameInfo(): loading_failed"
                    }

                    state.value = State.Final.FailedLoadingUsernameInfo(
                        error = error,
                        invoiceStringToLaunch =
                        if (error is GetUsernameInfoUseCase.AddressIsAnInvoiceException)
                            rawAddress
                        else
                            null,
                    )
                }
            )
            .addTo(compositeDisposable)
    }

    private fun cancelUsernameInfoLoading() {
        loadUsernameInfoDisposable?.dispose()
    }

    private fun onDoneLoadingUsernameInfo(usernameInfo: UsernameInfo) {
        if (!isTipping) {
            lastExplicitlyLoadedUsernameInfo = usernameInfo
        }
        enteredAmount.value = ""
        state.value = State.DoneLoadingUsernameInfo(usernameInfo)
    }

    private var createInvoiceDisposable: Disposable? = null
    fun createInvoice() {
        val usernameInfo = (state.value as? State.DoneLoadingUsernameInfo)?.usernameInfo
            .checkNotNull {
                "There is no loaded username info to create an invoice for"
            }

        val amountSat = parsedAmount
        val copyToClipboard = isCopyInvoiceChecked.value == true

        log.debug {
            "createInvoice(): begin_creation:" +
                    "\namountSat=$amountSat," +
                    "\nusernameInfo=$usernameInfo," +
                    "\ncopyToClipboard=$copyToClipboard"
        }

        val useCase = get<GetBolt11InvoiceUseCase> {
            parametersOf(amountSat, usernameInfo.callbackUrl)
        }

        createInvoiceDisposable?.dispose()
        createInvoiceDisposable = Single.zip(
            useCase.perform(),
            Single.timer(500, TimeUnit.MILLISECONDS)
        ) { invoiceString, _ -> invoiceString }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe {
                state.value = State.Loading.CreatingInvoice
            }
            .subscribeBy(
                onSuccess = { invoiceString ->
                    createdInvoicesCounter.incrementCreatedInvoices()

                    log.debug {
                        "createInvoice(): created:" +
                                "\ninvoiceString=$invoiceString," +
                                "\ncreatedInvoicesCount=${createdInvoicesCounter.createdInvoiceCount}"
                    }

                    if (copyToClipboard && clipboardManager != null) {
                        clipboardManager.setPrimaryClip(
                            ClipData.newPlainText(
                                "Bitcoin Lightning invoice",
                                invoiceString,
                            )
                        )

                        log.debug {
                            "createInvoice(): copied_to_clipboard"
                        }
                    } else if (copyToClipboard) {
                        log.warn {
                            "createInvoice(): cant_copy_without_clipboard_manager"
                        }
                    }

                    state.value = State.DoneCreatingInvoice(invoiceString)
                },
                onError = { error ->
                    log.error(error) {
                        "createInvoice(): creation_failed"
                    }

                    state.value = State.Final.FailedCreatingInvoice
                }
            )
            .addTo(compositeDisposable)
    }

    private fun cancelInvoiceCreation() {
        createInvoiceDisposable?.dispose()
    }

    fun onBottomLabelClicked() {
        if (state.value is State.DoneLoadingUsernameInfo) {
            toTip()
        }
    }

    private fun toTip() {
        enteredAmount.value = ""
        state.value = State.Tip
    }

    fun onInvoicePaymentLaunched() {
        val suggestTip =
            !isTipping
                    && !tipStateStorage.isEverTipped
                    && createdInvoicesCounter.createdInvoiceCount % tipEveryNthInvoice == 0

        log.debug {
            "onInvoicePaymentLaunched(): launched:" +
                    "\nsuggestTip=$suggestTip," +
                    "\neverTipped=${tipStateStorage.isEverTipped}" +
                    "\nisTipping=$isTipping"
        }

        if (isTipping) {
            tipStateStorage.isEverTipped = true
        }

        if (suggestTip) {
            suggestedTipping = true
            state.value = State.Tip
        } else {
            state.value = State.Final.Finish
        }
    }

    fun tip() {
        log.debug {
            "tip(): make_a_tip:" +
                    "\nauthorTipAddress=$authorTipAddress"
        }

        isTipping = true
        loadUsernameInfo(authorTipAddress)
    }

    fun updateQuickAmount(
        index: Int,
        newValue: Long,
    ) {
        val updatedList = quickAmounts.value!!.toMutableList()
        updatedList[index] = newValue
        quickAmounts.postValue(updatedList)
        quickAmountsStorage.quickAmounts = updatedList
    }

    override fun onCleared() {
        super.onCleared()
        compositeDisposable.dispose()
    }
}
