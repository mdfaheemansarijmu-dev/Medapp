package com.example.util

object ChapterSimilarityHelper {

    private val STOP_WORDS = setOf(
        "the", "of", "and", "in", "for", "ch", "chapter", "part", "unit", "section",
        "study", "general", "introduction", "to", "on", "system", "module", "lecture"
    )

    private val REMEDY_SUFFIXES = setOf(
        "nigricans", "nigrum", "nig", "album", "alba", "alb", "officinale", "officinalis",
        "communis", "vulgaris", "purpurea", "dioica", "sativa", "annua", "perennis",
        "napellus", "clavatum", "vomica", "toxicodendron", "tox", "foetida", "maritima",
        "metallicum", "met", "carbonica", "carb", "phosphorica", "phos", "muriatica", "mur",
        "sulphurica", "sulph", "sulf", "arsenicosa", "nitrica", "nit", "iodatum", "iod",
        "bichromicum", "bich", "bromatum", "crudum", "crud", "oxydatum", "aceticum"
    )

    /**
     * Collapses duplicate consecutive letters (e.g. "pulsatilla" -> "pulsatila", "cannabis" -> "canabis")
     * and strips non-alphanumeric characters.
     */
    fun cleanRoot(word: String): String {
        val lower = word.lowercase().trim()
        val sb = StringBuilder()
        for (ch in lower) {
            if (ch.isLetterOrDigit()) {
                if (sb.isEmpty() || sb.last() != ch) {
                    sb.append(ch)
                }
            }
        }
        return sb.toString()
    }

    /**
     * Tokenizes a title into cleaned, significant words.
     */
    fun tokenize(title: String): List<String> {
        return title.split(Regex("[^a-zA-Z0-9]+"))
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() && it !in STOP_WORDS }
    }

    /**
     * Extracts the primary core stem of a remedy / chapter title,
     * stripping secondary species/adjective words if present.
     */
    fun extractPrimaryStem(title: String): String {
        val tokens = tokenize(title)
        if (tokens.isEmpty()) return cleanRoot(title)
        val firstClean = cleanRoot(tokens.first())
        return firstClean
    }

    /**
     * Checks if two chapter titles in the same subject represent the same topic.
     * Examples that match:
     * - "pulsatila" and "pulsatilla nigricans"
     * - "pulsatilla nigricans" and "pulsatila nigrum"
     * - "Cardiovascular" and "Cardiovascular System"
     * - "Upper Limb" and "Upper Limb - Bones"
     * - "Aconite" and "Aconitum Napellus"
     */
    fun isSimilar(title1: String, title2: String): Boolean {
        val t1 = title1.trim().lowercase()
        val t2 = title2.trim().lowercase()
        if (t1.isEmpty() || t2.isEmpty()) return false
        if (t1 == t2) return true

        // 1. Direct clean root check
        val c1 = cleanRoot(t1)
        val c2 = cleanRoot(t2)
        if (c1 == c2) return true

        // Substring check on collapsed clean roots
        if (minOf(c1.length, c2.length) >= 4) {
            if (c1.contains(c2) || c2.contains(c1)) {
                return true
            }
        }

        // 2. Tokenize both titles
        val tokens1 = tokenize(t1)
        val tokens2 = tokenize(t2)
        if (tokens1.isEmpty() || tokens2.isEmpty()) return false

        // Check if one token list is subset of another
        val set1 = tokens1.map { cleanRoot(it) }.toSet()
        val set2 = tokens2.map { cleanRoot(it) }.toSet()
        if (set1.containsAll(set2) || set2.containsAll(set1)) {
            val shorter = minOf(set1.size, set2.size)
            if (shorter > 0) return true
        }

        // 3. Primary Root Word matching
        // In medical/BHMS/MBBS syllabi, the first word is almost always the drug genus or organ/system
        // e.g. "pulsatilla" vs "pulsatila", "aconitum" vs "aconite", "carbohydrate" vs "carbohydrates"
        val r1 = cleanRoot(tokens1.first())
        val r2 = cleanRoot(tokens2.first())

        if (r1.length >= 4 && r2.length >= 4) {
            if (r1 == r2) return true
            // Edit distance <= 1 for typos (e.g. "pulsatila" vs "pulsatilla" -> distance 0 after cleanRoot)
            if (levenshteinDistance(r1, r2) <= 1) return true
            // Common prefix match of length >= 5 (e.g. "aconit" in "aconite" and "aconitum")
            if (r1.length >= 5 && r2.length >= 5 && (r1.startsWith(r2.take(5)) || r2.startsWith(r1.take(5)))) {
                return true
            }
        }

        // 4. Secondary species filtering check
        // e.g. "pulsatilla nigricans" vs "pulsatila nigrum"
        // If the secondary words are known homoeopathic/botanical species and primary root matches
        val nonSuffix1 = tokens1.filter { it !in REMEDY_SUFFIXES }.map { cleanRoot(it) }
        val nonSuffix2 = tokens2.filter { it !in REMEDY_SUFFIXES }.map { cleanRoot(it) }
        if (nonSuffix1.isNotEmpty() && nonSuffix2.isNotEmpty()) {
            val base1 = nonSuffix1.first()
            val base2 = nonSuffix2.first()
            if (base1.length >= 4 && base2.length >= 4) {
                if (base1 == base2 || levenshteinDistance(base1, base2) <= 1) {
                    return true
                }
            }
        }

        // 5. Significant word overlap (any token of length >= 5 matches)
        for (tok1 in tokens1) {
            val cr1 = cleanRoot(tok1)
            if (cr1.length >= 5 && cr1 !in REMEDY_SUFFIXES) {
                for (tok2 in tokens2) {
                    val cr2 = cleanRoot(tok2)
                    if (cr2.length >= 5 && cr2 !in REMEDY_SUFFIXES) {
                        if (cr1 == cr2 || levenshteinDistance(cr1, cr2) <= 1) {
                            return true
                        }
                    }
                }
            }
        }

        // 6. Overall Levenshtein similarity on normalized text
        val maxLen = maxOf(c1.length, c2.length)
        if (maxLen >= 6) {
            val dist = levenshteinDistance(c1, c2)
            if (dist <= 2 || (dist.toDouble() / maxLen.toDouble()) <= 0.22) {
                return true
            }
        }

        return false
    }

    fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}
