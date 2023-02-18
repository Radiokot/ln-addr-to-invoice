package ua.com.radiokot.lnaddr2invoice

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
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

                MainViewModel.State.CreatingInvoice ->
                    onCreatingInvoice()

                is MainViewModel.State.DoneCreatingInvoice ->
                    onDoneCreatingInvoice(state.invoiceString)

                is MainViewModel.State.FailedCreatingInvoice ->
                    onFailedCreatingInvoice()
            }
        }
    }

    private fun onLoadingUsernameInfo() {
        showLoading(getString(R.string.progress_loading_username_info))
    }

    private fun showLoading(message: String) {
        with(binding) {
            loadingLayout.visibility = View.VISIBLE
            invoiceCreationLayout.visibility = View.GONE

            payButton.visibility = View.GONE

            loadingProgressTextView.text = message
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
        viewModel.createInvoice(
            amountSat = checkNotNull(amount.value) {
                "There is no amount to create an invoice with"
            }
        )
    }

    private fun onCreatingInvoice() {
        SoftInputUtil.hideSoftInput(this)
        showLoading(getString(R.string.progress_creating_invoice))
    }

    private fun onFailedCreatingInvoice() {
        toastManager.short(R.string.error_failed_to_create_invoice)
        finish()
    }

    private fun onDoneCreatingInvoice(invoiceString: String) {
        launchPaymentIntent(invoiceString)

        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun launchPaymentIntent(invoiceString: String) {
        val paymentIntent = Intent(Intent.ACTION_VIEW)
            .setData(
                Uri.Builder()
                    .scheme("lightning")
                    .authority(invoiceString)
                    .build()
            )

        Intent.createChooser(
            paymentIntent,
            getString(R.string.use_your_wallet_to_pay_the_invoice)
        )
            .apply {
                // Exclude ourself from the chooser.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    putExtra(
                        Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(
                            ComponentName(
                                applicationContext,
                                MainActivity::class.java
                            )
                        )
                    )
                }
            }
            .also(::startActivity)
    }
}