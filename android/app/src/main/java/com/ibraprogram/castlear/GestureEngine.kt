package com.ibraprogram.castlear

import kotlin.math.hypot

/**
 * يحوّل نقاط الإيد (21 نقطة بإحداثيات الشاشة) إلى حركات اللعبة:
 * ☝️ ✌️ 🤟 = اختيار جدار/برج/فخ  •  🤏 كمشة = مسك وتحريك وإفلات للبناء
 * 👋 حركة سريعة = ضربة  •  👍 = بدء الموجة
 */
class GestureEngine {
    interface Listener {
        fun onCursor(x: Float, y: Float, pinching: Boolean)
        fun onHit(x: Float, y: Float)
        fun onSelect(type: BType)
        fun onPinchStart(x: Float, y: Float)
        fun onPinchMove(x: Float, y: Float)
        fun onPinchEnd(x: Float, y: Float, place: Boolean)
        fun onThumbsUp()
    }

    var pinching = false
        private set
    var fingers = 0
        private set

    private var lastX = 0f
    private var lastY = 0f
    private var lastT = 0L
    private var speed = 0f
    private var lastHitT = 0L
    private var candidate = 0
    private var candidateSince = 0L
    private var selected = 0
    private var thumbSince = 0L
    private var thumbFired = false
    private var pinchX = 0f
    private var pinchY = 0f

    private fun d(p: FloatArray, a: Int, b: Int) = hypot(p[a * 2] - p[b * 2], p[a * 2 + 1] - p[b * 2 + 1])

    /** p = 42 قيمة (x,y لكل نقطة) بإحداثيات الشاشة، tMs بالملي ثانية، viewH ارتفاع الشاشة. */
    fun update(p: FloatArray, tMs: Long, viewH: Float, l: Listener) {
        val hs = d(p, 0, 9)
        if (hs < 8f) return

        fun ext(tip: Int, pip: Int) = d(p, 0, tip) > d(p, 0, pip) * 1.12f
        fingers = listOf(ext(8, 6), ext(12, 10), ext(16, 14), ext(20, 18)).count { it }
        val thumbExt = d(p, 4, 17) > d(p, 3, 17) * 1.08f

        // ── الكمشة (إبهام + سبابة) مع هامش حتى ما ترجف ──
        val pinchD = d(p, 4, 8) / hs
        val mx = (p[8] + p[16]) / 2f
        val my = (p[9] + p[17]) / 2f
        if (!pinching && pinchD < 0.3f) {
            pinching = true
            pinchX = mx; pinchY = my
            l.onPinchStart(mx, my)
        } else if (pinching && pinchD > 0.55f) {
            pinching = false
            l.onPinchEnd(pinchX, pinchY, true)
        } else if (pinching) {
            // تنعيم الحركة
            pinchX += (mx - pinchX) * 0.5f
            pinchY += (my - pinchY) * 0.5f
            l.onPinchMove(pinchX, pinchY)
        }

        // ── سرعة رأس السبابة ──
        val tx = p[16]; val ty = p[17]
        if (lastT != 0L) {
            val dt = ((tMs - lastT).coerceAtLeast(1)) / 1000f
            val v = hypot(tx - lastX, ty - lastY) / dt
            speed = speed * 0.4f + v * 0.6f
        }
        lastX = tx; lastY = ty; lastT = tMs

        if (pinching) l.onCursor(pinchX, pinchY, true) else l.onCursor(tx, ty, false)

        // ── الضربة: حركة سريعة ──
        if (!pinching && speed > viewH * 1.5f && tMs - lastHitT > 260) {
            lastHitT = tMs
            l.onHit(tx, ty)
        }

        // ── اختيار نوع الدفاع بعدد الأصابع (والإيد ثابتة) ──
        val calm = speed < viewH * 0.6f
        if (!pinching && calm && fingers in 1..3 && pinchD > 0.45f) {
            if (fingers != candidate) { candidate = fingers; candidateSince = tMs }
            else if (tMs - candidateSince > 450 && selected != fingers) {
                selected = fingers
                l.onSelect(when (fingers) { 1 -> BType.WALL; 2 -> BType.TOWER; else -> BType.SPIKE })
            }
        } else candidate = 0

        // ── 👍 إبهام لفوق ──
        val thumbsUp = fingers == 0 && thumbExt && (p[1] - p[9]) > hs * 0.8f && !pinching
        if (thumbsUp) {
            if (thumbSince == 0L) thumbSince = tMs
            if (!thumbFired && tMs - thumbSince > 700) { thumbFired = true; l.onThumbsUp() }
        } else { thumbSince = 0L; thumbFired = false }
    }

    /** الإيد طلعت من الكاميرا. */
    fun lost(l: Listener) {
        if (pinching) { pinching = false; l.onPinchEnd(pinchX, pinchY, false) }
        lastT = 0L; speed = 0f; candidate = 0; thumbSince = 0L; thumbFired = false
    }
}
