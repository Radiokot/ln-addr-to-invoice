package ua.com.radiokot.lnaddr2invoice.view

import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import ua.com.radiokot.lnaddr2invoice.R
import ua.com.radiokot.lnaddr2invoice.base.view.ToastManager
import ua.com.radiokot.lnaddr2invoice.databinding.ActivityPasteAddressBinding

class PasteAddressActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val view = ActivityPasteAddressBinding.inflate(layoutInflater)
        setContentView(view.root)

        // Since Android 10, clipboard can't be accessed
        // when the app is not in focus.
        view.root.post {
            val clipboardText = getSystemService<ClipboardManager>()
                ?.primaryClip
                ?.getItemAt(0)
                ?.text
                ?.toString()

            if (clipboardText != null) {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .setData(Uri.parse("lightning:$clipboardText"))
                )
            } else {
                ToastManager(this).short(R.string.error_clipboard_has_no_text)
            }

            finish()
        }
    }
}
