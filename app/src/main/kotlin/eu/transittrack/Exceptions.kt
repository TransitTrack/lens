package eu.transittrack

open class LensException(
    message: String,
) : RuntimeException(message)

class ParseException(
    message: String,
) : LensException(message)
