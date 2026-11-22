package com.ibraprogram.castlear

import android.opengl.GLES20
import android.opengl.Matrix

/**
 * شجرة مشهد صغيرة: كل عقدة لها موقع/دوران/حجم ومجسم اختياري وأبناء.
 * الدوران بالدرجات: Yaw (حول Y) ثم Pitch (حول X) ثم Roll (حول Z).
 */
class Node(var mesh: Mesh? = null) {
    var pos = V3.ZERO
    var yaw = 0f
    var pitch = 0f
    var roll = 0f
    var scale = V3(1f, 1f, 1f)
    var alpha = 1f
    var flash = 0f
    var tint = floatArrayOf(1f, 1f, 1f)
    var visible = true
    /** يواجه الكاميرا دائماً (لأشرطة الصحة). */
    var billboard = false

    val children = ArrayList<Node>()
    var parent: Node? = null
        private set

    fun add(n: Node): Node { n.parent?.children?.remove(n); n.parent = this; children.add(n); return n }
    fun remove(n: Node) { if (children.remove(n)) n.parent = null }
    fun removeFromParent() { parent?.remove(this) }

    fun setScale(s: Float) { scale = V3(s, s, s) }

    private val local = FloatArray(16)
    private val world = FloatArray(16)
    private val tmp = FloatArray(16)

    private fun buildLocal() {
        Matrix.setIdentityM(local, 0)
        Matrix.translateM(local, 0, pos.x, pos.y, pos.z)
        val y = if (billboard) billboardYaw else yaw
        if (y != 0f) Matrix.rotateM(local, 0, y, 0f, 1f, 0f)
        if (pitch != 0f) Matrix.rotateM(local, 0, pitch, 1f, 0f, 0f)
        if (roll != 0f) Matrix.rotateM(local, 0, roll, 0f, 0f, 1f)
        Matrix.scaleM(local, 0, scale.x, scale.y, scale.z)
    }

    /**
     * pass 0 = المعتم، pass 1 = الشفاف.
     */
    fun draw(s: MeshShader, parentWorld: FloatArray, viewProj: FloatArray, pass: Int, inFlash: Float = 0f, inAlpha: Float = 1f) {
        if (!visible) return
        buildLocal()
        Matrix.multiplyMM(world, 0, parentWorld, 0, local, 0)
        val f = maxOf(flash, inFlash)
        val a = alpha * inAlpha
        val m = mesh
        if (m != null && a > 0.01f) {
            val transparent = a < 0.999f
            if ((pass == 1) == transparent) {
                Matrix.multiplyMM(tmp, 0, viewProj, 0, world, 0)
                GLES20.glUniformMatrix4fv(s.uMvp, 1, false, tmp, 0)
                GLES20.glUniformMatrix4fv(s.uModel, 1, false, world, 0)
                GLES20.glUniform3f(s.uTint, tint[0], tint[1], tint[2])
                GLES20.glUniform1f(s.uFlash, f)
                GLES20.glUniform1f(s.uAlpha, a)
                m.draw(s)
            }
        }
        for (i in children.indices) {
            if (i < children.size) children[i].draw(s, world, viewProj, pass, f, a)
        }
    }

    /** موقع العقدة بإحداثيات أعلى عقدة (الساحة). */
    fun posInRoot(): V3 {
        var p = pos
        var n = parent
        var cur: Node = this
        while (n != null && n.parent != null) {
            // تحويل تقريبي (حجم + Yaw) — يكفي لنقاط الإطلاق
            val r = Math.toRadians(n.yaw.toDouble())
            val c = kotlin.math.cos(r).toFloat(); val sn = kotlin.math.sin(r).toFloat()
            val sp = V3(p.x * n.scale.x, p.y * n.scale.y, p.z * n.scale.z)
            p = n.pos + V3(sp.x * c + sp.z * sn, sp.y, -sp.x * sn + sp.z * c)
            cur = n
            n = n.parent
        }
        return p
    }

    companion object {
        /** اتجاه الكاميرا بالنسبة للساحة — يتحدث كل فريم. */
        var billboardYaw = 0f
    }
}
