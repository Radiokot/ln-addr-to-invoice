package ua.com.radiokot.lnaddr2invoice.base.extension

import mu.KLogger
import mu.KotlinLogging

fun Any.kLogger(name: String): KLogger = KotlinLogging.logger("$name@${hashCode()}")