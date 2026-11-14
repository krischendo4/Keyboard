package com.privacykeyboard

/** Hangul Engine: сборка слогов из совместимых джамо. Состояние живёт только в памяти текущего слога. */
class HangulComposer {
    data class Out(val commit: String, val comp: String)

    private var cho: Char? = null
    private var jung: Char? = null
    private var jong: Char? = null
    val active get() = cho != null || jung != null

    fun reset() { cho = null; jung = null; jong = null }
    fun isJamo(ch: Char) = CHO.indexOf(ch) >= 0 || JUNG.indexOf(ch) >= 0

    private fun comp(): String {
        val c = cho; val v = jung; val t = jong
        if (c != null && v != null) {
            val code = 0xAC00 + (CHO.indexOf(c) * 21 + JUNG.indexOf(v)) * 28 + (if (t == null) 0 else JONG.indexOf(t.toString()))
            return code.toChar().toString()
        }
        return (c ?: v)?.toString() ?: ""
    }

    fun input(ch: Char): Out {
        if (JUNG.indexOf(ch) >= 0) {
            val t = jong
            if (t != null) {                       // конечная согласная переходит в следующий слог
                val sp = JSPLIT[t]
                jong = sp?.first
                val done = comp()
                cho = sp?.second ?: t; jung = ch; jong = null
                return Out(done, comp())
            }
            val v = jung
            if (v != null) {
                val m = VCOMB["$v$ch"]
                if (m != null) { jung = m; return Out("", comp()) }
                val done = comp(); cho = null; jung = ch; jong = null
                return Out(done, comp())
            }
            jung = ch
            return Out("", comp())
        }
        // согласная
        if (jung == null) {
            if (cho == null) { cho = ch; return Out("", comp()) }
            val done = comp(); cho = ch
            return Out(done, comp())
        }
        if (cho == null) { val done = comp(); cho = ch; jung = null; jong = null; return Out(done, comp()) }
        val t = jong
        if (t == null) {
            if (JONG.indexOf(ch.toString()) > 0) { jong = ch; return Out("", comp()) }
        } else {
            val m = JCOMB["$t$ch"]
            if (m != null) { jong = m; return Out("", comp()) }
        }
        val done = comp(); cho = ch; jung = null; jong = null
        return Out(done, comp())
    }

    /** Удаляет последнюю введённую джамо. */
    fun backspace(): Out {
        val t = jong; val v = jung
        when {
            t != null -> jong = JSPLIT[t]?.first
            v != null -> jung = VSPLIT[v]
            else -> cho = null
        }
        return Out("", comp())
    }

    companion object {
        const val CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
        const val JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
        val JONG = listOf("", "ㄱ", "ㄲ", "ㄳ", "ㄴ", "ㄵ", "ㄶ", "ㄷ", "ㄹ", "ㄺ", "ㄻ", "ㄼ", "ㄽ", "ㄾ", "ㄿ", "ㅀ",
            "ㅁ", "ㅂ", "ㅄ", "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ")
        private val VCOMB = mapOf("ㅗㅏ" to 'ㅘ', "ㅗㅐ" to 'ㅙ', "ㅗㅣ" to 'ㅚ', "ㅜㅓ" to 'ㅝ', "ㅜㅔ" to 'ㅞ', "ㅜㅣ" to 'ㅟ', "ㅡㅣ" to 'ㅢ')
        private val VSPLIT = VCOMB.entries.associate { it.value to it.key[0] }
        private val JCOMB = mapOf("ㄱㅅ" to 'ㄳ', "ㄴㅈ" to 'ㄵ', "ㄴㅎ" to 'ㄶ', "ㄹㄱ" to 'ㄺ', "ㄹㅁ" to 'ㄻ', "ㄹㅂ" to 'ㄼ',
            "ㄹㅅ" to 'ㄽ', "ㄹㅌ" to 'ㄾ', "ㄹㅍ" to 'ㄿ', "ㄹㅎ" to 'ㅀ', "ㅂㅅ" to 'ㅄ')
        private val JSPLIT = JCOMB.entries.associate { it.value to Pair(it.key[0], it.key[1]) }
    }
}
