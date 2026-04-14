package com.chibiclaw.memory.vector

/**
 * Minimal WordPiece tokenizer compatible with the all-MiniLM-L6-v2
 * vocabulary. Implements the same splitting rules as HuggingFace's
 * `BertTokenizer(do_lower_case=True)`:
 *
 *   1. Strip control chars, normalise whitespace.
 *   2. Lowercase.
 *   3. Split on whitespace and punctuation.
 *   4. For each token, greedy longest-match against the vocab,
 *      prefixing continuation sub-tokens with "##".
 *   5. Wrap with [CLS] ... [SEP] and pad/truncate to maxLen.
 *
 * This is ~200 lines of pure JVM code with no native deps — slower
 * than HF's Rust tokenizer but plenty fast for the ≤128-token
 * sentences we send to MiniLM, and completely offline.
 */
class BertTokenizer(vocabLines: List<String>) {

    private val vocab: Map<String, Long>
    private val unkId: Long
    private val clsId: Long
    private val sepId: Long
    private val padId: Long

    init {
        val map = HashMap<String, Long>(vocabLines.size)
        for ((idx, line) in vocabLines.withIndex()) {
            val token = line.trim()
            if (token.isNotEmpty()) map[token] = idx.toLong()
        }
        vocab = map
        unkId = map["[UNK]"] ?: 100L
        clsId = map["[CLS]"] ?: 101L
        sepId = map["[SEP]"] ?: 102L
        padId = map["[PAD]"] ?: 0L
    }

    data class Encoded(
        val inputIds: LongArray,
        val attentionMask: LongArray,
        val tokenTypeIds: LongArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Encoded) return false
            return inputIds.contentEquals(other.inputIds) &&
                   attentionMask.contentEquals(other.attentionMask) &&
                   tokenTypeIds.contentEquals(other.tokenTypeIds)
        }

        override fun hashCode(): Int =
            inputIds.contentHashCode() * 31 + attentionMask.contentHashCode()
    }

    fun encode(text: String, maxLen: Int): Encoded {
        val words = basicTokenize(text)
        val pieces = mutableListOf<Long>()
        pieces.add(clsId)
        outer@ for (word in words) {
            val wp = wordpiece(word)
            for (id in wp) {
                if (pieces.size >= maxLen - 1) break@outer
                pieces.add(id)
            }
        }
        pieces.add(sepId)
        val pad = maxLen - pieces.size
        val ids = LongArray(maxLen)
        val mask = LongArray(maxLen)
        val types = LongArray(maxLen) // all zeros — single-segment input
        for (i in pieces.indices) {
            ids[i] = pieces[i]
            mask[i] = 1L
        }
        if (pad > 0) {
            for (i in pieces.size until maxLen) {
                ids[i] = padId
                mask[i] = 0L
            }
        }
        return Encoded(ids, mask, types)
    }

    // ---- stage 1: basic tokenizer (whitespace + punctuation) ----

    private fun basicTokenize(text: String): List<String> {
        val cleaned = cleanText(text).lowercase()
        val out = mutableListOf<String>()
        val buf = StringBuilder()
        for (ch in cleaned) {
            when {
                ch.isWhitespace() -> {
                    if (buf.isNotEmpty()) { out.add(buf.toString()); buf.clear() }
                }
                isPunct(ch) -> {
                    if (buf.isNotEmpty()) { out.add(buf.toString()); buf.clear() }
                    out.add(ch.toString())
                }
                else -> buf.append(ch)
            }
        }
        if (buf.isNotEmpty()) out.add(buf.toString())
        return out
    }

    private fun cleanText(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            val code = ch.code
            if (code == 0 || code == 0xfffd || isControl(ch)) continue
            if (ch.isWhitespace()) sb.append(' ') else sb.append(ch)
        }
        return sb.toString()
    }

    private fun isControl(ch: Char): Boolean {
        if (ch == '\t' || ch == '\n' || ch == '\r') return false
        val cat = Character.getType(ch)
        return cat == Character.CONTROL.toInt() ||
               cat == Character.FORMAT.toInt() ||
               cat == Character.PRIVATE_USE.toInt() ||
               cat == Character.SURROGATE.toInt() ||
               cat == Character.UNASSIGNED.toInt()
    }

    private fun isPunct(ch: Char): Boolean {
        val code = ch.code
        // ASCII punctuation
        if ((code in 33..47) || (code in 58..64) ||
            (code in 91..96) || (code in 123..126)) return true
        val cat = Character.getType(ch)
        return cat in Character.DASH_PUNCTUATION.toInt()..Character.OTHER_PUNCTUATION.toInt()
    }

    // ---- stage 2: wordpiece (greedy longest match) ----

    private fun wordpiece(word: String): List<Long> {
        if (word.isEmpty()) return emptyList()
        if (word.length > MAX_INPUT_CHARS_PER_WORD) return listOf(unkId)
        val out = mutableListOf<Long>()
        var start = 0
        while (start < word.length) {
            var end = word.length
            var current: Long? = null
            while (end > start) {
                val sub = if (start == 0) word.substring(start, end) else "##${word.substring(start, end)}"
                val id = vocab[sub]
                if (id != null) {
                    current = id
                    break
                }
                end--
            }
            if (current == null) {
                return listOf(unkId)
            }
            out.add(current)
            start = end
        }
        return out
    }

    companion object {
        private const val MAX_INPUT_CHARS_PER_WORD = 100
    }
}
