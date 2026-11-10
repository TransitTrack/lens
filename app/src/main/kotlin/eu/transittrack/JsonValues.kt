package eu.transittrack

import java.math.BigDecimal
import java.math.BigInteger

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** Converts a serialization tree to plain Kotlin values (maps, lists, primitives). */
internal fun JsonElement.toScalarValue(): Any? =
    when (this) {
        JsonNull -> {
            null
        }

        is JsonObject -> {
            mapValues { (_, value) -> value.toScalarValue() }
        }

        is JsonArray -> {
            map { it.toScalarValue() }
        }

        is JsonPrimitive -> {
            when {
                isString -> content
                booleanOrNull != null -> booleanOrNull
                intOrNull != null -> intOrNull
                longOrNull != null -> longOrNull
                content.none { it == '.' || it == 'e' || it == 'E' } -> BigInteger(content)
                else -> BigDecimal(content)
            }
        }
    }
