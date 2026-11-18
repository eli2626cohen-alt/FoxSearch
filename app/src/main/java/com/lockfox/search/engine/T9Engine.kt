package com.lockfox.search.engine

import java.text.Normalizer

object T9Engine {

    // Mapeo T9 estándar (Inglés, Español, y Hebreo estándar israelí RTL)
    private val CHAR_TO_T9 = HashMap<Char, Char>(128).apply {
        for (d in '0'..'9') put(d, d)

        // Español / Inglés
        "abc".forEach { put(it, '2') }
        "def".forEach { put(it, '3') }
        "ghi".forEach { put(it, '4') }
        "jkl".forEach { put(it, '5') }
        "mnoñ".forEach { put(it, '6') }
        "pqrs".forEach { put(it, '7') }
        "tuv".forEach { put(it, '8') }
        "wxyz".forEach { put(it, '9') }

        // Hebreo Estándar Teclado Físico Israelí (RTL)
        "דהו".forEach { put(it, '2') }
        "אבג".forEach { put(it, '3') }
        "מםנן".forEach { put(it, '4') }
        "יכךל".forEach { put(it, '5') }
        "זחט".forEach { put(it, '6') }
        "רשת".forEach { put(it, '7') }
        "צץק".forEach { put(it, '8') }
        "סעפף".forEach { put(it, '9') }
    }

    // Mapeo T9 alternativo para Hebreo (Orden alfabético tradicional LTR)
    private val CHAR_TO_T9_ALT_HEBREW = HashMap<Char, Char>(64).apply {
        for (d in '0'..'9') put(d, d)
        "אבג".forEach { put(it, '2') }
        "דהו".forEach { put(it, '3') }
        "זחט".forEach { put(it, '4') }
        "יכךל".forEach { put(it, '5') }
        "מםנן".forEach { put(it, '6') }
        "סע".forEach { put(it, '7') }
        "פףצץ".forEach { put(it, '8') }
        "קרשת".forEach { put(it, '9') }
    }

    fun normalize(input: String): String {
        if (input.isEmpty()) return ""
        val decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
        val sb = StringBuilder(decomposed.length)
        for (c in decomposed) {
            if (Character.getType(c) != Character.NON_SPACING_MARK.toInt() || c == 'ñ' || c == 'Ñ') {
                sb.append(c.lowercaseChar())
            }
        }
        return sb.toString()
    }

    fun toT9(input: String): String {
        val norm = normalize(input)
        val sb = StringBuilder(norm.length)
        for (c in norm) {
            val mapped = CHAR_TO_T9[c]
            if (mapped != null) {
                sb.append(mapped)
            } else if (c.isWhitespace()) {
                sb.append('0')
            }
        }
        return sb.toString()
    }

    fun toT9Alt(input: String): String {
        val norm = normalize(input)
        val sb = StringBuilder(norm.length)
        var hasHebrew = false
        for (c in norm) {
            val mappedHebrew = CHAR_TO_T9_ALT_HEBREW[c]
            if (mappedHebrew != null) {
                hasHebrew = true
                sb.append(mappedHebrew)
            } else {
                val mapped = CHAR_TO_T9[c]
                if (mapped != null) {
                    sb.append(mapped)
                } else if (c.isWhitespace()) {
                    sb.append('0')
                }
            }
        }
        return if (hasHebrew) sb.toString() else ""
    }

    fun cleanPhone(phone: String): String = phone.filter { it.isDigit() }

    fun matchScore(
        queryRaw: String,
        itemNormalized: String,
        itemT9: String,
        itemT9Alt: String = "",
        cleanPhone: String?
    ): Int {
        val query = normalize(queryRaw).trim()
        if (query.isEmpty()) return 0

        val isNumericQuery = query.all { it.isDigit() }
        var score = 0

        // Búsqueda por texto directo (Táctil / QWERTY / ABC - Español, Inglés y Hebreo)
        if (itemNormalized.startsWith(query)) score += 100
        else if (itemNormalized.contains(" $query")) score += 85
        else if (itemNormalized.contains(query)) score += 60

        // Búsqueda simultánea T9 (ES/EN/HE) + números de teléfono
        if (isNumericQuery) {
            if (itemT9.startsWith(query)) score = maxOf(score, 95)
            else if (itemT9.contains("0$query")) score = maxOf(score, 80)
            else if (itemT9.contains(query)) score = maxOf(score, 55)

            if (itemT9Alt.isNotEmpty()) {
                if (itemT9Alt.startsWith(query)) score = maxOf(score, 95)
                else if (itemT9Alt.contains("0$query")) score = maxOf(score, 80)
                else if (itemT9Alt.contains(query)) score = maxOf(score, 55)
            }

            if (!cleanPhone.isNullOrEmpty()) {
                if (cleanPhone.contains(query)) {
                    score = maxOf(score, if (cleanPhone.endsWith(query)) 90 else 75)
                } else if (query.length >= 6) {
                    val qSuffix = query.takeLast(8)
                    val pSuffix = cleanPhone.takeLast(8)
                    if (pSuffix.isNotEmpty() && (pSuffix.contains(qSuffix) || qSuffix.contains(pSuffix))) {
                        score = maxOf(score, 85)
                    }
                }
            }
        }

        return score
    }
}
