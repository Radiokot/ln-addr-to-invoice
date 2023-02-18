package ua.com.radiokot.lnaddr2invoice.view

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf
import ua.com.radiokot.lnaddr2invoice.base.extension.kLogger
import ua.com.radiokot.lnaddr2invoice.logic.GetUsernameInfoUseCase
import ua.com.radiokot.lnaddr2invoice.model.UsernameInfo

class MainViewModel : ViewModel(), KoinComponent {
    private val compositeDisposable = CompositeDisposable()
    private val log = kLogger("MainVM")

    val state: MutableLiveData<State> = MutableLiveData()

    sealed class State {
        object LoadingUsernameInfo : State()
        class FailedLoadingUsernameInfo(val error: Throwable) : State()
        class DoneLoadingUsernameInfo(val usernameInfo: UsernameInfo) : State()
    }

    fun loadUsernameInfo(address: String) {
        log.debug {
            "loadUsernameInfo(): begin_loading:" +
                    "\naddress=$address"
        }

        val useCase: GetUsernameInfoUseCase = get {
            parametersOf(address)
        }

        useCase
            .perform()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { state.postValue(State.LoadingUsernameInfo) }
            .subscribeBy(
                onSuccess = { usernameInfo ->
                    log.debug {
                        "loadUsernameInfo(): loaded:" +
                                "\ninfo=$usernameInfo"
                    }

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

    override fun onCleared() {
        super.onCleared()
        compositeDisposable.dispose()
    }
}