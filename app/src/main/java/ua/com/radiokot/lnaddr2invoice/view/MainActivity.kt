package ua.com.radiokot.lnaddr2invoice.view

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.MutableLiveData
import okio.IOException
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.qualifier.named
import ua.com.radiokot.lnaddr2invoice.R
import ua.com.radiokot.lnaddr2invoice.base.extension.kLogger
import ua.com.radiokot.lnaddr2invoice.base.view.SoftInputUtil
import ua.com.radiokot.lnaddr2invoice.base.view.ToastManager
import ua.com.radiokot.lnaddr2invoice.databinding.ActivityMainBinding
import ua.com.radiokot.lnaddr2invoice.di.InjectedAmountFormat
import ua.com.radiokot.lnaddr2invoice.model.UsernameInfo
import java.math.BigDecimal
import java.text.NumberFormat

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModel()
    private val log = kLogger("MainActivity")

    private val toastManager: ToastManager by inject()
    private val satAmountFormat: NumberFormat by inject(named(InjectedAmountFormat.SAT))
    private val quickAmountFormat: NumberFormat by inject(named(InjectedAmountFormat.DEFAULT))

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

            bottomLabelTextView.setOnClickListener {
                viewModel.onBottomLabelClicked()
            }

            // Quick amounts.
            listOf(200, 500, 1000)
                .forEachIndexed { i, quickAmount ->
                    (quickAmountsLayout.getChildAt(i) as Button).apply {
                        text = quickAmountFormat.format(quickAmount)

                        setOnClickListener {
                            val value = quickAmount.toString()
                            amountEditText.setText(value)
                            amountEditText.setSelection(value.length)
                        }
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
                    onLoadingUsernameInfo(state.address)

                is MainViewModel.State.DoneLoadingUsernameInfo ->
                    onDoneLoadingUsernameInfo(state.usernameInfo)

                is MainViewModel.State.FailedLoadingUsernameInfo ->
                    onFailedLoadingUsernameInfo(state.error)

                MainViewModel.State.CreatingInvoice ->
                    onCreatingInvoice()

                is MainViewModel.State.DoneCreatingInvoice ->
                    onDoneCreatingInvoice(state.invoiceString)

                is MainViewModel.State.FailedCreatingInvoice ->
                    onFailedCreatingInvoice()

                is MainViewModel.State.Tip ->
                    onTip()

                is MainViewModel.State.Finish ->
                    finish()
            }
        }
    }

    private fun onLoadingUsernameInfo(address: String) {
        showLoading(
            getString(
                R.string.template_resolving_address,
                address
            )
        )
    }

    private fun showLoading(message: String) {
        with(binding) {
            loadingLayout.visibility = View.VISIBLE
            invoiceCreationLayout.visibility = View.GONE
            tipLayout.visibility = View.GONE

            primaryButton.visibility = View.GONE

            loadingProgressTextView.text = message
            loadingAnimationView.playAnimation()
        }
    }

    private fun onFailedLoadingUsernameInfo(error: Throwable) {
        when (error) {
            is IOException -> {
                toastManager.long(R.string.error_need_internet_to_load_username_info)
            }
            else -> {
                toastManager.long(R.string.error_failed_to_load_username_info)
            }
        }
        finish()
    }

    private fun onDoneLoadingUsernameInfo(usernameInfo: UsernameInfo) {
        with(binding) {
            loadingLayout.visibility = View.GONE
            invoiceCreationLayout.visibility = View.VISIBLE
            tipLayout.visibility = View.GONE

            with(primaryButton) {
                visibility = View.VISIBLE
                text = getString(R.string.pay_the_invoice)
                setOnClickListener {
                    if (canPay.value == true) {
                        payTheInvoice()
                    }
                }
            }

            descriptionTextView.text = usernameInfo.description

            amountEditText.requestFocus()
            SoftInputUtil.showSoftInputOnView(amountEditText)

            amount.removeObservers(this@MainActivity)
            amount.observe(this@MainActivity) { amount ->
                when {
                    amountEditText.text.isNullOrEmpty() -> {
                        amountTextInputLayout.error = null
                    }
                    amount < usernameInfo.minSendableSat -> {
                        amountTextInputLayout.error = getString(
                            R.string.template_error_you_cant_send_less_than,
                            satAmountFormat.format(usernameInfo.minSendableSat)
                        )
                    }
                    amount > usernameInfo.maxSendableSat -> {
                        amountTextInputLayout.error = getString(
                            R.string.template_error_you_cant_send_more_than,
                            satAmountFormat.format(usernameInfo.maxSendableSat)
                        )
                    }
                    else -> {
                        amountTextInputLayout.error = null
                    }
                }

                canPay.postValue(amount > BigDecimal.ZERO && amountTextInputLayout.error == null)
            }

            canPay.removeObservers(this@MainActivity)
            canPay.observe(this@MainActivity) { canPay ->
                primaryButton.isEnabled = canPay
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

        viewModel.onInvoicePaymentLaunched()
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

    private fun onTip() {
        SoftInputUtil.hideSoftInput(this)

        with(binding) {
            loadingLayout.visibility = View.GONE
            invoiceCreationLayout.visibility = View.GONE
            tipLayout.visibility = View.VISIBLE

            canPay.removeObservers(this@MainActivity)
            amountEditText.text?.clear()

            with(primaryButton) {
                visibility = View.VISIBLE
                text = getString(R.string.tip_the_author)
                isEnabled = true
                setOnClickListener {
                    viewModel.tip()
                }
            }
        }
    }
}