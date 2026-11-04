package eu.transittrack.predict.smile

import smile.base.cart.Loss
import smile.data.DataFrame
import smile.data.formula.Formula
import smile.data.vector.DoubleVector
import smile.math.MathEx
import smile.regression.GradientTreeBoost

import eu.transittrack.predict.PredictProperties

/** One observed stop-to-stop traversal: [StopToStopFeatures] and the travel time it took. */
data class TrainingSample(
    val features: DoubleArray,
    val travelSec: Double,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is TrainingSample && travelSec == other.travelSec && features.contentEquals(other.features))

    override fun hashCode(): Int = 31 * features.contentHashCode() + travelSec.hashCode()
}

/**
 * Training data held column-wise: one primitive array per [StopToStopFeatures.NAMES] feature, plus
 * the travel times, which become the model's data frame without another copy when the set is full.
 * Samples with a non-finite feature or travel time, or the wrong feature count, are dropped on [add].
 */
class TrainingSet(
    capacity: Int,
) {
    private val features = Array(StopToStopFeatures.NAMES.size) { DoubleArray(capacity) }
    private val travelSecs = DoubleArray(capacity)

    var size: Int = 0
        private set

    /** Returns `false` (and keeps nothing) for an unusable sample. */
    fun add(
        sample: DoubleArray,
        travelSec: Double,
    ): Boolean {
        if (sample.size != features.size || !travelSec.isFinite() || sample.any { !it.isFinite() }) return false
        check(size < travelSecs.size) { "training set is full (${travelSecs.size})" }
        for (j in sample.indices) features[j][size] = sample[j]
        travelSecs[size] = travelSec
        size++
        return true
    }

    internal fun dataFrame(target: String): DataFrame {
        val columns =
            StopToStopFeatures.NAMES.mapIndexed { j, name -> DoubleVector(name, trimmed(features[j])) } +
                DoubleVector(target, trimmed(travelSecs))
        return DataFrame(*columns.toTypedArray())
    }

    private fun trimmed(column: DoubleArray): DoubleArray = if (column.size == size) column else column.copyOf(size)

    companion object {
        fun of(samples: List<TrainingSample>): TrainingSet =
            TrainingSet(samples.size).apply { samples.forEach { add(it.features, it.travelSec) } }
    }
}

/**
 * Fits the three [StopToStopModel] regressors: Smile `GradientTreeBoost` with quantile loss at 0.5,
 * [PredictProperties.Smile.lowQuantile] and [PredictProperties.Smile.highQuantile].
 */
class SmileTrainer(
    private val props: PredictProperties.Smile,
) {
    /** [train] over [samples]; see there. */
    fun train(
        samples: List<TrainingSample>,
        routeIndex: Map<String, Int>,
    ): StopToStopModel? = train(TrainingSet.of(samples), routeIndex)

    /**
     * Returns `null` when [samples] holds fewer than [PredictProperties.Smile.minTrainingRows] usable
     * samples. Deterministic for the same input: Smile's row subsampling uses its per-thread RNG,
     * which is reseeded before each fit.
     */
    fun train(
        samples: TrainingSet,
        routeIndex: Map<String, Int>,
    ): StopToStopModel? {
        if (samples.size < props.minTrainingRows) return null
        val data = samples.dataFrame(TARGET)
        val formula = Formula.lhs(TARGET)

        return StopToStopModel(
            routeIndex = routeIndex,
            median = fit(formula, data, 0.5),
            low = fit(formula, data, props.lowQuantile),
            high = fit(formula, data, props.highQuantile),
        )
    }

    private fun fit(
        formula: Formula,
        data: DataFrame,
        quantile: Double,
    ): GradientTreeBoost {
        val options =
            GradientTreeBoost.Options(
                Loss.quantile(quantile),
                props.trees,
                props.maxDepth,
                // maxDepth bounds the tree; maxNodes (leaves) is set so it doesn't bind first.
                1 shl props.maxDepth.coerceAtMost(MAX_LEAVES_LOG2),
                NODE_SIZE,
                props.shrinkage,
                props.subsample,
                null,
                null,
            )
        MathEx.setSeed(SEED)
        return GradientTreeBoost.fit(formula, data, options)
    }

    private companion object {
        const val TARGET = "travelSec"
        const val SEED = 20_261_103L

        /**
         * Minimum rows per leaf. Leaf values are quantiles of the leaf's residuals, so tiny leaves
         * (Smile's suggested 5: the 0.1 quantile of 5 rows is just their minimum) give noisy, over-fit
         * intervals.
         */
        const val NODE_SIZE = 50
        const val MAX_LEAVES_LOG2 = 12
    }
}
