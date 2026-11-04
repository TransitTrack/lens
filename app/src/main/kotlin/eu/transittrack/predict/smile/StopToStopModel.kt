package eu.transittrack.predict.smile

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InvalidObjectException
import java.io.ObjectInputFilter
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serial
import java.io.Serializable

import smile.data.Tuple
import smile.data.type.DataTypes
import smile.data.type.StructField
import smile.data.type.StructType
import smile.regression.GradientTreeBoost

/** One stop-to-stop travel-time prediction: the median plus a prediction interval, in seconds. */
data class StopToStopEstimate(
    val medianSec: Double,
    val lowSec: Double,
    val highSec: Double,
)

/**
 * Three quantile gradient-boosting regressors (median, low and high quantile) over
 * [StopToStopFeatures]. [routeIndex] maps route ids to the `routeIndex` feature the models were
 * trained with.
 *
 * Immutable and safe to share between threads once constructed (see [schema]).
 *
 * Serialization: Smile 4.4.2's `GradientTreeBoost` is `java.io.Serializable` (training workspaces are
 * transient), so [toBytes] uses plain Java serialization through a [SerializedForm] proxy; the proxy
 * rebuilds the model through its constructor, which re-binds the formulas before the model is
 * published.
 */
class StopToStopModel(
    routeIndex: Map<String, Int>,
    private val median: GradientTreeBoost,
    private val low: GradientTreeBoost,
    private val high: GradientTreeBoost,
) : Serializable {
    val routeIndex: Map<String, Int> = LinkedHashMap(routeIndex)

    /**
     * Single schema instance for every prediction tuple. Smile's `Formula` caches its binding to the
     * last schema it saw by identity and rebinds unsynchronized on a mismatch, so binding all three
     * formulas here, in the constructor, and always passing this same instance makes every later
     * `predict` a read-only lookup, and final-field semantics publish that binding to other threads.
     */
    private val schema = StructType(StopToStopFeatures.NAMES.map { StructField(it, DataTypes.DoubleType) })

    init {
        median.formula().bind(schema)
        low.formula().bind(schema)
        high.formula().bind(schema)
    }

    /**
     * Guarantees `0 <= lowSec <= medianSec <= highSec`, all finite. The three raw outputs are sorted
     * (independently fitted quantile models can cross) and clamped at zero (travel times can't be
     * negative).
     */
    fun estimate(features: DoubleArray): StopToStopEstimate {
        require(features.size == schema.length()) {
            "expected ${schema.length()} features, got ${features.size}"
        }
        val tuple = Tuple.of(schema, features)
        val sorted =
            doubleArrayOf(low.predict(tuple), median.predict(tuple), high.predict(tuple))
                .map { if (it.isNaN()) 0.0 else it.coerceIn(0.0, Double.MAX_VALUE) }
                .sorted()
        return StopToStopEstimate(medianSec = sorted[1], lowSec = sorted[0], highSec = sorted[2])
    }

    fun toBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        ObjectOutputStream(out).use { it.writeObject(this) }
        return out.toByteArray()
    }

    @Serial
    private fun writeReplace(): Any = SerializedForm(LinkedHashMap(routeIndex), median, low, high)

    /** Only [SerializedForm] may be deserialized, so the constructor always runs. */
    @Serial
    @Suppress("UnusedParameter")
    private fun readObject(input: ObjectInputStream): Unit = throw InvalidObjectException("SerializedForm required")

    private class SerializedForm(
        private val routeIndex: LinkedHashMap<String, Int>,
        private val median: GradientTreeBoost,
        private val low: GradientTreeBoost,
        private val high: GradientTreeBoost,
    ) : Serializable {
        @Serial
        private fun readResolve(): Any = StopToStopModel(routeIndex, median, low, high)

        companion object {
            @Serial
            private const val serialVersionUID = 1L
        }
    }

    companion object {
        @Serial
        private const val serialVersionUID = 1L

        /**
         * Defence in depth: the bytes come from our own database, but only model classes may be
         * revived, within bounds. A default model (200 trees of depth 6) is ~1 MB, nests 11 deep and
         * holds ~30 000 references; depth 12 gives ~3.5 MB, 16 deep, ~100 000 references. Its longest
         * array is the tree array (`trees` long). The limits leave room for far larger settings while
         * stopping a corrupt or hostile payload from exhausting the stack or the heap.
         */
        private val INPUT_FILTER: ObjectInputFilter =
            ObjectInputFilter.Config.createFilter(
                "maxdepth=256;maxrefs=2000000;maxarray=1000000;maxbytes=67108864;" +
                    "java.**;kotlin.**;smile.**;eu.transittrack.predict.smile.**;!*",
            )

        /** @throws IllegalArgumentException when [bytes] don't hold a serialized [StopToStopModel]. */
        fun fromBytes(bytes: ByteArray): StopToStopModel {
            val restored =
                try {
                    ObjectInputStream(ByteArrayInputStream(bytes)).use {
                        it.objectInputFilter = INPUT_FILTER
                        it.readObject()
                    }
                } catch (e: Exception) {
                    throw IllegalArgumentException("not a serialized StopToStopModel", e)
                } catch (e: StackOverflowError) {
                    // Deep nesting under the depth limit can still overflow a small stack; the
                    // failure is confined to this payload's own frames, which are unwound by now.
                    throw IllegalArgumentException("not a serialized StopToStopModel: nested too deep", e)
                }
            return restored as? StopToStopModel
                ?: throw IllegalArgumentException("not a serialized StopToStopModel: ${restored?.javaClass?.name}")
        }
    }
}
