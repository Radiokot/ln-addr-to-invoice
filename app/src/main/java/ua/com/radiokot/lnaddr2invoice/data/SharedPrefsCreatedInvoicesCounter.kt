package ua.com.radiokot.lnaddr2invoice.data

import android.content.SharedPreferences
import ua.com.radiokot.lnaddr2invoice.base.extension.kLogger

class SharedPrefsCreatedInvoicesCounter(
    private val sharedPreferences: SharedPreferences,
    private val key: String,
) : CreatedInvoicesCounter {
    private val log = kLogger("SPInvoicesCounter")

    override val createdInvoiceCount: Int
        get() = sharedPreferences.getInt(key, 0)

    override fun incrementCreatedInvoices() {
        sharedPreferences
            .edit()
            .putInt(key, createdInvoiceCount + 1)
            .apply()

        log.debug {
            "incrementCreatedInvoices(): counter_incremented:" +
                    "\nnewValue=$createdInvoiceCount"
        }
    }
}