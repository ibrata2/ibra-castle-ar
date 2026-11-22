package com.ibraprogram.castlear

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class Axis { X, Y, Z }

/**
 * يبني مجسمات Low-Poly بتظليل مسطّح (Flat shading) من أشكال أساسية:
 * صندوق، أسطوانة، مخروط، كرة. كل رأس فيه: موقع + اتجاه + لون.
 */
class MeshBuilder {
    private val data = ArrayList<Float>(4096)

    private fun rgb(c: Int) = floatArrayOf(((c shr 16) and 255) / 255f, ((c shr 8) and 255) / 255f, (c and 255) / 255f)

    fun tri(a: V3, b: V3, c: V3, color: Int) {
        val n = cross(b - a, c - a).norm()
        val col = rgb(color)
        for (p in arrayOf(a, b, c)) {
            data.add(p.x); data.add(p.y); data.add(p.z)
            data.add(n.x); data.add(n.y); data.add(n.z)
            data.add(col[0]); data.add(col[1]); data.add(col[2])
        }
    }

    fun quad(a: V3, b: V3, c: V3, d: V3, color: Int) { tri(a, b, c, color); tri(a, c, d, color) }

    /** صندوق: المركز والحجم. */
    fun box(cx: Float, cy: Float, cz: Float, sx: Float, sy: Float, sz: Float, color: Int, topColor: Int = color) {
        val x0 = cx - sx / 2; val x1 = cx + sx / 2
        val y0 = cy - sy / 2; val y1 = cy + sy / 2
        val z0 = cz - sz / 2; val z1 = cz + sz / 2
        quad(V3(x0, y1, z0), V3(x0, y1, z1), V3(x1, y1, z1), V3(x1, y1, z0), topColor) // top
        quad(V3(x0, y0, z0), V3(x1, y0, z0), V3(x1, y0, z1), V3(x0, y0, z1), color)    // bottom
        quad(V3(x0, y0, z1), V3(x1, y0, z1), V3(x1, y1, z1), V3(x0, y1, z1), color)    // front +z
        quad(V3(x1, y0, z0), V3(x0, y0, z0), V3(x0, y1, z0), V3(x1, y1, z0), color)    // back -z
        quad(V3(x1, y0, z1), V3(x1, y0, z0), V3(x1, y1, z0), V3(x1, y1, z1), color)    // right +x
        quad(V3(x0, y0, z0), V3(x0, y0, z1), V3(x0, y1, z1), V3(x0, y1, z0), color)    // left -x
    }

    private fun map(axis: Axis, base: V3, u: Float, v: Float, h: Float): V3 = when (axis) {
        Axis.Y -> V3(base.x + u, base.y + h, base.z + v)
        Axis.Z -> V3(base.x + u, base.y + v, base.z + h)
        Axis.X -> V3(base.x + h, base.y + v, base.z + u)
    }

    /** أسطوانة: base = مركز القاعدة، تمتد بطول h على المحور. */
    fun cylinder(base: V3, r: Float, h: Float, color: Int, seg: Int = 8, axis: Axis = Axis.Y, topColor: Int = color, r2: Float = r) {
        for (i in 0 until seg) {
            val a0 = (i.toFloat() / seg) * 2f * Math.PI.toFloat()
            val a1 = ((i + 1).toFloat() / seg) * 2f * Math.PI.toFloat()
            val b0 = map(axis, base, cos(a0) * r, sin(a0) * r, 0f)
            val b1 = map(axis, base, cos(a1) * r, sin(a1) * r, 0f)
            val t0 = map(axis, base, cos(a0) * r2, sin(a0) * r2, h)
            val t1 = map(axis, base, cos(a1) * r2, sin(a1) * r2, h)
            val ct = map(axis, base, 0f, 0f, h)
            val cb = map(axis, base, 0f, 0f, 0f)
            if (axis != Axis.Z) {
                quad(b0, t0, t1, b1, color)
                tri(ct, t1, t0, topColor)
                tri(cb, b0, b1, color)
            } else {
                quad(b0, b1, t1, t0, color)
                tri(ct, t0, t1, topColor)
                tri(cb, b1, b0, color)
            }
        }
    }

    fun cone(base: V3, r: Float, h: Float, color: Int, seg: Int = 8, axis: Axis = Axis.Y) {
        for (i in 0 until seg) {
            val a0 = (i.toFloat() / seg) * 2f * Math.PI.toFloat()
            val a1 = ((i + 1).toFloat() / seg) * 2f * Math.PI.toFloat()
            val b0 = map(axis, base, cos(a0) * r, sin(a0) * r, 0f)
            val b1 = map(axis, base, cos(a1) * r, sin(a1) * r, 0f)
            val tip = map(axis, base, 0f, 0f, h)
            val cb = map(axis, base, 0f, 0f, 0f)
            if (axis != Axis.Z) { tri(b0, tip, b1, color); tri(cb, b0, b1, color) }
            else { tri(b0, b1, tip, color); tri(cb, b1, b0, color) }
        }
    }

    fun sphere(c: V3, r: Float, color: Int, seg: Int = 8, rings: Int = 5, sy: Float = 1f) {
        for (j in 0 until rings) {
            val p0 = Math.PI.toFloat() * j / rings - Math.PI.toFloat() / 2
            val p1 = Math.PI.toFloat() * (j + 1) / rings - Math.PI.toFloat() / 2
            for (i in 0 until seg) {
                val a0 = 2f * Math.PI.toFloat() * i / seg
                val a1 = 2f * Math.PI.toFloat() * (i + 1) / seg
                fun pt(p: Float, a: Float) = V3(c.x + r * cos(p) * cos(a), c.y + r * sy * sin(p), c.z + r * cos(p) * sin(a))
                val v00 = pt(p0, a0); val v01 = pt(p0, a1); val v10 = pt(p1, a0); val v11 = pt(p1, a1)
                if (j > 0) tri(v00, v10, v01, color)
                if (j < rings - 1) tri(v01, v10, v11, color)
            }
        }
    }

    fun build(): Mesh {
        val arr = FloatArray(data.size)
        for (i in data.indices) arr[i] = data[i]
        return Mesh(arr)
    }

    private fun cross(a: V3, b: V3) = V3(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x)
}

/** مجسم مرفوع على كرت الشاشة (VBO). */
class Mesh(private val vertices: FloatArray) {
    val vertexCount = vertices.size / 9
    private var vbo = 0

    init { all.add(this) }

    companion object {
        private val all = ArrayList<Mesh>()
        /** بعد ضياع سياق OpenGL (رجوع للتطبيق) نعيد رفع المجسمات. */
        fun invalidateAll() { for (m in all) m.vbo = 0 }
    }

    fun upload() {
        if (vbo != 0) return
        val buf: FloatBuffer = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(vertices).position(0)
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        vbo = ids[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertices.size * 4, buf, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    fun draw(s: MeshShader) {
        if (vbo == 0) upload()
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glVertexAttribPointer(s.aPos, 3, GLES20.GL_FLOAT, false, 36, 0)
        GLES20.glVertexAttribPointer(s.aNormal, 3, GLES20.GL_FLOAT, false, 36, 12)
        GLES20.glVertexAttribPointer(s.aColor, 3, GLES20.GL_FLOAT, false, 36, 24)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }
}

/** شيدر كرتوني (Toon): إضاءة بطبقات + وميض أبيض + شفافية. */
class MeshShader {
    var program = 0; var aPos = 0; var aNormal = 0; var aColor = 0
    var uMvp = 0; var uModel = 0; var uTint = 0; var uFlash = 0; var uAlpha = 0

    fun init() {
        val vs = """
            uniform mat4 uMvp; uniform mat4 uModel;
            attribute vec3 aPos; attribute vec3 aNormal; attribute vec3 aColor;
            varying vec3 vN; varying vec3 vC;
            void main(){
                vN = normalize(mat3(uModel) * aNormal);
                vC = aColor;
                gl_Position = uMvp * vec4(aPos, 1.0);
            }""".trimIndent()
        val fs = """
            precision mediump float;
            uniform vec3 uTint; uniform float uFlash; uniform float uAlpha;
            varying vec3 vN; varying vec3 vC;
            void main(){
                vec3 L = normalize(vec3(0.4, 1.0, 0.3));
                float d = max(dot(normalize(vN), L), 0.0);
                float band = d > 0.65 ? 1.0 : (d > 0.3 ? 0.78 : 0.58);
                float sky = 0.5 + 0.5 * normalize(vN).y;
                vec3 c = vC * uTint * (0.42 + 0.62 * band + 0.08 * sky);
                c = mix(c, vec3(1.0), uFlash);
                gl_FragColor = vec4(c, uAlpha);
            }""".trimIndent()
        program = GlUtil.program(vs, fs)
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        aNormal = GLES20.glGetAttribLocation(program, "aNormal")
        aColor = GLES20.glGetAttribLocation(program, "aColor")
        uMvp = GLES20.glGetUniformLocation(program, "uMvp")
        uModel = GLES20.glGetUniformLocation(program, "uModel")
        uTint = GLES20.glGetUniformLocation(program, "uTint")
        uFlash = GLES20.glGetUniformLocation(program, "uFlash")
        uAlpha = GLES20.glGetUniformLocation(program, "uAlpha")
    }

    fun begin() {
        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glEnableVertexAttribArray(aNormal)
        GLES20.glEnableVertexAttribArray(aColor)
    }

    fun end() {
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aNormal)
        GLES20.glDisableVertexAttribArray(aColor)
    }
}

object GlUtil {
    fun shader(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) throw RuntimeException("Shader error: " + GLES20.glGetShaderInfoLog(s))
        return s
    }

    fun program(vs: String, fs: String): Int {
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, shader(GLES20.GL_VERTEX_SHADER, vs))
        GLES20.glAttachShader(p, shader(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) throw RuntimeException("Link error: " + GLES20.glGetProgramInfoLog(p))
        return p
    }
}

@Suppress("unused")
private fun lenOf(x: Float, y: Float) = sqrt(x * x + y * y)
