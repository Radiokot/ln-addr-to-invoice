package ua.com.radiokot.lnaddr2invoice

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import ua.com.radiokot.lnaddr2invoice.base.extension.kLogger
import ua.com.radiokot.lnaddr2invoice.base.view.ToastManager
import ua.com.radiokot.lnaddr2invoice.databinding.ActivityMainBinding
import ua.com.radiokot.lnaddr2invoice.model.UsernameInfo
import ua.com.radiokot.lnaddr2invoice.view.MainViewModel

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModel()
    private val log = kLogger("MainActivity")

    private val toastManager: ToastManager by inject()

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

        initToolbar()
        initButtons()

        subscribeToState()

        if (savedInstanceState == null) {
            viewModel.loadUsernameInfo(address)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        log.debug { "onNewIntent(): data=${intent?.data}" }
    }

    private fun initToolbar() {
        setSupportActionBar(binding.toolbar)
    }

    private fun initButtons() {
        onBackPressedDispatcher.addCallback(this) {
            finish()
        }

        binding.cancelButton.setOnClickListener {
            finish()
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
        binding.loadingLayout.visibility = View.VISIBLE
        binding.loadingProgressTextView.text = getString(R.string.progress_loading_username_info)
    }

    private fun onFailedLoadingUsernameInfo() {
        toastManager.short(R.string.error_failed_to_load_username_info)
        finish()
    }

    private fun onDoneLoadingUsernameInfo(usernameInfo: UsernameInfo) {
        toastManager.short(usernameInfo.description)
    }
}