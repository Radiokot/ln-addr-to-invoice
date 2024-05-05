package ua.com.radiokot.lnaddr2invoice.data

import android.content.SharedPreferences

class SharedPrefsQuickAmountsStorage(
    private val sharedPreferences: SharedPreferences,
    private val key: String,
) : QuickAmountsStorage {
    override var quickAmounts: List<Long>?
        get() = sharedPreferences.getString(key, "")
            ?.takeIf(String::isNotEmpty)
            ?.split(SEPARATOR)
            ?.map(String::toLong)
        set(value) {
            sharedPreferences
                .edit()
                .putString(key, value?.joinToString(SEPARATOR))
                .apply()
        }

    private companion object {
        private const val SEPARATOR = ","
    }
}
