package ua.com.radiokot.lnaddr2invoice.base.extension

fun <T : Any> T?.checkNotNull(): T =
    checkNotNull(this)

inline fun <T : Any> T?.checkNotNull(lazyMessage: () -> Any): T =
    checkNotNull(this, lazyMessage)