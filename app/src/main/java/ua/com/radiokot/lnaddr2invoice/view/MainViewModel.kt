package ua.com.radiokot.lnaddr2invoice.view

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
import ua.com.radiokot.lnaddr2invoice.base.extension.kLogger
import ua.com.radiokot.lnaddr2invoice.data.CreatedInvoicesCounter
import ua.com.radiokot.lnaddr2invoice.data.TipStateStorage
import ua.com.radiokot.lnaddr2invoice.logic.GetBolt11InvoiceUseCase
import ua.com.radiokot.lnaddr2invoice.logic.GetUsernameInfoUseCase
import ua.com.radiokot.lnaddr2invoice.model.UsernameInfo
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

class MainViewModel(
    private val authorTipAddress: String,
    private val createdInvoicesCounter: CreatedInvoicesCounter,
    private val tipStateStorage: TipStateStorage,
    private val tipEveryNthInvoice: Int,
) : ViewModel(), KoinComponent {
    init {
        require(tipEveryNthInvoice > 0) {
            "The value must be positive"
        }
    }

    private val compositeDisposable = CompositeDisposable()
    private val log = kLogger("MainVM")

    val state: MutableLiveData<State> = MutableLiveData()
    val enteredAmount: MutableLiveData<CharSequence?> = MutableLiveData(null)
    val enteredAmountError: MutableLiveData<EnteredAmountError> =
        MutableLiveData(EnteredAmountError.None)
    val canPay: MutableLiveData<Boolean> = MutableLiveData(false)

    private var isTipping: Boolean = false
    private val parsedAmount: BigDecimal
        get() = BigDecimal(enteredAmount.value?.toString()?.toLongOrNull() ?: 0L)
    private var lastExplicitlyLoadedUsernameInfo: UsernameInfo? = null

    sealed class State {
        class LoadingUsernameInfo(val address: String) : State()
        class FailedLoadingUsernameInfo(val error: Throwable) : State()
        class DoneLoadingUsernameInfo(val usernameInfo: UsernameInfo) : State()
        object CreatingInvoice : State()
        class FailedCreatingInvoice(val error: Throwable) : State()
        class DoneCreatingInvoice(val invoiceString: String) : State()
        object Tip : State()
        object Finish : State()
    }

    sealed class EnteredAmountError {
        class TooSmall(val minAmount: BigDecimal) : EnteredAmountError()
        class TooBig(val maxAmount: BigDecimal) : EnteredAmountError()
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
                parsedAmount > BigDecimal.ZERO
                        && enteredAmountError.value == EnteredAmountError.None
        }
    }

    fun onCancel() {
        val fallbackUsernameInfo = lastExplicitlyLoadedUsernameInfo

        when {
            (state.value is State.Tip || isTipping) && fallbackUsernameInfo != null -> {
                isTipping = false
                cancelUsernameInfoLoading()
                cancelInvoiceCreation()
                onDoneLoadingUsernameInfo(fallbackUsernameInfo)
            }
            else -> {
                state.value = State.Finish
            }
        }
    }

    private var loadUsernameInfoDisposable: Disposable? = null
    fun loadUsernameInfo(address: String) {
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
            .doOnSubscribe { state.value = State.LoadingUsernameInfo(address) }
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

                    state.value = State.FailedLoadingUsernameInfo(error)
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
        checkNotNull(usernameInfo) {
            "There is no loaded username info to create an invoice for"
        }

        val amountSat = parsedAmount

        log.debug {
            "createInvoice(): begin_creation:" +
                    "\namountSat=$amountSat," +
                    "\nusernameInfo=$usernameInfo"
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
                state.value = State.CreatingInvoice
            }
            .subscribeBy(
                onSuccess = { invoiceString ->
                    createdInvoicesCounter.incrementCreatedInvoices()

                    log.debug {
                        "createInvoice(): created:" +
                                "\ninvoiceString=$invoiceString," +
                                "\ncreatedInvoices=${createdInvoicesCounter.createdInvoiceCount}"
                    }

                    state.value = State.DoneCreatingInvoice(invoiceString)
                },
                onError = { error ->
                    log.error(error) {
                        "createInvoice(): creation_failed"
                    }

                    state.value = State.FailedCreatingInvoice(error)
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
        enteredAmount.value = null
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
            state.value = State.Tip
        } else {
            state.value = State.Finish
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

    override fun onCleared() {
        super.onCleared()
        compositeDisposable.dispose()
    }
}