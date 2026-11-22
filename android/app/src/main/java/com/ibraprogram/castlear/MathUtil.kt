package com.ibraprogram.castlear

import kotlin.math.sqrt

/** متجه ثلاثي بسيط للمنطق (إحداثيات الساحة: مربع واحد = 1 وحدة). */
data class V3(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f) {
    operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = V3(x * s, y * s, z * s)
    operator fun div(s: Float) = V3(x / s, y / s, z / s)
    fun dot(o: V3) = x * o.x + y * o.y + z * o.z
    fun len() = sqrt(x * x + y * y + z * z)
    fun lenSq() = x * x + y * y + z * z
    fun flat() = V3(x, 0f, z)
    fun norm(): V3 { val l = len(); return if (l > 1e-6f) this / l else V3() }

    companion object {
        val ZERO = V3()
        fun lerp(a: V3, b: V3, t: Float) = a + (b - a) * t
    }
}

data class Cell(val x: Int, val y: Int) {
    operator fun plus(o: Cell) = Cell(x + o.x, y + o.y)
}

fun clamp01(v: Float) = v.coerceIn(0f, 1f)
fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

/** زاوية الاتجاه (Yaw بالدرجات) بحيث +Z = الأمام. */
fun yawOf(dir: V3): Float = Math.toDegrees(kotlin.math.atan2(dir.x.toDouble(), dir.z.toDouble())).toFloat()

fun moveTowardsAngle(cur: Float, target: Float, maxDelta: Float): Float {
    var d = ((target - cur) % 360f + 540f) % 360f - 180f
    if (d > maxDelta) d = maxDelta
    if (d < -maxDelta) d = -maxDelta
    return cur + d
}

/** شعاع (Ray) بإحداثيات الساحة. */
data class Ray(val origin: V3, val dir: V3) {
    fun at(t: Float) = origin + dir * t

    /** تقاطع مع السطح y = h. */
    fun hitPlaneY(h: Float = 0f): V3? {
        if (kotlin.math.abs(dir.y) < 1e-6f) return null
        val t = (h - origin.y) / dir.y
        return if (t > 0f) at(t) else null
    }

    /** مسافة التقاطع مع كرة، أو null. */
    fun hitSphere(c: V3, r: Float): Float? {
        val oc = origin - c
        val b = oc.dot(dir)
        val cc = oc.dot(oc) - r * r
        val disc = b * b - cc
        if (disc < 0f) return null
        val t = -b - sqrt(disc)
        return if (t > 0f) t else null
    }
}
