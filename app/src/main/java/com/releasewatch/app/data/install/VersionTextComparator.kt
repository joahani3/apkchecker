package com.releasewatch.app.data.install

object VersionTextComparator {

    // Only matches a dotted version number like "1.0.31" (at least one dot), not a bare
    // standalone number. Release titles often carry unrelated numbers alongside the version
    // (e.g. "v1.0.31 (versionCode 32)"), and grabbing every digit run would fold that "32" into
    // the comparison and make an up-to-date install look outdated.
    private val versionPattern = Regex("""\d+(?:\.\d+)+""")

    // Returns null when either string has no dotted version number to compare, since tag/title
    // naming schemes vary too much to assume a match otherwise.
    fun isOlder(currentVersion: String?, latestVersion: String?): Boolean? {
        val current = extractVersion(currentVersion) ?: return null
        val latest = extractVersion(latestVersion) ?: return null

        val length = maxOf(current.size, latest.size)
        for (i in 0 until length) {
            val c = current.getOrElse(i) { 0 }
            val l = latest.getOrElse(i) { 0 }
            if (c != l) return c < l
        }
        return false
    }

    private fun extractVersion(raw: String?): List<Int>? {
        if (raw.isNullOrBlank()) return null
        val match = versionPattern.find(raw) ?: return null
        return match.value.split(".").map { it.toInt() }
    }
}
