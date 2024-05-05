package ua.com.radiokot.lnaddr2invoice.view

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.view.WindowManager
import android.widget.Button
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.updateLayoutParams
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.qualifier.named
import ua.com.radiokot.lnaddr2invoice.R
import ua.com.radiokot.lnaddr2invoice.base.extension.bindTextTwoWay
import ua.com.radiokot.lnaddr2invoice.base.extension.kLogger
import ua.com.radiokot.lnaddr2invoice.base.view.SoftInputUtil
import ua.com.radiokot.lnaddr2invoice.base.view.ToastManager
import ua.com.radiokot.lnaddr2invoice.databinding.ActivityMainBinding
import ua.com.radiokot.lnaddr2invoice.databinding.ViewQuickAmountEditDialogBinding
import ua.com.radiokot.lnaddr2invoice.di.InjectedAmountFormat
import ua.com.radiokot.lnaddr2invoice.logic.GetUsernameInfoUseCase
import ua.com.radiokot.lnaddr2invoice.model.UsernameInfo
import java.io.IOException
import java.text.NumberFormat
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModel()
    private val log = kLogger("MMainActivity")

    private val toastManager: ToastManager by inject()
    private val satAmountFormat: NumberFormat by inject(named(InjectedAmountFormat.SAT))
    private val quickAmountFormat: NumberFormat by inject(named(InjectedAmountFormat.DEFAULT))

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
            log.error {
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
            viewModel.loadUsernameInfo(rawAddress = address)
        }
    }

    private fun initButtons() {
        onBackPressedDispatcher.addCallback(this) {
            viewModel.onCancel()
        }

        with(binding) {
            cancelButton.setOnClickListener {
                viewModel.onCancel()
            }

            bottomLabelTextView.setOnClickListener {
                viewModel.onBottomLabelClicked()
            }

            viewModel.quickAmounts.observe(this@MainActivity) { quickAmounts ->
                quickAmounts.forEachIndexed { i, quickAmount ->
                    (quickAmountsLayout.getChildAt(i) as Button).apply {
                        text = quickAmountFormat.format(quickAmount)

                        setOnClickListener {
                            viewModel.enteredAmount.value = quickAmount.toString()
                        }

                        setOnLongClickListener {
                            editQuickAmount(
                                index = i,
                                currentValue = quickAmount,
                            )
                            true
                        }
                    }
                }
            }
        }
    }

    private fun initFields() {
        with(binding) {
            amountEditText.bindTextTwoWay(viewModel.enteredAmount)

            amountEditText.setOnEditorActionListener { _, _, _ ->
                if (viewModel.canPay.value == true) {
                    payTheInvoice()
                    false
                } else {
                    true
                }
            }

            viewModel.enteredAmountError.observe(this@MainActivity) { error ->
                // Compensate error space.
                quickAmountsLayout.updateLayoutParams {
                    this as MarginLayoutParams
                    topMargin =
                        ((if (error is MainViewModel.EnteredAmountError.None)
                            -12
                        else
                            4) * resources.displayMetrics.density).roundToInt()
                }

                when (error) {
                    is MainViewModel.EnteredAmountError.None -> {
                        amountTextInputLayout.error = null
                    }

                    is MainViewModel.EnteredAmountError.TooSmall -> {
                        amountTextInputLayout.error = getString(
                            R.string.template_error_you_cant_send_less_than,
                            satAmountFormat.format(error.minAmount)
                        )
                    }

                    is MainViewModel.EnteredAmountError.TooBig -> {
                        amountTextInputLayout.error = getString(
                            R.string.template_error_you_cant_send_more_than,
                            satAmountFormat.format(error.maxAmount)
                        )
                    }
                }
            }

            copyCheckBox.bindTextTwoWay(viewModel.isCopyInvoiceChecked)
        }
    }

    private fun subscribeToState() {
        viewModel.state.observe(this) { state ->
            log.debug {
                "subscribeToState(): received_new_state:" +
                        "\nstate=$state"
            }

            // Fast finishing avoids display of an intermediate state during the animation.
            if (state is MainViewModel.State.Final.Finish) {
                log.debug {
                    "subscribeToState(): fast_finishing"
                }
                finish()
                return@observe
            }

            with(binding) {
                // No need to update the view when finishing.
                if (state is MainViewModel.State.Final) {
                    return@with
                }

                loadingLayout.visibility =
                    if (state is MainViewModel.State.Loading
                        || state is MainViewModel.State.DoneCreatingInvoice
                    )
                        View.VISIBLE
                    else
                        View.GONE

                loadingProgressTextView.text =
                    when (state) {
                        is MainViewModel.State.Loading -> {
                            when (state) {
                                is MainViewModel.State.Loading.LoadingUsernameInfo ->
                                    getString(
                                        R.string.template_resolving_address,
                                        state.address
                                    )

                                is MainViewModel.State.Loading.CreatingInvoice ->
                                    getString(R.string.progress_creating_invoice)
                            }
                        }
                        // Keep the loading visible before finish to avoid blinking.
                        is MainViewModel.State.DoneCreatingInvoice ->
                            getString(R.string.progress_creating_invoice)

                        else ->
                            "You shouldn't see this"
                    }

                invoiceCreationLayout.visibility =
                    if (state is MainViewModel.State.DoneLoadingUsernameInfo)
                        View.VISIBLE
                    else
                        View.GONE

                tipLayout.visibility =
                    if (state is MainViewModel.State.Tip)
                        View.VISIBLE
                    else
                        View.GONE

                with(primaryButton) {
                    when (state) {
                        is MainViewModel.State.Loading -> {
                            visibility = View.GONE
                        }

                        is MainViewModel.State.DoneLoadingUsernameInfo -> {
                            visibility = View.VISIBLE
                            text = getString(R.string.pay_the_invoice)
                            viewModel.canPay.removeObservers(this@MainActivity)
                            viewModel.canPay.observe(this@MainActivity) { canPay ->
                                isEnabled = canPay
                            }
                            setOnClickListener {
                                if (isEnabled) {
                                    payTheInvoice()
                                }
                            }
                        }

                        is MainViewModel.State.Tip -> {
                            visibility = View.VISIBLE
                            text = getString(R.string.tip_the_author)
                            isEnabled = true
                            viewModel.canPay.removeObservers(this@MainActivity)
                            setOnClickListener {
                                viewModel.tip()
                            }
                        }

                        else -> {
                            visibility = View.GONE
                        }
                    }
                }
            }

            when (state) {
                is MainViewModel.State.Loading ->
                    onLoading()

                is MainViewModel.State.DoneLoadingUsernameInfo ->
                    onDoneLoadingUsernameInfo(state.usernameInfo)

                is MainViewModel.State.DoneCreatingInvoice ->
                    onDoneCreatingInvoice(state.invoiceString)

                is MainViewModel.State.Final.FailedLoadingUsernameInfo ->
                    onFailedLoadingUsernameInfo(state.error)

                is MainViewModel.State.Final.FailedCreatingInvoice ->
                    onFailedCreatingInvoice()

                is MainViewModel.State.Tip ->
                    onTip()

                MainViewModel.State.Final.Finish -> {
                    // See fast finishing above.
                }
            }

            if (state is MainViewModel.State.Final) {
                log.debug {
                    "subscribeToState(): finishing_by_final_state"
                }

                finish()
            }

            log.debug {
                "subscribeToState(): handled_new_state:" +
                        "\nstate=$state"
            }
        }
    }

    private fun onLoading() {
        SoftInputUtil.hideSoftInput(window)
        binding.loadingAnimationView.playAnimation()
    }

    private fun onFailedLoadingUsernameInfo(error: Throwable) {
        when (error) {
            is IOException -> {
                toastManager.long(R.string.error_need_internet_to_load_username_info)
            }

            is GetUsernameInfoUseCase.AddressIsAnInvoiceException -> {
                toastManager.long(R.string.error_only_addresses_allowed)
            }

            else -> {
                toastManager.long(R.string.error_failed_to_load_username_info)
            }
        }
    }

    private fun onDoneLoadingUsernameInfo(usernameInfo: UsernameInfo) {
        with(binding) {
            descriptionTextView.text = usernameInfo.description

            amountEditText.requestFocus()
            SoftInputUtil.showSoftInput(window)
        }
    }

    private fun payTheInvoice() {
        viewModel.createInvoice()
    }

    private fun onFailedCreatingInvoice() {
        toastManager.short(R.string.error_failed_to_create_invoice)
    }

    private fun onDoneCreatingInvoice(invoiceString: String) {
        launchPaymentIntent(invoiceString)
        setResult(Activity.RESULT_OK)
        viewModel.onInvoicePaymentLaunched()
    }

    private fun launchPaymentIntent(invoiceString: String) {
        val paymentIntent = Intent(Intent.ACTION_VIEW, Uri.parse("lightning:$invoiceString"))

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

        log.debug {
            "launchPaymentIntent(): launched:" +
                    "\ndata=${paymentIntent.data}"
        }
    }

    private fun onTip() {
        SoftInputUtil.hideSoftInput(window)
    }

    @SuppressLint("SetTextI18n")
    private fun editQuickAmount(
        index: Int,
        currentValue: Long,
    ) {
        val dialogView = ViewQuickAmountEditDialogBinding.inflate(layoutInflater)
        lateinit var dialog: AlertDialog

        fun trySaveAmount(): Boolean {
            val parsedAmount = dialogView.valueTextInput.editText?.text?.toString()?.toLongOrNull() ?: 0L
            return if (parsedAmount != 0L) {
                viewModel.updateQuickAmount(
                    index = index,
                    newValue = parsedAmount,
                )
                true
            } else {
                false
            }
        }

        with(dialogView.valueTextInput.editText!!) {
            setText(currentValue.toString())
            setSelection(text.length)
            setOnEditorActionListener { _, _, _ ->
                if (trySaveAmount()) {
                    dialog.dismiss()
                    false
                } else {
                    true
                }
            }
        }

        dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView.root)
            .setPositiveButton(R.string.ok) { _, _ ->
                trySaveAmount()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
            .also {
                it.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
            }
    }
}
