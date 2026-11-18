package com.lockfox.search.engine

import android.content.Context
import com.lockfox.search.R
import com.lockfox.search.model.Category
import com.lockfox.search.model.SearchListItem
import java.text.DecimalFormat

object CommandParser {

    private val MATH_REGEX = Regex("^[0-9+\\-*/().\\s]+$")

    fun parseQuickActions(
        context: Context,
        query: String,
        contacts: List<SearchListItem.Result>
    ): List<SearchListItem> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val actions = mutableListOf<SearchListItem>()

        // 1. Calculadora instantánea
        if (trimmed.length >= 3 && MATH_REGEX.matches(trimmed) && trimmed.any { it in "+-*/" }) {
            evaluateMath(trimmed)?.let { result ->
                actions.add(
                    SearchListItem.Result(
                        id = "calc_$result",
                        category = Category.ACTION,
                        title = "= $result",
                        subtitle = context.getString(R.string.action_calc_subtitle, trimmed),
                        extraData = "COPY:$result",
                        score = 1000
                    )
                )
            }
        }

        // 1.1 Comando o búsqueda de widget (ES / EN / HE)
        val isWidgetCmd = trimmed.contains("widget", ignoreCase = true) ||
                trimmed.contains("barra", ignoreCase = true) ||
                trimmed.contains("bar", ignoreCase = true) ||
                trimmed.contains("וידג'ט") ||
                trimmed.contains("ווידג'ט")

        if (isWidgetCmd) {
            actions.add(
                SearchListItem.Result(
                    id = "cmd_pin_widget",
                    category = Category.ACTION,
                    title = context.getString(R.string.action_pin_widget_title),
                    subtitle = context.getString(R.string.action_pin_widget_subtitle),
                    extraData = "PIN_WIDGET",
                    score = 1000
                )
            )
        }

        // 1.2 Comando de Idiomas (ES: idioma / EN: language / HE: שפה)
        val isLangCmd = trimmed.equals("idioma", ignoreCase = true) ||
                trimmed.equals("idiomas", ignoreCase = true) ||
                trimmed.equals("language", ignoreCase = true) ||
                trimmed.equals("languages", ignoreCase = true) ||
                trimmed.equals("שפה") ||
                trimmed.equals("שפות")

        if (isLangCmd) {
            actions.add(
                SearchListItem.Result(
                    id = "cmd_locale_settings",
                    category = Category.SETTINGS,
                    title = "🌐 Idiomas / Languages / שפות",
                    subtitle = "Ajustes de idioma del teléfono",
                    extraData = "OPEN_LOCALE_SETTINGS",
                    score = 1000
                )
            )
        }

        // 2. Comando: nota (ES) / note (EN) / פתק (HE) <texto>
        val isNoteCmd = trimmed.startsWith("nota ", ignoreCase = true) ||
                trimmed.startsWith("note ", ignoreCase = true) ||
                trimmed.startsWith("פתק ") ||
                trimmed.startsWith("רשום ")

        if (isNoteCmd) {
            val noteBody = trimmed.substringAfter(" ").trim()
            if (noteBody.isNotEmpty()) {
                actions.add(
                    SearchListItem.Result(
                        id = "cmd_note",
                        category = Category.NOTE,
                        title = context.getString(R.string.action_create_note),
                        subtitle = "\"$noteBody\"",
                        extraData = "NOTE:$noteBody",
                        score = 1000
                    )
                )
            }
        }

        // 3. Comando: sms / text (EN) / סמס / הודעה (HE) <contacto/numero> <mensaje>
        val isSmsCmd = trimmed.startsWith("sms ", ignoreCase = true) ||
                trimmed.startsWith("text ", ignoreCase = true) ||
                trimmed.startsWith("סמס ") ||
                trimmed.startsWith("הודעה ")

        if (isSmsCmd) {
            val parts = trimmed.substringAfter(" ").trim().split(" ", limit = 2)
            val targetQuery = parts[0]
            val messageText = if (parts.size > 1) parts[1] else ""

            val matchedContact = contacts.firstOrNull {
                it.normalizedText.contains(T9Engine.normalize(targetQuery)) ||
                (it.phoneNumber != null && T9Engine.cleanPhone(it.phoneNumber).contains(targetQuery))
            }

            val destName = matchedContact?.title ?: targetQuery
            val destPhone = matchedContact?.phoneNumber ?: targetQuery

            actions.add(
                SearchListItem.Result(
                    id = "cmd_sms",
                    category = Category.SMS,
                    title = context.getString(R.string.action_send_sms_to, destName),
                    subtitle = if (messageText.isNotEmpty()) "\"$messageText\"" else context.getString(R.string.action_open_fox_messages),
                    phoneNumber = destPhone,
                    extraData = "SMS:$destPhone|$messageText",
                    score = 1000
                )
            )
        }

        // 4. Marcado y fila compacta de acciones para Números detectados o códigos USSD
        // En lugar de cuatro bloques independientes, se genera una sola fila compacta:
        // [Numero] [Llamar] [SMS] [Copiar] [Guardar]
        val digitsOnly = trimmed.filter { it.isDigit() || it == '*' || it == '#' || it == '+' }
        if (digitsOnly.length >= 3 && digitsOnly.length == trimmed.replace(" ", "").length) {
            actions.add(
                SearchListItem.NumberAction(
                    number = digitsOnly,
                    score = 1000
                )
            )
        }

        return actions
    }

    private fun evaluateMath(expr: String): String? {
        return try {
            val clean = expr.replace(" ", "")
            var pos = -1
            var ch = 0

            fun nextChar() {
                ch = if (++pos < clean.length) clean[pos].code else -1
            }

            fun eat(charToEat: Int): Boolean {
                while (ch == ' '.code) nextChar()
                if (ch == charToEat) {
                    nextChar()
                    return true
                }
                return false
            }

            fun parseExpression(): Double {
                fun parseTerm(): Double {
                    fun parseFactor(): Double {
                        if (eat('+'.code)) return parseFactor()
                        if (eat('-'.code)) return -parseFactor()
                        var x: Double
                        val startPos = pos
                        if (eat('('.code)) {
                            x = parseExpression()
                            if (!eat(')'.code)) throw RuntimeException("Paréntesis sin cerrar")
                        } else if (ch in '0'.code..'9'.code || ch == '.'.code) {
                            while (ch in '0'.code..'9'.code || ch == '.'.code) nextChar()
                            x = clean.substring(startPos, pos).toDouble()
                        } else {
                            throw RuntimeException("Carácter inesperado")
                        }
                        return x
                    }

                    var x = parseFactor()
                    while (true) {
                        x = when {
                            eat('*'.code) -> x * parseFactor()
                            eat('/'.code) -> {
                                val div = parseFactor()
                                if (div == 0.0) throw ArithmeticException("División por cero")
                                x / div
                            }
                            else -> return x
                        }
                    }
                }

                var x = parseTerm()
                while (true) {
                    x = when {
                        eat('+'.code) -> x + parseTerm()
                        eat('-'.code) -> x - parseTerm()
                        else -> return x
                    }
                }
            }

            nextChar()
            val value = parseExpression()
            if (pos < clean.length || value.isNaN() || value.isInfinite()) return null
            DecimalFormat("#.####").format(value)
        } catch (_: Exception) {
            null
        }
    }
}
