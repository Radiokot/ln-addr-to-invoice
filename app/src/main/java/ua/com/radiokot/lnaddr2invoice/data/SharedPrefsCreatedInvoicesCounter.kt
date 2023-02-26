package ua.com.radiokot.lnaddr2invoice.data

import android.content.SharedPreferences

class SharedPrefsCreatedInvoicesCounter(
    private val sharedPreferences: SharedPreferences,
    private val key: String,
) : CreatedInvoicesCounter {
    override val createdInvoiceCount: Int
        get() = sharedPreferences.getInt(key, 0)

    override fun incrementCreatedInvoices() {
        sharedPreferences
            .edit()
            .putInt(key, createdInvoiceCount + 1)
            .apply()
    }
}