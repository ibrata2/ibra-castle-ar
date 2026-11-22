package com.ibraprogram.castlear

import android.opengl.GLES20
import android.opengl.Matrix
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** يرسم الأسطح المكتشفة كطبقة شفافة ذهبية حتى يعرف اللاعب وين يكدر يحط القلعة. */
class PlaneRenderer {
    private var program = 0
    private var aPos = 0
    private var uMvp = 0
    private var uColor = 0
    private var buf: FloatBuffer = alloc(256)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)

    fun init() {
        val vs = """
            uniform mat4 uMvp; attribute vec3 aPos; varying float vEdge;
            void main(){ gl_Position = uMvp * vec4(aPos.x, 0.0, aPos.y, 1.0); vEdge = aPos.z; }""".trimIndent()
        val fs = """
            precision mediump float; uniform vec4 uColor; varying float vEdge;
            void main(){ gl_FragColor = vec4(uColor.rgb, uColor.a * mix(1.0, 0.0, vEdge)); }""".trimIndent()
        program = GlUtil.program(vs, fs)
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        uMvp = GLES20.glGetUniformLocation(program, "uMvp")
        uColor = GLES20.glGetUniformLocation(program, "uColor")
    }

    fun draw(planes: Collection<Plane>, viewProj: FloatArray) {
        GLES20.glUseProgram(program)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)
        GLES20.glUniform4f(uColor, 1f, 0.82f, 0.35f, 0.35f)
        GLES20.glEnableVertexAttribArray(aPos)

        for (p in planes) {
            if (p.trackingState != TrackingState.TRACKING || p.subsumedBy != null) continue
            if (p.type != Plane.Type.HORIZONTAL_UPWARD_FACING) continue
            val poly = p.polygon ?: continue
            val n = poly.limit() / 2
            if (n < 3) continue

            // مروحة مثلثات: المركز (شفاف أقل) + الحافة (تتلاشى)
            val floats = (n + 2) * 3
            if (buf.capacity() < floats) buf = alloc(floats * 2)
            buf.clear()
            buf.put(0f); buf.put(0f); buf.put(0f)
            for (i in 0..n) {
                val k = (i % n) * 2
                buf.put(poly.get(k)); buf.put(poly.get(k + 1)); buf.put(0.6f)
            }
            buf.position(0)

            p.centerPose.toMatrix(model, 0)
            Matrix.multiplyMM(mvp, 0, viewProj, 0, model, 0)
            GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 0, buf)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, n + 2)
        }

        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun alloc(floats: Int): FloatBuffer =
        ByteBuffer.allocateDirect(floats * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
}
