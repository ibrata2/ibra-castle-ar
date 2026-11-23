package com.ibraprogram.castlear

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.ar.core.Frame
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * يتعرف على الإيد من صورة كاميرا ARCore باستخدام MediaPipe (يشتغل بدون إنترنت).
 * النتيجة: 21 نقطة بإحداثيات صورة الكاميرا (IMAGE_PIXELS) جاهزة للتحويل لإحداثيات الشاشة.
 */
class HandTracker(context: Context) {
    private val exec = Executors.newSingleThreadExecutor()
    @Volatile private var landmarker: HandLandmarker? = null
    @Volatile var error: String? = null
        private set
    private val busy = AtomicBoolean(false)

    @Volatile private var latest: FloatArray? = null
    @Volatile var latestTime = 0L
        private set
    @Volatile var seq = 0
        private set

    private var yBuf = ByteArray(0)
    private var uBuf = ByteArray(0)
    private var vBuf = ByteArray(0)
    private var pixels = IntArray(0)

    val ready get() = landmarker != null

    init {
        val app = context.applicationContext
        exec.execute {
            try {
                val opts = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build())
                    .setRunningMode(RunningMode.IMAGE)
                    .setNumHands(1)
                    .setMinHandDetectionConfidence(0.5f)
                    .setMinHandPresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .build()
                landmarker = HandLandmarker.createFromOptions(app, opts)
            } catch (t: Throwable) {
                Log.e("IbraCastle", "hand init", t)
                error = t.message ?: "hand init failed"
            }
        }
    }

    /** يُستدعى من خيط الرسم كل فريم — ياخذ صورة إذا المعالج فاضي. */
    fun submit(frame: Frame) {
        val lm = landmarker ?: return
        if (!busy.compareAndSet(false, true)) return
        val img = try { frame.acquireCameraImage() } catch (e: Exception) { busy.set(false); return }
        val w: Int; val h: Int
        val yRow: Int; val uvRow: Int; val uvPix: Int
        try {
            w = img.width; h = img.height
            val yp = img.planes[0]; val up = img.planes[1]; val vp = img.planes[2]
            yRow = yp.rowStride; uvRow = up.rowStride; uvPix = up.pixelStride
            yBuf = copy(yp.buffer, yBuf); uBuf = copy(up.buffer, uBuf); vBuf = copy(vp.buffer, vBuf)
        } catch (e: Exception) {
            busy.set(false); return
        } finally {
            img.close()
        }

        exec.execute {
            try {
                // نصغّر للنص وندوّر للوضع العمودي (الإيد تكون واقفة = تعرّف أدق)
                val pw = h / 2
                val ph = w / 2
                if (pixels.size != pw * ph) pixels = IntArray(pw * ph)
                for (py in 0 until ph) {
                    val nx = py * 2
                    for (px in 0 until pw) {
                        val ny = (h - 1) - px * 2
                        val yv = (yBuf[ny * yRow + nx].toInt() and 255) - 16
                        val ui = (ny / 2) * uvRow + (nx / 2) * uvPix
                        val u = (uBuf[ui].toInt() and 255) - 128
                        val v = (vBuf[ui].toInt() and 255) - 128
                        val y1 = 1.164f * yv.coerceAtLeast(0)
                        val r = (y1 + 1.596f * v).toInt().coerceIn(0, 255)
                        val g = (y1 - 0.392f * u - 0.813f * v).toInt().coerceIn(0, 255)
                        val b = (y1 + 2.017f * u).toInt().coerceIn(0, 255)
                        pixels[py * pw + px] = (255 shl 24) or (r shl 16) or (g shl 8) or b
                    }
                }
                val bmp = Bitmap.createBitmap(pixels, pw, ph, Bitmap.Config.ARGB_8888)
                val res = lm.detect(BitmapImageBuilder(bmp).build())
                val hands = res.landmarks()
                if (hands.isNotEmpty()) {
                    val pts = hands[0]
                    val out = FloatArray(42)
                    for (i in 0 until 21) {
                        // من الصورة العمودية المصغّرة ← بكسلات صورة الكاميرا الأصلية
                        val px = pts[i].x() * pw * 2f
                        val py = pts[i].y() * ph * 2f
                        out[i * 2] = py
                        out[i * 2 + 1] = (h - 1) - px
                    }
                    latest = out
                    latestTime = System.currentTimeMillis()
                    seq++
                }
                bmp.recycle()
            } catch (t: Throwable) {
                Log.e("IbraCastle", "hand detect", t)
            } finally {
                busy.set(false)
            }
        }
    }

    fun latestPoints(): FloatArray? = latest

    fun close() {
        exec.execute { try { landmarker?.close() } catch (_: Throwable) {} }
        exec.shutdown()
    }

    private fun copy(src: java.nio.ByteBuffer, dst: ByteArray): ByteArray {
        val n = src.remaining()
        val out = if (dst.size >= n) dst else ByteArray(n)
        src.get(out, 0, n)
        return out
    }
}
