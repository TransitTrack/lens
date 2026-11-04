package eu.transittrack.predict.smile

import java.util.Random
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertFailsWith

import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isLessThan
import assertk.assertions.isNull
import assertk.assertions.isTrue

import eu.transittrack.predict.PredictProperties
import eu.transittrack.predict.learn.DayType
import eu.transittrack.predict.learn.TimeBand

class SmileTrainerTest {
    @Test
    fun medianTracksSignalOnHoldout() {
        val errors = holdout.map { abs(model.estimate(it.features).medianSec - signal(it.features)) }
        val meanTrue = holdout.map { signal(it.features) }.average()
        assertThat(errors.average()).isLessThan(0.08 * meanTrue)
    }

    @Test
    fun rangeBracketsMostHoldoutOutcomes() {
        val covered =
            holdout.count {
                val e = model.estimate(it.features)
                it.travelSec in e.lowSec..e.highSec
            }
        assertThat(covered.toDouble() / holdout.size).isBetween(0.70, 0.90)
    }

    @Test
    fun estimateIsMonotoneAndFinite() {
        val rng = Random(7)
        repeat(1_000) {
            // Deliberately wider than the training distribution, including negative values.
            val features = DoubleArray(StopToStopFeatures.NAMES.size) { (rng.nextDouble() - 0.5) * 20_000 }
            val e = model.estimate(features)
            assertThat(e.lowSec.isFinite() && e.medianSec.isFinite() && e.highSec.isFinite()).isTrue()
            assertThat(0.0 <= e.lowSec && e.lowSec <= e.medianSec && e.medianSec <= e.highSec).isTrue()
        }
    }

    @Test
    fun returnsNullBelowMinimum() {
        val trainer = SmileTrainer(props)
        assertThat(trainer.train(training.take(props.minTrainingRows - 1), ROUTE_INDEX)).isNull()
    }

    @Test
    fun serializationRoundTripKeepsEstimates() {
        val restored = StopToStopModel.fromBytes(model.toBytes())
        assertThat(restored.routeIndex).isEqualTo(ROUTE_INDEX)
        holdout.take(50).forEach {
            assertThat(restored.estimate(it.features)).isEqualTo(model.estimate(it.features))
        }
    }

    @Test
    fun trainingSetDropsUnusableSamples() {
        val set = TrainingSet(capacity = 4)
        val good = training.first().features
        assertThat(set.add(good, 10.0)).isTrue()
        assertThat(set.add(good.copyOf().also { it[0] = Double.NaN }, 10.0)).isFalse()
        assertThat(set.add(good, Double.POSITIVE_INFINITY)).isFalse()
        assertThat(set.add(good.copyOf(good.size - 1), 10.0)).isFalse()
        assertThat(set.size).isEqualTo(1)
    }

    @Test
    fun partlyFilledTrainingSetTrainsLikeTheSampleList() {
        val quick = props.copy(trees = 20)
        val set = TrainingSet(capacity = training.size + 100)
        training.forEach { set.add(it.features, it.travelSec) }

        val fromSet = SmileTrainer(quick).train(set, ROUTE_INDEX)!!
        val fromList = SmileTrainer(quick).train(training, ROUTE_INDEX)!!

        holdout.take(50).forEach { assertThat(fromSet.estimate(it.features)).isEqualTo(fromList.estimate(it.features)) }
    }

    @Test
    fun deeplyNestedPayloadIsRejected() {
        var nested: Any = "leaf"
        repeat(400) { nested = arrayListOf(nested) }
        assertFailsWith<IllegalArgumentException> { StopToStopModel.fromBytes(serialize(nested)) }
    }

    @Test
    fun oversizedArrayIsRejected() {
        assertFailsWith<IllegalArgumentException> { StopToStopModel.fromBytes(serialize(IntArray(2_000_000))) }
    }

    @Test
    fun foreignClassIsRejected() {
        assertFailsWith<IllegalArgumentException> { StopToStopModel.fromBytes(serialize(java.util.ArrayList<Int>())) }
    }

    private fun serialize(o: Any): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        java.io.ObjectOutputStream(out).use { it.writeObject(o) }
        return out.toByteArray()
    }

    @Test
    fun corruptBytesThrowIllegalArgumentException() {
        assertFailsWith<IllegalArgumentException> { StopToStopModel.fromBytes(byteArrayOf(1, 2, 3)) }
    }

    companion object {
        private val ROUTE_INDEX = mapOf("R1" to 0, "R2" to 1, "R3" to 2)

        /**
         * maxDepth 4 rather than the production 6: depth-6 trees over-fit 3,500 rows with five noise
         * features, which narrows the in-sample quantiles and pulls holdout coverage of the nominal
         * 80% interval down to ~0.71; depth 4 centres it at ~0.77.
         */
        private val props = PredictProperties.Smile(minTrainingRows = 1_000, maxDepth = 4)

        private val SEED_INDEX = StopToStopFeatures.NAMES.indexOf("seedSec")

        private fun signal(features: DoubleArray): Double = 1.2 * features[SEED_INDEX]

        private val samples: List<TrainingSample> by lazy {
            val rng = Random(42)
            List(4_000) {
                val features =
                    StopToStopFeatures.vector(
                        stopPathIndex = rng.nextInt(30),
                        seedSec = 60.0 + rng.nextDouble() * 540.0,
                        dayType = DayType.entries[rng.nextInt(DayType.entries.size)],
                        timeBand = TimeBand.entries[rng.nextInt(TimeBand.entries.size)],
                        tripStartSec = rng.nextInt(86_400),
                        directionId = rng.nextInt(2),
                        routeIndex = rng.nextInt(ROUTE_INDEX.size),
                    )
                val truth = signal(features)
                TrainingSample(features, truth + rng.nextGaussian() * (5.0 + 0.1 * truth))
            }
        }
        private val training by lazy { samples.take(3_500) }
        private val holdout by lazy { samples.drop(3_500) }

        /** Trained once and shared: three 200-tree ensembles are the slow part of this suite. */
        private val model: StopToStopModel by lazy {
            requireNotNull(SmileTrainer(props).train(training, ROUTE_INDEX)) { "training returned null" }
        }
    }
}
