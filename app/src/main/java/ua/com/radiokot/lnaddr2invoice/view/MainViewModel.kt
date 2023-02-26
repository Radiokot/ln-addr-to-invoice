package ua.com.radiokot.lnaddr2invoice.view

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf
import ua.com.radiokot.lnaddr2invoice.base.extension.kLogger
import ua.com.radiokot.lnaddr2invoice.logic.GetBolt11InvoiceUseCase
import ua.com.radiokot.lnaddr2invoice.logic.GetUsernameInfoUseCase
import ua.com.radiokot.lnaddr2invoice.model.UsernameInfo
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

class MainViewModel(
    private val authorTipAddress: String,
) : ViewModel(), KoinComponent {
    private val compositeDisposable = CompositeDisposable()
    private val log = kLogger("MainVM")

    val state: MutableLiveData<State> = MutableLiveData()

    private var usernameInfo: UsernameInfo? = null

    sealed class State {
        class LoadingUsernameInfo(val address: String) : State()
        class FailedLoadingUsernameInfo(val error: Throwable) : State()
        class DoneLoadingUsernameInfo(val usernameInfo: UsernameInfo) : State()
        object CreatingInvoice : State()
        class FailedCreatingInvoice(val error: Throwable) : State()
        class DoneCreatingInvoice(val invoiceString: String) : State()
        object Tip : State()
    }

    fun loadUsernameInfo(address: String) {
        log.debug {
            "loadUsernameInfo(): begin_loading:" +
                    "\naddress=$address"
        }

        val useCase: GetUsernameInfoUseCase = get {
            parametersOf(address)
        }

        Single.zip(
            useCase.perform(),
            Single.timer(500, TimeUnit.MILLISECONDS)
        ) { usernameInfo, _ -> usernameInfo }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { state.postValue(State.LoadingUsernameInfo(address)) }
            .subscribeBy(
                onSuccess = { usernameInfo ->
                    log.debug {
                        "loadUsernameInfo(): loaded:" +
                                "\ninfo=$usernameInfo"
                    }

                    this.usernameInfo = usernameInfo
                    state.postValue(State.DoneLoadingUsernameInfo(usernameInfo))
                },
                onError = { error ->
                    log.error(error) {
                        "loadUsernameInfo(): loading_failed"
                    }

                    state.postValue(State.FailedLoadingUsernameInfo(error))
                }
            )
            .addTo(compositeDisposable)
    }

    fun createInvoice(amountSat: BigDecimal) {
        val usernameInfo = this.usernameInfo
        checkNotNull(usernameInfo) {
            "There is no loaded username info to create an invoice for"
        }

        log.debug {
            "createInvoice(): begin_creation:" +
                    "\namountSat=$amountSat," +
                    "\nusernameInfo=$usernameInfo"
        }

        val useCase = get<GetBolt11InvoiceUseCase> {
            parametersOf(amountSat, usernameInfo.callbackUrl)
        }

        Single.zip(
            useCase.perform(),
            Single.timer(500, TimeUnit.MILLISECONDS)
        ) { invoiceString, _ -> invoiceString }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe {
                state.postValue(State.CreatingInvoice)
            }
            .subscribeBy(
                onSuccess = { invoiceString ->
                    log.debug {
                        "createInvoice(): created:" +
                                "\ninvoiceString=$invoiceString"
                    }

                    state.postValue(State.DoneCreatingInvoice(invoiceString))
                },
                onError = { error ->
                    log.error(error) {
                        "createInvoice(): creation_failed"
                    }

                    state.postValue(State.FailedCreatingInvoice(error))
                }
            )
            .addTo(compositeDisposable)
    }

    fun onBottomLabelClicked() {
        if (state.value is State.DoneLoadingUsernameInfo) {
            state.postValue(State.Tip)
        }
    }

    fun tip() {
        log.debug {
            "tip(): make_a_tip:" +
                    "\nauthorTipAddress=$authorTipAddress"
        }

        loadUsernameInfo(authorTipAddress)
    }

    override fun onCleared() {
        super.onCleared()
        compositeDisposable.dispose()
    }
}