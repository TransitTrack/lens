package eu.transittrack.predict.smile

import smile.base.cart.Loss
import smile.data.DataFrame
import smile.data.formula.Formula
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
 * Fits the three [StopToStopModel] regressors: Smile `GradientTreeBoost` with quantile loss at 0.5,
 * [PredictProperties.Smile.lowQuantile] and [PredictProperties.Smile.highQuantile].
 */
class SmileTrainer(
    private val props: PredictProperties.Smile,
) {
    init {
        // Smile rejects maxDepth < 2 only once training starts; fail at wiring time instead.
        require(props.maxDepth >= 2) { "smile.maxDepth must be >= 2" }
    }

    /**
     * Returns `null` when fewer than [PredictProperties.Smile.minTrainingRows] usable samples are
     * given; samples with a non-finite feature or travel time, or the wrong feature count, are dropped.
     * Deterministic for the same input: Smile's row subsampling uses its per-thread RNG, which is
     * reseeded before each fit.
     */
    fun train(
        samples: List<TrainingSample>,
        routeIndex: Map<String, Int>,
    ): StopToStopModel? {
        val usable =
            samples.filter { s ->
                s.features.size == StopToStopFeatures.NAMES.size && s.travelSec.isFinite() && s.features.all { it.isFinite() }
            }
        if (usable.size < props.minTrainingRows) return null

        val rows = Array(usable.size) { i -> usable[i].features + usable[i].travelSec }
        val data = DataFrame.of(rows, *(StopToStopFeatures.NAMES + TARGET).toTypedArray())
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
