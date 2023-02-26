package ua.com.radiokot.lnaddr2invoice.data

import android.content.SharedPreferences

class SharedPrefsTipStateStorage(
    private val sharedPreferences: SharedPreferences,
    private val key: String,
) : TipStateStorage {
    override var isEverTipped: Boolean
        get() = sharedPreferences.getBoolean(key, false)
        set(value) {
            sharedPreferences
                .edit()
                .putBoolean(key, value)
                .apply()
        }
}