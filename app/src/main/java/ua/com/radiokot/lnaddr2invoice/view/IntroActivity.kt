package ua.com.radiokot.lnaddr2invoice.view

import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.core.text.HtmlCompat
import androidx.core.view.forEach
import org.koin.android.ext.android.getKoin
import ua.com.radiokot.lnaddr2invoice.R
import ua.com.radiokot.lnaddr2invoice.databinding.ActivityIntroBinding
import ua.com.radiokot.lnaddr2invoice.util.InternalLinkMovementMethod

class IntroActivity : AppCompatActivity() {
    private lateinit var view: ActivityIntroBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        view = ActivityIntroBinding.inflate(layoutInflater)
        setContentView(view.root)

        setSupportActionBar(view.toolbar)

        val tipAddress: String = getKoin().getProperty("authorTipAddress")!!

        view.addressTextView.text = HtmlCompat.fromHtml(
            getString(
                R.string.template_intro_clicking_address_address,
                tipAddress
            ),
            HtmlCompat.FROM_HTML_MODE_COMPACT
        )
        view.addressTextView.movementMethod = InternalLinkMovementMethod { false }

        view.commentTextView.text = getString(
            R.string.template_intro_selecting_address_comment,
            tipAddress
        )

        view.gotItButton.setOnClickListener {
            finish()
        }
    }

    override fun onActionModeStarted(mode: ActionMode) {
        super.onActionModeStarted(mode)

        val appTitle = getString(R.string.app_name)
        val textSelectionActionTitle = getString(R.string.send_sats)

        // Manually fix incorrectly resolved name.
        // This is weird, as it happens in apps (this one, Instagram) but not in a browser.
        mode.menu.forEach { item ->
            if (item.title == appTitle || item.title == textSelectionActionTitle) {
                item.title = textSelectionActionTitle
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.intro, menu)

        menu.findItem(R.id.paste).setOnMenuItemClickListener {
            proceedWithClipboardText()
            true
        }

        return super.onCreateOptionsMenu(menu)
    }

    private fun proceedWithClipboardText() {
        val clipboardText = application.getSystemService<ClipboardManager>()
            ?.primaryClip
            ?.getItemAt(0)
            ?.text
            ?.toString()

        if (clipboardText != null) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .setData(Uri.parse("lightning:$clipboardText"))
            )
        }
    }
}
