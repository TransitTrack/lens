package eu.transittrack

fun median(values: List<Int>): Int? {
    if (values.isEmpty()) {
        return null
    }
    val s = values.sorted()
    return if (s.size % 2 == 1) s[s.size / 2] else ((s[s.size / 2 - 1] + s[s.size / 2]) / 2)
}
