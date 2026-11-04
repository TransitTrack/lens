package eu.transittrack

import java.math.BigDecimal
import java.math.BigInteger

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class JsonValuesTest {
    @Test
    fun `JSON scalar values preserve primitives nulls and numeric precision`() {
        val value =
            ProjectJson
                .parseToJsonElement(
                    """
                {
                  "string":"null","null":null,"boolean":true,"int":12,"long":9007199254740993,
                  "big":9223372036854775808,"decimal":17.1234567890123456789,"array":[1,"2",null]
                }
                    """.trimIndent(),
                ).toScalarValue()

        assertThat(value).isEqualTo(
            linkedMapOf(
                "string" to "null",
                "null" to null,
                "boolean" to true,
                "int" to 12,
                "long" to 9007199254740993L,
                "big" to BigInteger("9223372036854775808"),
                "decimal" to BigDecimal("17.1234567890123456789"),
                "array" to listOf(1, "2", null),
            ),
        )
    }

    @Test
    fun `geometry retains longitude latitude and path order`() {
        val raw = "[[17.1234567890123456789,51.25],[17,52.5],[17.1234567890123456789,51.25]]"
        assertThat(ProjectJson.parseToJsonElement(raw).toScalarValue()).isEqualTo(
            listOf(
                listOf(BigDecimal("17.1234567890123456789"), BigDecimal("51.25")),
                listOf(17, BigDecimal("52.5")),
                listOf(BigDecimal("17.1234567890123456789"), BigDecimal("51.25")),
            ),
        )
    }
}
