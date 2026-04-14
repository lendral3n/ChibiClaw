package com.chibiclaw.executor.tier0

import com.chibiclaw.executor.UtilityAction
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Phase 6 — pure-logic utility executor. No accessibility, no network,
 * no model inference. This tier is the cheapest path: Gemma should route
 * "5km in miles" / "12 USD to IDR" / "2+2*7" here instead of opening a
 * calculator app.
 *
 * Kinds:
 *   calc         — safe arithmetic (whitelist: + - * / % ^ ( ) . digits)
 *   unit_convert — length / mass / temperature / volume conversions
 *   timezone     — current time in a named zone
 *   translate    — ML Kit on-device translation (downloads model the first
 *                  time; subsequent calls are offline)
 *
 * **Note (Phase 10 fix):** the old implementation tried to use
 * `javax.script.ScriptEngineManager` as a primary calculator. That API is
 * part of Java SE but **not shipped with Android** — the class is resolved
 * at load time by Hilt's constructor injection, which made
 * [UtilityExecutor.&lt;init&gt;] throw `NoClassDefFoundError`. The resulting
 * crash cascaded into [com.chibiclaw.service.ChibiService.onCreate] (Hilt
 * injects tier-0 dependencies) and the whole app force-closed on launch.
 * We now use [ShuntingYard] exclusively — it handles every operator we
 * advertise (`+ - * / % ^ ( )`) and has zero external dependencies.
 */
@Singleton
class UtilityExecutor @Inject constructor() {

    suspend fun perform(action: UtilityAction): String {
        return when (action.kind.lowercase()) {
            "calc" -> calc(action.input)
            "unit_convert" -> unitConvert(action.input, action.param)
            "timezone" -> timezone(action.input)
            "translate" -> translate(action.input, action.param)
            else -> "utility_error: unknown kind=${action.kind}"
        }
    }

    private fun calc(expr: String): String {
        if (expr.isBlank()) return "calc_error: empty"
        // Whitelist check — we absolutely do NOT want unsafe expression
        // characters. Only numbers, ops, parens, decimal, whitespace.
        if (!expr.matches(Regex("[0-9+\\-*/%^().\\s]+"))) {
            return "calc_error: only digits and + - * / % ^ ( ) allowed"
        }
        return try {
            val value = ShuntingYard.eval(expr)
            "calc: $expr = $value"
        } catch (e: Exception) {
            "calc_error: ${e.message}"
        }
    }

    private fun unitConvert(input: String, param: String): String {
        // Input format: "5 km", param: "mi"
        val match = Regex("([0-9]+\\.?[0-9]*)\\s*([a-zA-Z°]+)").find(input)
            ?: return "unit_error: expected '<number> <unit>'"
        val value = match.groupValues[1].toDoubleOrNull() ?: return "unit_error: bad_number"
        val from = match.groupValues[2].lowercase()
        val to = param.trim().lowercase()
        if (to.isEmpty()) return "unit_error: missing target unit"
        val result = UnitTable.convert(value, from, to)
            ?: return "unit_error: unknown pair $from → $to"
        return "unit: $value $from = $result $to"
    }

    private fun timezone(query: String): String {
        val tz = TimeZone.getTimeZone(query)
        // getTimeZone() returns GMT silently on unknown IDs. Reject.
        if (tz.id != query && !query.equals(tz.id, ignoreCase = true)) {
            return "tz_error: unknown_zone=$query"
        }
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss zzz", Locale.getDefault())
        fmt.timeZone = tz
        return "tz: ${fmt.format(Date())}"
    }

    private suspend fun translate(text: String, param: String): String {
        if (text.isBlank()) return "translate_error: empty"
        // param is "src->dst" e.g. "en->id" ; default src=auto(en), dst=id
        val parts = param.split("->", "-", ",").map { it.trim().lowercase() }
        val srcLang = parts.getOrNull(0)?.takeIf { it.isNotEmpty() } ?: "en"
        val dstLang = parts.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: "id"
        val src = TranslateLanguage.fromLanguageTag(srcLang)
            ?: return "translate_error: unknown src=$srcLang"
        val dst = TranslateLanguage.fromLanguageTag(dstLang)
            ?: return "translate_error: unknown dst=$dstLang"
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(src)
            .setTargetLanguage(dst)
            .build()
        val translator = Translation.getClient(options)
        return try {
            suspendCancellableCoroutine { cont ->
                translator.downloadModelIfNeeded()
                    .addOnSuccessListener {
                        translator.translate(text)
                            .addOnSuccessListener { result ->
                                cont.resume("translate: $result")
                            }
                            .addOnFailureListener { e -> cont.resumeWithException(e) }
                    }
                    .addOnFailureListener { e -> cont.resumeWithException(e) }
                cont.invokeOnCancellation { translator.close() }
            }
        } catch (e: Exception) {
            "translate_error: ${e.message}"
        } finally {
            // translator.close() is called on cancellation above. For
            // completion we intentionally keep it alive so subsequent calls
            // don't re-download the model — ML Kit reference-counts these.
        }
    }
}

/**
 * Tiny shunting-yard evaluator used as a fallback when the JS script
 * engine isn't present on the device (some AOSP builds ship without it).
 */
private object ShuntingYard {
    fun eval(expr: String): Double {
        val tokens = tokenize(expr)
        val output = ArrayDeque<String>()
        val ops = ArrayDeque<String>()
        for (tok in tokens) {
            when {
                tok.toDoubleOrNull() != null -> output.addLast(tok)
                tok == "(" -> ops.addLast(tok)
                tok == ")" -> {
                    while (ops.isNotEmpty() && ops.last() != "(") output.addLast(ops.removeLast())
                    if (ops.isNotEmpty() && ops.last() == "(") ops.removeLast()
                }
                else -> {
                    while (ops.isNotEmpty() && prec(ops.last()) >= prec(tok)) {
                        output.addLast(ops.removeLast())
                    }
                    ops.addLast(tok)
                }
            }
        }
        while (ops.isNotEmpty()) output.addLast(ops.removeLast())
        val stack = ArrayDeque<Double>()
        for (tok in output) {
            val d = tok.toDoubleOrNull()
            if (d != null) {
                stack.addLast(d)
            } else {
                val b = stack.removeLast()
                val a = stack.removeLast()
                stack.addLast(apply(a, b, tok))
            }
        }
        return stack.last()
    }

    private fun tokenize(expr: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < expr.length) {
            val c = expr[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    val start = i
                    while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) i++
                    tokens.add(expr.substring(start, i))
                }
                c in "+-*/%^()" -> { tokens.add(c.toString()); i++ }
                else -> throw IllegalArgumentException("Unknown char: $c")
            }
        }
        return tokens
    }

    private fun prec(op: String): Int = when (op) {
        "+", "-" -> 1
        "*", "/", "%" -> 2
        "^" -> 3
        else -> 0
    }

    private fun apply(a: Double, b: Double, op: String): Double = when (op) {
        "+" -> a + b
        "-" -> a - b
        "*" -> a * b
        "/" -> a / b
        "%" -> a % b
        "^" -> Math.pow(a, b)
        else -> throw IllegalArgumentException("Unknown op: $op")
    }
}

/** Tiny unit conversion table used by [UtilityExecutor.unitConvert]. */
private object UnitTable {
    // Everything stored as a factor to a canonical SI unit.
    private val length = mapOf(
        "m" to 1.0, "km" to 1000.0, "cm" to 0.01, "mm" to 0.001,
        "mi" to 1609.344, "yd" to 0.9144, "ft" to 0.3048, "in" to 0.0254
    )
    private val mass = mapOf(
        "g" to 1.0, "kg" to 1000.0, "mg" to 0.001, "t" to 1_000_000.0,
        "lb" to 453.59237, "oz" to 28.3495
    )
    private val volume = mapOf(
        "l" to 1.0, "ml" to 0.001, "m3" to 1000.0,
        "gal" to 3.78541, "floz" to 0.0295735
    )

    fun convert(value: Double, from: String, to: String): Double? {
        // Temperature needs a dedicated path — no linear factor.
        if (from in setOf("c", "f", "k", "°c", "°f", "°k") || to in setOf("c", "f", "k", "°c", "°f", "°k")) {
            return convertTemperature(value, from.trimStart('°'), to.trimStart('°'))
        }
        listOf(length, mass, volume).forEach { table ->
            val f = table[from]; val t = table[to]
            if (f != null && t != null) return value * f / t
        }
        return null
    }

    private fun convertTemperature(value: Double, from: String, to: String): Double? {
        val asC = when (from) {
            "c" -> value
            "f" -> (value - 32) * 5.0 / 9.0
            "k" -> value - 273.15
            else -> return null
        }
        return when (to) {
            "c" -> asC
            "f" -> asC * 9.0 / 5.0 + 32
            "k" -> asC + 273.15
            else -> null
        }
    }
}
