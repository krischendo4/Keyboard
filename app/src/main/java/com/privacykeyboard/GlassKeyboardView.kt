package com.privacykeyboard

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * Theme Engine + рендер. «Стекло» нарисовано имитацией: полупрозрачная заливка, блик-градиент,
 * световая кайма, лёгкая тень. Настоящий blur фона приложения из окна IME недоступен.
 */
class GlassKeyboardView(ctx: Context, private val prefs: Prefs, private val emit: (String) -> Unit) : View(ctx) {
    private class Cell(val key: Key, val r: RectF)

    private var rows: List<List<Key>> = emptyList()
    private var lang = "en"
    private var shift = false
    private val cells = ArrayList<Cell>()
    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = v * d
    private val h = Handler(Looper.getMainLooper())

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x26000000 }
    private val gloss = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1f) }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val panel = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tmp = RectF()
    private val rect = RectF()

    private var radius = 0f; private var gap = 0f; private var anim = 0f; private var refl = 0f
    private var keyA = 0f; private var keyAF = 0f; private var tint = 0xFFFFFF; private var textC = Color.WHITE
    private var edgeB = 0; private var panelTop = 0; private var panelBot = 0; private var keyH = 0f
    private var darkText = false

    private var pressT = 0f
    private var animKey: Key? = null
    private val va = ValueAnimator().apply { addUpdateListener { pressT = it.animatedValue as Float; invalidate() } }

    private fun sa(c: Int, f: Float) = (c and 0xFFFFFF) or ((Color.alpha(c) * f).toInt() shl 24)
    private fun c(v: Long) = v.toInt()

    fun refresh() {
        val op = prefs.int("opacity", 60) / 100f
        val frost = prefs.int("blur", 60) / 100f
        refl = prefs.int("reflect", 60) / 100f
        radius = dp(prefs.int("radius", 12).toFloat()); gap = dp(prefs.int("gap", 5).toFloat())
        anim = prefs.int("anim", 70) / 100f
        val base: Float
        darkText = false
        edgeB = Color.argb(30, 255, 255, 255)
        when (prefs.int("theme", 0)) {
            1 -> { panelTop = c(0xE6F6F8FC); panelBot = c(0xF0D9DEE8); tint = 0xFFFFFF; base = .9f; textC = c(0xFF14141A); edgeB = c(0x33000000); darkText = true }
            2 -> { panelTop = c(0x55FFFFFF); panelBot = c(0x40FFFFFF); tint = 0xFFFFFF; base = .28f; textC = Color.WHITE }
            3 -> { panelTop = c(0xE60C1838); panelBot = c(0xF0040A1E); tint = 0x9CC2FF; base = .26f; textC = c(0xFFE8F0FF) }
            4 -> {
                val hue = prefs.int("hue", 210).toFloat()
                tint = Color.HSVToColor(floatArrayOf(hue, .45f, 1f)) and 0xFFFFFF
                panelTop = Color.HSVToColor(0xE6, floatArrayOf(hue, .55f, .28f))
                panelBot = Color.HSVToColor(0xF0, floatArrayOf(hue, .6f, .14f))
                base = .24f; textC = Color.WHITE
            }
            else -> { panelTop = c(0xE6232330); panelBot = c(0xF00C0C14); tint = 0xFFFFFF; base = .22f; textC = Color.WHITE }
        }
        val pf = 0.45f + 0.55f * frost
        panelTop = sa(panelTop, pf); panelBot = sa(panelBot, pf)
        keyA = (base * (0.4f + 1.2f * op)).coerceIn(0f, 1f); keyAF = keyA * 0.6f
        txt.setShadowLayer(if (darkText) 0f else dp(2f), 0f, dp(1f), 0x66000000)
        requestLayout(); layoutKeys(); invalidate()
    }

    fun update(rows: List<List<Key>>, lang: String, shift: Boolean) {
        this.rows = rows; this.lang = lang; this.shift = shift
        requestLayout(); layoutKeys(); invalidate()
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val scale = prefs.int("height", 100) / 100f * (if (land) .75f else 1f)
        val hh = (rows.size.coerceAtLeast(1) * dp(50f) * scale + dp(12f)).toInt()
        setMeasuredDimension(MeasureSpec.getSize(wSpec), hh)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { layoutKeys() }

    private fun layoutKeys() {
        cells.clear()
        if (width == 0 || rows.isEmpty()) return
        val pad = dp(6f)
        val rowH = (height - 2 * pad) / rows.size
        rows.forEachIndexed { ri, row ->
            val tw = row.sumOf { it.w.toDouble() }.toFloat()
            val unit = (width - 2 * pad - gap * (row.size - 1)).coerceAtLeast(1f) / tw
            var x = pad
            row.forEach { k ->
                val w = k.w * unit
                cells.add(Cell(k, RectF(x, pad + ri * rowH, x + w, pad + (ri + 1) * rowH)))
                x += w + gap
            }
        }
        keyH = (rowH - gap).coerceAtLeast(1f)
        panel.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), panelTop, panelBot, Shader.TileMode.CLAMP)
        gloss.shader = LinearGradient(0f, 0f, 0f, keyH,
            intArrayOf(Color.argb((255 * .45f * refl).toInt(), 255, 255, 255), 0, 0), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        edge.shader = LinearGradient(0f, 0f, 0f, keyH, Color.argb((255 * .8f * refl).toInt(), 255, 255, 255), edgeB, Shader.TileMode.CLAMP)
    }

    override fun onDraw(c: Canvas) {
        c.drawRoundRect(0f, 0f, width.toFloat(), height + dp(28f), dp(22f), dp(22f), panel)
        for (cell in cells) {
            val k = cell.key
            if (k.a.isEmpty()) continue
            val t = if (k === animKey) pressT else 0f
            rect.set(cell.r.left, cell.r.top + gap / 2, cell.r.right, cell.r.bottom - gap / 2)
            c.save()
            val s = 1f - 0.07f * anim * t
            c.scale(s, s, rect.centerX(), rect.centerY())
            val off = dp(1.2f) * (1f - t)
            tmp.set(rect.left, rect.top + off, rect.right, rect.bottom + off)
            c.drawRoundRect(tmp, radius, radius, shadow)
            val a = ((if (k.fn) keyAF else keyA) + 0.2f * t * refl).coerceIn(0f, 1f)
            fill.color = ((a * 255).toInt() shl 24) or tint
            c.drawRoundRect(rect, radius, radius, fill)
            c.save(); c.translate(rect.left, rect.top)
            tmp.set(0f, 0f, rect.width(), rect.height())
            c.drawRoundRect(tmp, radius, radius, gloss)
            tmp.inset(dp(.5f), dp(.5f))
            c.drawRoundRect(tmp, radius, radius, edge)
            c.restore()
            val isSpace = k.a == "SPACE"
            val label = when {
                k.a == "SHIFT" -> if (shift) "⇪" else "⇧"
                k.fn -> k.label
                shift -> Layouts.shifted(lang, k.label)
                else -> k.label
            }
            txt.textSize = if (k.fn) dp(if (isSpace) 14f else 17f) else dp(22f)
            txt.color = textC
            txt.alpha = if (isSpace) 170 else 255
            val fm = txt.fontMetrics
            c.drawText(label, rect.centerX(), rect.centerY() - (fm.ascent + fm.descent) / 2, txt)
            c.restore()
        }
    }

    // ---- ввод ----
    private var down: Cell? = null
    private var downX = 0f
    private var fired = false
    private var swiped = false
    private val repeat = object : Runnable { override fun run() { emit("DEL"); h.postDelayed(this, 45) } }
    private val longRun = Runnable {
        val k = down?.key ?: return@Runnable
        if (k.a == "DEL") { fired = true; emit("DEL"); h.postDelayed(repeat, 45) }
        else if (k.alt != null) { fired = true; emit(out(k.alt)) }
    }

    private fun out(t: String) = if (shift) Layouts.shifted(lang, t) else t
    private fun fire(k: Key) = emit(if (k.fn) k.a else out(k.a))

    private fun hit(x: Float, y: Float): Cell? {
        val yy = y.coerceIn(dp(6f), height - dp(6f) - 1f)
        return cells.firstOrNull { it.key.a.isNotEmpty() && yy >= it.r.top && yy < it.r.bottom && x >= it.r.left - gap / 2 && x <= it.r.right + gap / 2 }
    }

    private fun animTo(v: Float) {
        va.cancel(); va.setFloatValues(pressT, v)
        va.duration = if (v > 0f) 70L else 180L
        va.start()
    }

    private fun stopCallbacks() { h.removeCallbacks(longRun); h.removeCallbacks(repeat) }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val c = hit(ev.x, ev.y) ?: return true
                down = c; downX = ev.x; fired = false; swiped = false
                animKey = c.key; animTo(1f)
                if (prefs.bool("haptic", true)) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                h.postDelayed(longRun, 380)
            }
            MotionEvent.ACTION_MOVE -> {
                val c = down ?: return true
                if (c.key.a == "SPACE") {
                    val dx = ev.x - downX
                    if (!swiped && abs(dx) > dp(80f)) {
                        swiped = true; stopCallbacks(); animTo(0f)
                        emit(if (dx > 0) "LANG_NEXT" else "LANG_PREV")
                    }
                } else if (!fired && hit(ev.x, ev.y) !== c) { stopCallbacks(); down = null; animTo(0f) }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                stopCallbacks()
                val c = down
                if (ev.actionMasked == MotionEvent.ACTION_UP && c != null && !fired && !swiped && hit(ev.x, ev.y) === c) fire(c.key)
                down = null; animTo(0f)
            }
        }
        return true
    }

    override fun onDetachedFromWindow() { stopCallbacks(); va.cancel(); super.onDetachedFromWindow() }
}
