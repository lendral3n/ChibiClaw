package com.chibiclaw.agent.initiative.trigger

import com.chibiclaw.agent.initiative.TriggerEvent
import com.chibiclaw.world.WorldSnapshot
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SimplePredicateEvaluator — string expression evaluator dengan grammar
 * minimal tapi cukup untuk standing instruction.
 *
 * Grammar (BNF lite):
 *   expr   := orExpr
 *   orExpr := andExpr ('OR' andExpr)*
 *   andExpr:= notExpr ('AND' notExpr)*
 *   notExpr:= 'NOT' notExpr | cmp
 *   cmp    := value op value | value 'IN' '[' list ']' | value 'BETWEEN' value 'AND' value
 *   op     := '==' | '!=' | '<' | '<=' | '>' | '>='
 *   value  := number | string | accessor
 *   accessor := IDENT('.' IDENT)*('(' ARG ')')?
 *
 * Accessor yang didukung:
 *   - battery.level (int 0-100)
 *   - battery.charging (bool)
 *   - screen.on (bool)
 *   - network.connected (bool)
 *   - time.hour (int 0-23)
 *   - time.minute (int 0-59)
 *   - time.weekday (int 1-7, 1=Senin)
 *   - event.text (string)
 *   - event.title (string)
 *   - event.package (string)
 *   - event.value (int)
 *   - location.distance_from('placeKey') (double meters, butuh memory lookup)
 *
 * Phase 6 evaluator: hand-rolled recursive descent. Phase 9: optional fallback
 * ke LLM-evaluated predicate (PredicateEvaluator.LLM) untuk expression complex.
 *
 * Examples:
 *   "battery.level < 30"
 *   "battery.charging == false AND battery.level < 20"
 *   "time.hour >= 18 AND time.hour <= 22"
 *   "event.package == 'com.whatsapp' AND event.title ~= 'Mama'"
 */
@Singleton
class SimplePredicateEvaluator @Inject constructor() {

    /** True kalau expression evaluate ke true di context yang ada. */
    fun evaluate(expression: String, world: WorldSnapshot?, event: TriggerEvent?): Boolean {
        return runCatching {
            val tokens = tokenize(expression)
            val parser = Parser(tokens)
            val node = parser.parseExpr()
            if (!parser.atEnd()) {
                Timber.w("Predicate trailing tokens: ${tokens.drop(parser.pos)}")
            }
            node.eval(Context(world, event))
        }.onFailure {
            Timber.w(it, "Predicate eval failed: '$expression'")
        }.getOrDefault(false)
    }

    // ── Tokenizer ────────────────────────────────────────────────────────────

    private fun tokenize(input: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c.isWhitespace() -> i++
                c == '(' -> { tokens += Token.LParen; i++ }
                c == ')' -> { tokens += Token.RParen; i++ }
                c == '[' -> { tokens += Token.LBracket; i++ }
                c == ']' -> { tokens += Token.RBracket; i++ }
                c == ',' -> { tokens += Token.Comma; i++ }
                c == '\'' || c == '"' -> {
                    val end = input.indexOf(c, i + 1)
                    require(end > 0) { "Unterminated string at $i" }
                    tokens += Token.StringLit(input.substring(i + 1, end))
                    i = end + 1
                }
                c.isDigit() || (c == '-' && (i + 1) < input.length && input[i + 1].isDigit()) -> {
                    val start = i
                    if (c == '-') i++
                    while (i < input.length && (input[i].isDigit() || input[i] == '.')) i++
                    val numStr = input.substring(start, i)
                    tokens += if ('.' in numStr) Token.NumLit(numStr.toDouble()) else Token.NumLit(numStr.toLong().toDouble())
                }
                c == '=' && (i + 1) < input.length && input[i + 1] == '=' -> { tokens += Token.Op("=="); i += 2 }
                c == '!' && (i + 1) < input.length && input[i + 1] == '=' -> { tokens += Token.Op("!="); i += 2 }
                c == '<' && (i + 1) < input.length && input[i + 1] == '=' -> { tokens += Token.Op("<="); i += 2 }
                c == '>' && (i + 1) < input.length && input[i + 1] == '=' -> { tokens += Token.Op(">="); i += 2 }
                c == '<' -> { tokens += Token.Op("<"); i++ }
                c == '>' -> { tokens += Token.Op(">"); i++ }
                c == '~' && (i + 1) < input.length && input[i + 1] == '=' -> { tokens += Token.Op("~="); i += 2 }
                c.isLetter() || c == '_' -> {
                    val start = i
                    while (i < input.length && (input[i].isLetterOrDigit() || input[i] == '_' || input[i] == '.')) i++
                    val word = input.substring(start, i)
                    tokens += when (word.uppercase()) {
                        "AND" -> Token.And
                        "OR" -> Token.Or
                        "NOT" -> Token.Not
                        "IN" -> Token.In
                        "BETWEEN" -> Token.Between
                        "TRUE" -> Token.BoolLit(true)
                        "FALSE" -> Token.BoolLit(false)
                        "NULL" -> Token.NullLit
                        else -> Token.Ident(word)
                    }
                }
                else -> error("Unexpected char '$c' at $i")
            }
        }
        return tokens
    }

    // ── Parser ───────────────────────────────────────────────────────────────

    private class Parser(val tokens: List<Token>) {
        var pos = 0

        fun atEnd() = pos >= tokens.size
        private fun peek(): Token? = tokens.getOrNull(pos)
        private fun consume(): Token = tokens[pos++]
        private fun match(t: Token): Boolean = if (peek() == t) { pos++; true } else false

        fun parseExpr(): Node = parseOr()

        private fun parseOr(): Node {
            var left = parseAnd()
            while (match(Token.Or)) left = Node.Or(left, parseAnd())
            return left
        }

        private fun parseAnd(): Node {
            var left = parseNot()
            while (match(Token.And)) left = Node.And(left, parseNot())
            return left
        }

        private fun parseNot(): Node {
            if (match(Token.Not)) return Node.Not(parseNot())
            return parseCmp()
        }

        private fun parseCmp(): Node {
            if (match(Token.LParen)) {
                val inner = parseExpr()
                require(match(Token.RParen)) { "Expected ')'" }
                return inner
            }
            val left = parseValue()
            return when (val nxt = peek()) {
                Token.In -> {
                    consume()
                    require(match(Token.LBracket)) { "Expected '['" }
                    val items = mutableListOf<Node.Value>()
                    while (peek() != Token.RBracket) {
                        items += parseValue()
                        if (peek() == Token.Comma) consume()
                    }
                    consume() // ]
                    Node.In(left, items)
                }
                Token.Between -> {
                    consume()
                    val lo = parseValue()
                    require(match(Token.And)) { "Expected 'AND' after BETWEEN low" }
                    val hi = parseValue()
                    Node.Between(left, lo, hi)
                }
                is Token.Op -> {
                    consume()
                    val right = parseValue()
                    Node.Cmp(left, nxt.op, right)
                }
                else -> Node.Truthy(left)
            }
        }

        private fun parseValue(): Node.Value {
            return when (val t = consume()) {
                is Token.NumLit -> Node.Value.Num(t.value)
                is Token.StringLit -> Node.Value.Str(t.value)
                is Token.BoolLit -> Node.Value.Bool(t.value)
                Token.NullLit -> Node.Value.Null
                is Token.Ident -> {
                    var args: List<Node.Value>? = null
                    if (match(Token.LParen)) {
                        val arr = mutableListOf<Node.Value>()
                        while (peek() != Token.RParen) {
                            arr += parseValue()
                            if (peek() == Token.Comma) consume()
                        }
                        consume()
                        args = arr
                    }
                    Node.Value.Accessor(t.name, args)
                }
                else -> error("Unexpected token: $t")
            }
        }
    }

    // ── AST ──────────────────────────────────────────────────────────────────

    private sealed class Token {
        object LParen : Token(); object RParen : Token()
        object LBracket : Token(); object RBracket : Token()
        object Comma : Token()
        object And : Token(); object Or : Token(); object Not : Token()
        object In : Token(); object Between : Token()
        object NullLit : Token()
        data class Op(val op: String) : Token()
        data class NumLit(val value: Double) : Token()
        data class StringLit(val value: String) : Token()
        data class BoolLit(val value: Boolean) : Token()
        data class Ident(val name: String) : Token()
    }

    private sealed class Node {
        abstract fun eval(ctx: Context): Boolean

        class Or(val l: Node, val r: Node) : Node() { override fun eval(ctx: Context) = l.eval(ctx) || r.eval(ctx) }
        class And(val l: Node, val r: Node) : Node() { override fun eval(ctx: Context) = l.eval(ctx) && r.eval(ctx) }
        class Not(val inner: Node) : Node() { override fun eval(ctx: Context) = !inner.eval(ctx) }

        class Cmp(val l: Value, val op: String, val r: Value) : Node() {
            override fun eval(ctx: Context): Boolean {
                val lv = l.resolve(ctx)
                val rv = r.resolve(ctx)
                return when (op) {
                    "==" -> equalsValue(lv, rv)
                    "!=" -> !equalsValue(lv, rv)
                    "<" -> toDouble(lv) < toDouble(rv)
                    "<=" -> toDouble(lv) <= toDouble(rv)
                    ">" -> toDouble(lv) > toDouble(rv)
                    ">=" -> toDouble(lv) >= toDouble(rv)
                    "~=" -> (lv?.toString() ?: "").contains((rv?.toString() ?: ""), ignoreCase = true)
                    else -> false
                }
            }
        }

        class In(val v: Value, val list: List<Value>) : Node() {
            override fun eval(ctx: Context): Boolean {
                val vv = v.resolve(ctx)
                return list.any { equalsValue(vv, it.resolve(ctx)) }
            }
        }

        class Between(val v: Value, val lo: Value, val hi: Value) : Node() {
            override fun eval(ctx: Context): Boolean {
                val vv = toDouble(v.resolve(ctx))
                return vv >= toDouble(lo.resolve(ctx)) && vv <= toDouble(hi.resolve(ctx))
            }
        }

        class Truthy(val v: Value) : Node() {
            override fun eval(ctx: Context): Boolean = when (val r = v.resolve(ctx)) {
                is Boolean -> r
                is Number -> r.toDouble() != 0.0
                null -> false
                is String -> r.isNotEmpty()
                else -> true
            }
        }

        sealed class Value {
            abstract fun resolve(ctx: Context): Any?
            data class Num(val value: Double) : Value() { override fun resolve(ctx: Context) = value }
            data class Str(val value: String) : Value() { override fun resolve(ctx: Context) = value }
            data class Bool(val value: Boolean) : Value() { override fun resolve(ctx: Context) = value }
            object Null : Value() { override fun resolve(ctx: Context): Any? = null }
            data class Accessor(val path: String, val args: List<Value>?) : Value() {
                override fun resolve(ctx: Context): Any? = ctx.resolve(path, args)
            }
        }
    }

    // ── Context (resolve accessors) ──────────────────────────────────────────

    private class Context(val world: WorldSnapshot?, val event: TriggerEvent?) {
        fun resolve(path: String, args: List<Node.Value>?): Any? {
            val parts = path.split('.')
            return when (parts.firstOrNull()) {
                "battery" -> resolveBattery(parts.drop(1))
                "screen" -> resolveScreen(parts.drop(1))
                "network" -> resolveNetwork(parts.drop(1))
                "time" -> resolveTime(parts.drop(1))
                "event" -> resolveEvent(parts.drop(1))
                "location" -> resolveLocation(parts.drop(1), args)
                else -> null
            }
        }

        private fun resolveBattery(tail: List<String>): Any? = when (tail.firstOrNull()) {
            "level" -> world?.batteryLevel
            "charging" -> world?.batteryCharging
            else -> null
        }

        private fun resolveScreen(tail: List<String>): Any? = when (tail.firstOrNull()) {
            "on" -> world?.screenOn
            else -> null
        }

        private fun resolveNetwork(tail: List<String>): Any? = when (tail.firstOrNull()) {
            "connected" -> world?.networkOnline
            else -> null
        }

        private fun resolveTime(tail: List<String>): Any? {
            val now = java.time.ZonedDateTime.now()
            return when (tail.firstOrNull()) {
                "hour" -> now.hour
                "minute" -> now.minute
                "weekday" -> now.dayOfWeek.value
                else -> null
            }
        }

        private fun resolveEvent(tail: List<String>): Any? = when (tail.firstOrNull()) {
            "text" -> event?.metadata?.get("text")
            "title" -> event?.metadata?.get("title")
            "package" -> event?.metadata?.get("package")
            "value" -> event?.metadata?.get("value")?.toDoubleOrNull()
            else -> null
        }

        private fun resolveLocation(tail: List<String>, args: List<Node.Value>?): Any? {
            // Phase 6 minimal — placeholder always-far. WorldObserver Phase 6 polish
            // bisa lookup memory.places + FusedLocation last known.
            return when (tail.firstOrNull()) {
                "distance_from" -> Double.POSITIVE_INFINITY
                else -> null
            }
        }
    }

    companion object {
        private fun equalsValue(a: Any?, b: Any?): Boolean = when {
            a is Number && b is Number -> a.toDouble() == b.toDouble()
            else -> a == b
        }

        private fun toDouble(v: Any?): Double = when (v) {
            is Number -> v.toDouble()
            is Boolean -> if (v) 1.0 else 0.0
            is String -> v.toDoubleOrNull() ?: 0.0
            null -> 0.0
            else -> 0.0
        }
    }
}
