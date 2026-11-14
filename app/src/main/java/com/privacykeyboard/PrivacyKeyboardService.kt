package com.privacykeyboard

import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.inputmethodservice.InputMethodService

/** Keyboard Engine. Текст идёт только в InputConnection; ничего не пишется на диск и в лог. */
class PrivacyKeyboardService : InputMethodService() {
    private lateinit var prefs: Prefs
    private lateinit var view: GlassKeyboardView
    private val composer = HangulComposer()
    private var lang = "en"
    private var shift = false
    private var sym = false
    private var afterWord = false   // последний введённый символ — буква/цифра (для «двойной пробел → точка»)

    override fun onCreateInputView(): View {
        prefs = Prefs(this)
        view = GlassKeyboardView(this, prefs) { handle(it) }
        view.refresh(); render()
        return view
    }

    override fun onEvaluateFullscreenMode() = false

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        composer.reset(); sym = false; afterWord = false
        val langs = prefs.langs()
        if (lang !in langs) lang = langs[0]
        val caps = currentInputConnection?.getCursorCapsMode(info.inputType) ?: 0
        shift = lang != "ko" && info.inputType != 0 && caps != 0
        view.refresh(); render()
    }

    override fun onFinishInput() { composer.reset(); super.onFinishInput() }

    override fun onUpdateSelection(os: Int, oe: Int, ns: Int, ne: Int, cs: Int, ce: Int) {
        super.onUpdateSelection(os, oe, ns, ne, cs, ce)
        if (cs == -1 && composer.active) composer.reset()   // курсор ушёл — слог уже зафиксирован системой
    }

    private fun render() { view.update(Layouts.get(lang, sym), lang, shift && !sym) }

    private fun handle(a: String) {
        when (a) {
            "SHIFT" -> { shift = !shift; render() }
            "SYM" -> { sym = true; render() }
            "ABC" -> { sym = false; render() }
            "LANG", "LANG_NEXT" -> switchLang(1)
            "LANG_PREV" -> switchLang(-1)
            "DEL" -> del()
            "ENTER" -> enter()
            "SPACE" -> text(" ")
            else -> text(a)
        }
    }

    private fun switchLang(dir: Int) {
        endComposition()
        val l = prefs.langs()
        val i = l.indexOf(lang).let { if (it < 0) 0 else it }
        lang = l[(i + dir + l.size) % l.size]
        shift = false; sym = false; render()
    }

    private fun endComposition() {
        if (composer.active) { composer.reset(); currentInputConnection?.finishComposingText() }
    }

    private fun text(s: String) {
        val ic = currentInputConnection ?: return
        if (lang == "ko" && !sym && s.length == 1 && composer.isJamo(s[0])) {
            val o = composer.input(s[0])
            if (o.commit.isNotEmpty()) ic.commitText(o.commit, 1)
            if (o.comp.isNotEmpty()) ic.setComposingText(o.comp, 1)
            afterWord = true
        } else {
            endComposition()
            if (s == " " && afterWord && !sym && prefs.bool("dotspace", true) && lastWasSpace) {
                ic.deleteSurroundingText(1, 0); ic.commitText(". ", 1)
                lastWasSpace = false; afterWord = false
                if (lang != "ko") { shift = true; render() }
                return
            }
            ic.commitText(s, 1)
            lastWasSpace = s == " "
            afterWord = s.length == 1 && s[0].isLetterOrDigit() || s.length > 1
        }
        if (s != " ") lastWasSpace = false
        if (shift && s != " ") { shift = false; render() }
    }
    private var lastWasSpace = false

    private fun del() {
        val ic = currentInputConnection ?: return
        lastWasSpace = false; afterWord = false
        if (composer.active) {
            val o = composer.backspace()
            if (o.comp.isEmpty()) ic.commitText("", 1) else ic.setComposingText(o.comp, 1)
        } else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    private fun enter() {
        endComposition(); lastWasSpace = false; afterWord = false
        val o = currentInputEditorInfo?.imeOptions ?: 0
        val act = o and EditorInfo.IME_MASK_ACTION
        if (act != EditorInfo.IME_ACTION_NONE && act != EditorInfo.IME_ACTION_UNSPECIFIED && (o and EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0)
            currentInputConnection?.performEditorAction(act)
        else sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
    }
}
