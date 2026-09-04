package eu.transittrack.predict.learn

data class Crossing(
    val stopPathIndex: Int,
    val observedTravelTimeSec: Double,
)

fun detectCrossings(
    prevStopPathIndex: Int,
    newStopPathIndex: Int,
    elapsedSec: Double,
    stopPathCumM: DoubleArray,
    patternLengthM: Double,
    maxPlausibleSec: Int,
): List<Crossing> {
    // Return empty when no advance
    if (newStopPathIndex <= prevStopPathIndex) {
        return emptyList()
    }

    // Calculate lengths for each crossed stop path
    val lengths = mutableMapOf<Int, Double>()
    var totalLengthM = 0.0

    for (i in (prevStopPathIndex + 1)..newStopPathIndex) {
        val lengthM = if (i + 1 < stopPathCumM.size) {
            stopPathCumM[i + 1] - stopPathCumM[i]
        } else {
            patternLengthM - stopPathCumM[i]
        }
        lengths[i] = lengthM
        totalLengthM += lengthM
    }

    // If degenerate geometry, return empty
    if (totalLengthM <= 0.0) {
        return emptyList()
    }

    // Apportion elapsed time by length share and filter plausible samples
    val crossings = mutableListOf<Crossing>()

    for (i in (prevStopPathIndex + 1)..newStopPathIndex) {
        val lengthM = lengths[i] ?: continue
        val share = lengthM / totalLengthM
        val sampleSec = share * elapsedSec

        // Per-sample filtering: drop if <= 0 or > maxPlausibleSec
        if (sampleSec > 0.0 && sampleSec <= maxPlausibleSec) {
            crossings.add(Crossing(i, sampleSec))
        }
    }

    return crossings
}
