package eu.transittrack.util

fun Double.kmphToMps(): Double = this * (5.0 / 18.0)

fun Double.mpsToKmph(): Double = this * (18.0 / 5.0)
