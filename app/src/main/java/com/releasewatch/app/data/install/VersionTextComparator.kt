package com.releasewatch.app.data.install

object VersionTextComparator {

    // Extracts dot/dash-separated numeric groups (e.g. "v2.3.1-beta" -> [2, 3, 1]) and compares
    // them component-wise. Returns null when either string has no digits to compare, since tag
    // naming schemes vary too much to assume a match otherwise.
    fun isOlder(currentVersion: String?, latestVersion: String?): Boolean? {
        val current = extractNumbers(currentVersion) ?: return null
        val latest = extractNumbers(latestVersion) ?: return null

        val length = maxOf(current.size, latest.size)
        for (i in 0 until length) {
            val c = current.getOrElse(i) { 0 }
            val l = latest.getOrElse(i) { 0 }
            if (c != l) return c < l
        }
        return false
    }

    private fun extractNumbers(raw: String?): List<Int>? {
        if (raw.isNullOrBlank()) return null
        val numbers = Regex("\\d+").findAll(raw).map { it.value.toInt() }.toList()
        return numbers.ifEmpty { null }
    }
}
