package ua.com.radiokot.lnaddr2invoice.view

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatDialogFragment
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ua.com.radiokot.lnaddr2invoice.R

class OpenSourceLicensesDialogFragment : AppCompatDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(" ")
            .setView(WebView(requireContext()).apply {
                setBackgroundColor(Color.TRANSPARENT)

                // Enable JS to inject the immersive CSS.
                settings.apply {
                    @SuppressLint("SetJavaScriptEnabled")
                    javaScriptEnabled = true
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
                        // Inject the immersive CSS on document load.
                        evaluateJavascript(
                            getSimpleHtmlImmersiveScript(
                                textColor = MaterialColors.getColor(
                                    view,
                                    com.google.android.material.R.attr.colorOnBackground,
                                    Color.BLUE
                                ),
                                codeBlockColor = MaterialColors.getColor(
                                    view,
                                    com.google.android.material.R.attr.colorSurfaceVariant,
                                    Color.YELLOW
                                ),
                                primaryColor = MaterialColors.getColor(
                                    view,
                                    com.google.android.material.R.attr.colorPrimary,
                                    Color.YELLOW
                                ),
                            ),
                            null
                        )
                    }
                }

                loadUrl("file:///android_asset/open_source_licenses.html")
            })
            .setPositiveButton(R.string.ok) { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .create()
    }

    fun getSimpleHtmlImmersiveScript(
        @ColorInt
        codeBlockColor: Int,
        @ColorInt
        textColor: Int,
        @ColorInt
        primaryColor: Int,
    ): String =
        """
            var immersiveCss = `
                <style type="text/css">
                    /* Make the background match window color
                     * and the text match text color
                     */
                    body {
                        color: ${textColor.toCssRgb()} !important;
                    }
                    
                    /* Make the links primary */
                    a {
                        color: ${primaryColor.toCssRgb()} !important;
                    }
                    
                    /* Make the code blocks look good
                     * in both light and dark modes
                     */
                    pre {
                        background: ${codeBlockColor.toCssRgb()} !important;
                    }
                </style>
            `
            document.head.insertAdjacentHTML('beforeend', immersiveCss)
            console.log('OOLEG tosos')
        """.trimIndent()

    private fun Int.toCssRgb(): String =
        "rgb(${Color.red(this)},${Color.green(this)},${Color.blue(this)})"

    companion object {
        const val TAG = "open-source-licenses"
    }
}
