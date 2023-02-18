package ua.com.radiokot.lnaddr2invoice

import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.MutableLiveData
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.qualifier.named
import ua.com.radiokot.lnaddr2invoice.base.extension.kLogger
import ua.com.radiokot.lnaddr2invoice.base.view.SoftInputUtil
import ua.com.radiokot.lnaddr2invoice.base.view.ToastManager
import ua.com.radiokot.lnaddr2invoice.databinding.ActivityMainBinding
import ua.com.radiokot.lnaddr2invoice.di.InjectedAmountFormat
import ua.com.radiokot.lnaddr2invoice.model.UsernameInfo
import ua.com.radiokot.lnaddr2invoice.view.MainViewModel
import java.math.BigDecimal
import java.text.NumberFormat

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModel()
    private val log = kLogger("MainActivity")

    private val toastManager: ToastManager by inject()
    private val satAmountFormat: NumberFormat by inject(named(InjectedAmountFormat.SAT))

    private val amount: MutableLiveData<BigDecimal> = MutableLiveData()
    private val canPay: MutableLiveData<Boolean> = MutableLiveData(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        log.debug {
            "onCreate(): creating:" +
                    "\ndata=${intent.data}," +
                    "\nsavedState=$savedInstanceState"
        }

        val address = intent
            ?.dataString
            ?.substringAfter(intent.data?.scheme + ":", "")
            ?.takeIf(String::isNotEmpty)

        if (address == null) {
            log.warn {
                "onCreate(): missing_address"
            }
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initButtons()
        initFields()

        subscribeToState()

        if (savedInstanceState == null) {
            viewModel.loadUsernameInfo(address)
        }
    }

    private fun initButtons() {
        onBackPressedDispatcher.addCallback(this) {
            finish()
        }

        with(binding) {
            cancelButton.setOnClickListener {
                finish()
            }

            payButton.setOnClickListener {
                if (canPay.value == true) {
                    payTheInvoice()
                }
            }
        }
    }

    private fun initFields() {
        with(binding) {
            amountEditText.doOnTextChanged { text, _, _, _ ->
                amount.postValue(BigDecimal(text?.toString()?.toLongOrNull() ?: 0L))
            }

            amountEditText.setOnEditorActionListener { _, _, _ ->
                if (canPay.value == true) {
                    payTheInvoice()
                    false
                } else {
                    true
                }
            }
        }
    }

    private fun subscribeToState() {
        viewModel.state.observe(this) { state ->
            when (state) {
                is MainViewModel.State.LoadingUsernameInfo ->
                    onLoadingUsernameInfo()

                is MainViewModel.State.DoneLoadingUsernameInfo ->
                    onDoneLoadingUsernameInfo(state.usernameInfo)

                is MainViewModel.State.FailedLoadingUsernameInfo ->
                    onFailedLoadingUsernameInfo()
            }
        }
    }

    private fun onLoadingUsernameInfo() {
        with(binding) {
            loadingLayout.visibility = View.VISIBLE
            invoiceCreationLayout.visibility = View.GONE

            payButton.visibility = View.INVISIBLE

            loadingProgressTextView.text =
                getString(R.string.progress_loading_username_info)
        }
    }

    private fun onFailedLoadingUsernameInfo() {
        toastManager.short(R.string.error_failed_to_load_username_info)
        finish()
    }

    private fun onDoneLoadingUsernameInfo(usernameInfo: UsernameInfo) {
        with(binding) {
            loadingLayout.visibility = View.GONE
            invoiceCreationLayout.visibility = View.VISIBLE

            payButton.visibility = View.VISIBLE

            descriptionTextView.text = usernameInfo.description

            amountEditText.requestFocus()
            SoftInputUtil.showSoftInputOnView(amountEditText)

            amount.removeObservers(this@MainActivity)
            amount.observe(this@MainActivity) { amount ->
                when {
                    amount < usernameInfo.minSendableSat -> {
                        amountTextInputLayout.isErrorEnabled = true
                        amountTextInputLayout.error = getString(
                            R.string.template_error_you_cant_send_less_than,
                            satAmountFormat.format(usernameInfo.minSendableSat)
                        )
                    }
                    amount > usernameInfo.maxSendableSat -> {
                        amountTextInputLayout.isErrorEnabled = true
                        amountTextInputLayout.error = getString(
                            R.string.template_error_you_cant_send_more_than,
                            satAmountFormat.format(usernameInfo.maxSendableSat)
                        )
                    }
                    else -> {
                        amountTextInputLayout.isErrorEnabled = false
                        amountTextInputLayout.error = null
                    }
                }

                canPay.postValue(amountTextInputLayout.error == null)
            }

            canPay.removeObservers(this@MainActivity)
            canPay.observe(this@MainActivity) { canPay ->
                payButton.isEnabled = canPay
            }
        }
    }

    private fun payTheInvoice() {
        toastManager.short(R.string.pay_the_invoice)
    }
}