package com.example.util

object ChapterSimilarityHelper {

    private val STOP_WORDS = setOf(
        "the", "of", "and", "in", "for", "ch", "chapter", "part", "unit", "section",
        "study", "general", "introduction", "intro", "to", "on", "module", "lecture",
        "dr", "prof", "notes", "drug", "remedy", "portion", "portions", "topic", "topics"
    )

    // Optional botanical / natural species epithets that students frequently omit or vary
    // e.g. "Pulsatilla" vs "Pulsatilla Nigricans", "Aconitum" vs "Aconitum Napellus"
    private val OPTIONAL_SPECIES_EPITHETS = setOf(
        "nigricans", "nigrum", "nigra", "nig",
        "album", "alba", "alb",
        "napellus", "nap",
        "officinale", "officinalis",
        "communis", "vulgaris",
        "purpurea", "dioica", "sativa", "annua", "perennis",
        "clavatum", "vomica",
        "foetida", "maritima", "montana", "amara",
        "pratensis", "occidentalis", "radicans", "arvensis",
        "major", "minor", "system"
    )

    // Chemical salt radicals & qualifiers that define DISTINCT medicines in a group.
    // Two remedies sharing a group name (e.g., Calcaria) but having distinct radicals
    // (Carbonica, Phosphorica, Fluorica) are completely DIFFERENT medicines.
    private val DISTINCT_SALT_RADICALS = setOf(
        "carbonica", "phosphorica", "fluorica", "muriatica", "sulphurica",
        "iodata", "arsenica", "nitrica", "metallicum", "bichromicum",
        "bromata", "crudum", "solubilis", "corrosivus", "cyanatus",
        "dulcis", "aceticum", "picrata", "silicata", "caustica",
        "oxalicum", "salicylicum", "benzoicum", "hydrocyanicum",
        "flavus", "ruber", "moschata"
    )

    // Canonical dictionary of abbreviations and common spelling variations
    private val CANONICAL_WORDS = mapOf(
        // Salts and Radicals (distinct medicines)
        "calc" to "calcaria",
        "calcarea" to "calcaria",
        "carb" to "carbonica",
        "carbonic" to "carbonica",
        "carbonicum" to "carbonica",
        "phos" to "phosphorica",
        "phosphoric" to "phosphorica",
        "phosphoricum" to "phosphorica",
        "fluor" to "fluorica",
        "flour" to "fluorica",
        "flourica" to "fluorica",
        "fluoric" to "fluorica",
        "fluoricum" to "fluorica",
        "flouricum" to "fluorica",
        "mur" to "muriatica",
        "muriatic" to "muriatica",
        "muriaticum" to "muriatica",
        "sulph" to "sulphurica",
        "sulf" to "sulphurica",
        "sulphuric" to "sulphurica",
        "sulfuric" to "sulphurica",
        "sulphuricum" to "sulphurica",
        "sulfuricum" to "sulphurica",
        "iod" to "iodata",
        "iodat" to "iodata",
        "iodatum" to "iodata",
        "ars" to "arsenica",
        "arsen" to "arsenica",
        "arsenicosa" to "arsenica",
        "arsenicosum" to "arsenica",
        "nit" to "nitrica",
        "nitric" to "nitrica",
        "nitricum" to "nitrica",
        "met" to "metallicum",
        "metal" to "metallicum",
        "bich" to "bichromicum",
        "bichrom" to "bichromicum",
        "crud" to "crudum",
        "sol" to "solubilis",
        "corr" to "corrosivus",
        "cyan" to "cyanatus",
        "acet" to "aceticum",
        "acetic" to "aceticum",
        "pic" to "picrata",
        "picric" to "picrata",
        "picricum" to "picrata",
        "silic" to "silicata",
        "silicata" to "silicata",
        "flav" to "flavus",
        "flavum" to "flavus",
        "rub" to "ruber",
        "rubrum" to "ruber",

        // Genus / Base remedies
        "nat" to "natrum",
        "natr" to "natrum",
        "kali" to "kali",
        "kal" to "kali",
        "mag" to "magnesia",
        "magn" to "magnesia",
        "bar" to "baryta",
        "baryt" to "baryta",
        "ammon" to "ammonium",
        "amm" to "ammonium",
        "ferr" to "ferrum",
        "merc" to "mercurius",
        "aur" to "aurum",
        "arg" to "argentum",
        "lyc" to "lycopodium",
        "puls" to "pulsatilla",
        "acon" to "aconitum",
        "aconite" to "aconitum",
        "bell" to "belladonna",
        "bry" to "bryonia",
        "rhus" to "rhus",
        "tox" to "toxicodendron",
        "silica" to "silicea",
        "sulfur" to "sulphur",

        // Anatomical & System abbreviations
        "cvs" to "cardiovascular",
        "cns" to "central nervous",
        "git" to "gastrointestinal",
        "rs" to "respiratory",
        "fmt" to "forensic",
        "psm" to "community medicine",
        "obg" to "obstetrics",
        "ent" to "otorhinolaryngology"
    )

    fun normalizeSubjectCore(sub: String): String {
        var s = sub.trim().lowercase()
        val removePrefixes = listOf(
            "homoeopathic ", "homeopathic ", "ayurvedic ", "human ", "general ",
            "systemic ", "principles of ", "introduction to ", "clinical "
        )
        for (pref in removePrefixes) {
            if (s.startsWith(pref)) s = s.removePrefix(pref).trim()
        }
        val removeSuffixes = listOf(
            " and homoeopathic philosophy", " & homoeopathic philosophy",
            " & homoeopathic materia medica", " and homoeopathic materia medica",
            " & toxicology", " and toxicology", " and philosophy"
        )
        for (suf in removeSuffixes) {
            if (s.endsWith(suf)) s = s.removeSuffix(suf).trim()
        }
        return s.replace("&", "and").replace(Regex("\\s+"), " ").trim()
    }

    fun isSameOrSimilarSubject(s1: String, s2: String): Boolean {
        val sub1 = s1.trim().lowercase()
        val sub2 = s2.trim().lowercase()
        if (sub1.isEmpty() || sub2.isEmpty()) return true
        if (sub1 == sub2) return true
        if (sub1.contains(sub2) || sub2.contains(sub1)) return true

        val core1 = normalizeSubjectCore(sub1)
        val core2 = normalizeSubjectCore(sub2)
        if (core1 == core2) return true
        if (core1.contains(core2) || core2.contains(core1)) return true

        return false
    }

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
     * Canonicalizes an individual token:
     * - Strips non-alphanumerics
     * - Maps known synonyms and homoeopathic abbreviations (e.g. "carb" -> "carbonica", "phos" -> "phosphorica", "flour" -> "fluorica")
     * - Collapses double letters
     */
    fun canonicalizeWord(word: String): String {
        val clean = word.lowercase().trim().replace(Regex("[^a-z0-9]"), "")
        if (clean.isBlank()) return ""
        val canonical = CANONICAL_WORDS[clean] ?: clean
        return canonical
    }

    /**
     * Tokenizes a title into canonicalized, significant words.
     */
    fun tokenize(title: String): List<String> {
        val rawTokens = title.split(Regex("[^a-zA-Z0-9]+"))
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() && it !in STOP_WORDS }

        val tokens = mutableListOf<String>()
        var i = 0
        while (i < rawTokens.size) {
            // Check 2-word combinations in synonym map (e.g. "rhus tox" -> "rhus toxicodendron")
            if (i + 1 < rawTokens.size) {
                val pair = "${rawTokens[i]} ${rawTokens[i + 1]}"
                if (CANONICAL_WORDS.containsKey(pair)) {
                    val mapped = CANONICAL_WORDS[pair]!!
                    tokens.addAll(mapped.split(" ").map { canonicalizeWord(it) })
                    i += 2
                    continue
                }
            }
            val canonical = canonicalizeWord(rawTokens[i])
            if (canonical.isNotBlank() && canonical !in STOP_WORDS) {
                // If canonical expanded to multiple words
                if (canonical.contains(" ")) {
                    tokens.addAll(canonical.split(" ").map { canonicalizeWord(it) })
                } else {
                    tokens.add(canonical)
                }
            }
            i++
        }
        return tokens
    }

    /**
     * Checks if two individual canonical words are equivalent (exact or clean root or 1-edit typo).
     */
    private fun areWordsEquivalent(w1: String, w2: String): Boolean {
        if (w1 == w2) return true
        val c1 = cleanRoot(w1)
        val c2 = cleanRoot(w2)
        if (c1 == c2) return true
        if (c1.length >= 5 && c2.length >= 5 && levenshteinDistance(c1, c2) <= 1) return true
        return false
    }

    /**
     * Intelligent check to determine if two chapter titles represent the EXACT SAME entity (true)
     * or DIFFERENT entities/chapters (false).
     *
     * In homoeopathy and medical sciences:
     * - "Calcaria Carbonica", "Calcaria Phosphorica", and "Calcaria Fluorica" (or "Calcaria Flour")
     *   are THREE DIFFERENT MEDICINES with distinct radicals ("carbonica", "phosphorica", "fluorica").
     *   They are NOT duplicates!
     * - "Calcaria Carbonica" and "Calc Carb" ARE duplicates (alias/abbreviation).
     * - "Calcaria Fluorica" and "Calcarea Flour" ARE duplicates (spelling variant / alias).
     * - "Pulsatilla" and "Pulsatilla Nigricans" ARE duplicates (natural species epithet).
     * - "Pulsatilla" and "Pulsatila" ARE duplicates (typo).
     * - "Upper Limb - Bones" and "Upper Limb - Muscles" are DIFFERENT chapters.
     * - "Assignment 1" and "Assignment 2" are DIFFERENT assignments.
     */
    fun isSimilar(
        title1: String,
        title2: String,
        subject1: String? = null,
        subject2: String? = null
    ): Boolean {
        // 0. If subjects are provided and distinct, they are definitely different
        if (!subject1.isNullOrBlank() && !subject2.isNullOrBlank()) {
            if (!isSameOrSimilarSubject(subject1, subject2)) return false
        }

        val t1 = title1.trim().lowercase()
        val t2 = title2.trim().lowercase()
        if (t1.isEmpty() || t2.isEmpty()) return false
        if (t1 == t2) return true

        // 1. Direct clean root comparison of full string
        val cr1 = cleanRoot(t1)
        val cr2 = cleanRoot(t2)
        if (cr1 == cr2) return true

        // 2. Tokenize both titles using canonical vocabulary
        val tokens1 = tokenize(t1)
        val tokens2 = tokenize(t2)
        if (tokens1.isEmpty() || tokens2.isEmpty()) return false

        // Check exact token sequence match
        if (tokens1 == tokens2) return true

        // Check if concatenated canonical tokens are identical
        val joined1 = cleanRoot(tokens1.joinToString(""))
        val joined2 = cleanRoot(tokens2.joinToString(""))
        if (joined1 == joined2) return true

        // 3. Distinguishing Term & Radical Analysis
        // Find words in title 1 not present in title 2, and vice versa
        val diff1 = tokens1.filter { tok1 -> tokens2.none { tok2 -> areWordsEquivalent(tok1, tok2) } }
        val diff2 = tokens2.filter { tok2 -> tokens1.none { tok1 -> areWordsEquivalent(tok1, tok2) } }

        // Both titles have non-matching terms:
        // e.g. "Calcaria Carbonica" vs "Calcaria Phosphorica" -> diff1=["carbonica"], diff2=["phosphorica"]
        // e.g. "Calcaria Carbonica" vs "Calcaria Flour" -> diff1=["carbonica"], diff2=["fluorica"]
        // e.g. "Upper Limb - Bones" vs "Upper Limb - Muscles" -> diff1=["bones"], diff2=["muscles"]
        // e.g. "Chapter 1" vs "Chapter 2" -> diff1=["1"], diff2=["2"]
        if (diff1.isNotEmpty() && diff2.isNotEmpty()) {
            // These titles have contrasting qualifying terms -> DIFFERENT ENTITIES!
            return false
        }

        // One title's tokens are a subset of the other's tokens:
        // e.g. "Pulsatilla" (diff1=[]) vs "Pulsatilla Nigricans" (diff2=["nigricans"])
        // e.g. "Cardiovascular" (diff1=[]) vs "Cardiovascular System" (diff2=["system"])
        if (diff1.isEmpty() && diff2.isNotEmpty()) {
            // The extra words in diff2 must ONLY be recognized optional botanical epithets or generic descriptors
            val hasContradictoryRadicalOrDigit = diff2.any { extra ->
                extra in DISTINCT_SALT_RADICALS || extra.any { it.isDigit() }
            }
            if (hasContradictoryRadicalOrDigit) {
                // e.g. "Calcaria" vs "Calcaria Carbonica" - carbonica is a distinct radical, not just a filler
                return false
            }
            val allOptional = diff2.all { extra ->
                extra in OPTIONAL_SPECIES_EPITHETS || cleanRoot(extra) in OPTIONAL_SPECIES_EPITHETS
            }
            if (allOptional) return true
            return false
        }

        if (diff2.isEmpty() && diff1.isNotEmpty()) {
            val hasContradictoryRadicalOrDigit = diff1.any { extra ->
                extra in DISTINCT_SALT_RADICALS || extra.any { it.isDigit() }
            }
            if (hasContradictoryRadicalOrDigit) return false
            val allOptional = diff1.all { extra ->
                extra in OPTIONAL_SPECIES_EPITHETS || cleanRoot(extra) in OPTIONAL_SPECIES_EPITHETS
            }
            if (allOptional) return true
            return false
        }

        // 4. Overall typo distance on normalized joined text (for minor single-letter typos)
        val maxLen = maxOf(joined1.length, joined2.length)
        if (maxLen >= 6) {
            val dist = levenshteinDistance(joined1, joined2)
            // Edit distance <= 1 for titles >= 6 chars, provided neither has differing digits
            val digits1 = joined1.filter { it.isDigit() }
            val digits2 = joined2.filter { it.isDigit() }
            if (dist <= 1 && digits1 == digits2) {
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
