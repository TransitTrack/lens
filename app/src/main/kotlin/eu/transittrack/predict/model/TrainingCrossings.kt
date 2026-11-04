package eu.transittrack.predict.model

import java.time.Instant

/**
 * The columns of `avl_stop_crossing` the SMILE_GBM training job reads, for [size] crossings, held
 * column-wise in primitive arrays: about 50 bytes a crossing, against several hundred for a full
 * [AvlStopCrossing] with its strings, instants and boxed numbers. Route ids are deduplicated.
 * Every crossing has a travel time (the training query only returns those).
 */
class TrainingCrossings private constructor(
    val size: Int,
    private val tripPatternIds: LongArray,
    private val revisionIds: LongArray,
    private val stopPathIndexes: IntArray,
    private val observedAtMs: LongArray,
    private val travelSecs: DoubleArray,
    private val tripStartSecs: IntArray,
    private val directionIds: IntArray,
    private val routes: IntArray,
    private val routeIds: List<String>,
) {
    fun tripPatternId(i: Int): Long = tripPatternIds[check(i)]

    fun revisionId(i: Int): Long = revisionIds[check(i)]

    fun stopPathIndex(i: Int): Int = stopPathIndexes[check(i)]

    fun observedAt(i: Int): Instant = Instant.ofEpochMilli(observedAtMs[check(i)])

    fun travelSec(i: Int): Double = travelSecs[check(i)]

    fun tripStartSec(i: Int): Int? = tripStartSecs[check(i)].takeIf { it != NONE }

    fun directionId(i: Int): Int? = directionIds[check(i)].takeIf { it != NONE }

    fun routeId(i: Int): String? = routes[check(i)].takeIf { it != NONE }?.let(routeIds::get)

    private fun check(i: Int): Int {
        if (i !in 0..<size) throw IndexOutOfBoundsException("index $i, size $size")
        return i
    }

    /** Appends crossings one at a time; the arrays grow by doubling, starting at [initialCapacity]. */
    class Builder(
        initialCapacity: Int = 1_024,
    ) {
        private var size = 0
        private var tripPatternIds = LongArray(initialCapacity.coerceAtLeast(1))
        private var revisionIds = LongArray(tripPatternIds.size)
        private var stopPathIndexes = IntArray(tripPatternIds.size)
        private var observedAtMs = LongArray(tripPatternIds.size)
        private var travelSecs = DoubleArray(tripPatternIds.size)
        private var tripStartSecs = IntArray(tripPatternIds.size)
        private var directionIds = IntArray(tripPatternIds.size)
        private var routes = IntArray(tripPatternIds.size)
        private val routeIds = ArrayList<String>()
        private val routeIndex = HashMap<String, Int>()

        fun add(
            tripPatternId: Long,
            revisionId: Long,
            stopPathIndex: Int,
            observedAt: Instant,
            travelSec: Double,
            tripStartSec: Int?,
            directionId: Int?,
            routeId: String?,
        ) {
            if (size == tripPatternIds.size) grow()
            tripPatternIds[size] = tripPatternId
            revisionIds[size] = revisionId
            stopPathIndexes[size] = stopPathIndex
            observedAtMs[size] = observedAt.toEpochMilli()
            travelSecs[size] = travelSec
            tripStartSecs[size] = tripStartSec ?: NONE
            directionIds[size] = directionId ?: NONE
            routes[size] = routeId?.let { routeIndex.getOrPut(it) { routeIds.add(it).let { routeIds.size - 1 } } } ?: NONE
            size++
        }

        private fun grow() {
            val capacity = tripPatternIds.size * 2
            tripPatternIds = tripPatternIds.copyOf(capacity)
            revisionIds = revisionIds.copyOf(capacity)
            stopPathIndexes = stopPathIndexes.copyOf(capacity)
            observedAtMs = observedAtMs.copyOf(capacity)
            travelSecs = travelSecs.copyOf(capacity)
            tripStartSecs = tripStartSecs.copyOf(capacity)
            directionIds = directionIds.copyOf(capacity)
            routes = routes.copyOf(capacity)
        }

        fun build(): TrainingCrossings =
            TrainingCrossings(
                size, tripPatternIds, revisionIds, stopPathIndexes, observedAtMs, travelSecs, tripStartSecs,
                directionIds, routes, routeIds.toList(),
            )
    }

    private companion object {
        /** A missing trip start, direction or route. No real value is this: they are small and non-negative. */
        const val NONE = Int.MIN_VALUE
    }
}
