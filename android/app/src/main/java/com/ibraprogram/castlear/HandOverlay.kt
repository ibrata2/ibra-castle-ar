package com.ibraprogram.castlear

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/** يرسم هيكل الإيد (ذهبي) فوق الكاميرا حتى تعرف إن اللعبة شايفة إيدك. */
class HandOverlay(context: Context, private val source: () -> FloatArray?, private val pinching: () -> Boolean) : View(context) {
    private val bones = intArrayOf(
        0, 1, 1, 2, 2, 3, 3, 4,
        0, 5, 5, 6, 6, 7, 7, 8,
        5, 9, 9, 10, 10, 11, 11, 12,
        9, 13, 13, 14, 14, 15, 15, 16,
        13, 17, 0, 17, 17, 18, 18, 19, 19, 20
    )
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCD4AF37.toInt(); strokeWidth = 6f; strokeCap = Paint.Cap.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val tip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD4AF37.toInt() }
    private val pinch = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF51CF66.toInt() }

    init { setWillNotDraw(false); isClickable = false; isFocusable = false }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val p = source()
        if (p != null) {
            var i = 0
            while (i < bones.size) {
                val a = bones[i]; val b = bones[i + 1]
                c.drawLine(p[a * 2], p[a * 2 + 1], p[b * 2], p[b * 2 + 1], line)
                i += 2
            }
            for (k in 0 until 21) c.drawCircle(p[k * 2], p[k * 2 + 1], 6f, dot)
            val tipPaint = if (pinching()) pinch else tip
            c.drawCircle(p[8 * 2], p[8 * 2 + 1], 16f, tipPaint)
            c.drawCircle(p[4 * 2], p[4 * 2 + 1], 12f, tipPaint)
        }
        postInvalidateOnAnimation()
    }
}
