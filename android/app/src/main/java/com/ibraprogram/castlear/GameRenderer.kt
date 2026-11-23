package com.ibraprogram.castlear

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import com.google.ar.core.Anchor
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import java.util.concurrent.ConcurrentLinkedQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

sealed class Cmd {
    data class Tap(val x: Float, val y: Float) : Cmd()
    data class GhostBegin(val type: BType) : Cmd()
    data class GhostMove(val x: Float, val y: Float) : Cmd()
    data class GhostEnd(val x: Float, val y: Float, val place: Boolean) : Cmd()
    object Place : Cmd()
    object StartWave : Cmd()
    object Restart : Cmd()
}

/**
 * حلقة الرسم: الكاميرا ← الأسطح/العلامة ← الساحة واللعبة.
 * كل أوامر اللمس تنحط بطابور وتتنفذ هنا على خيط OpenGL (بدون تضارب).
 */
class GameRenderer(private val rotationProvider: () -> Int) : GLSurfaceView.Renderer, GestureEngine.Listener {
    @Volatile var session: Session? = null
    val commands = ConcurrentLinkedQueue<Cmd>()

    private val bg = BackgroundRenderer()
    private val planes = PlaneRenderer()
    private val shader = MeshShader()
    var game: Game? = null
        private set

    @Volatile var boardScale = 0.06f
    @Volatile var hasPose = false
    @Volatile var tracking = false
    @Volatile var placed = false
    @Volatile var errorText: String? = null

    // ── التحكم بالإيد ──
    var hand: HandTracker? = null
    private val gestures = GestureEngine()
    @Volatile var handMode = true
    @Volatile var selectedType = BType.TOWER
    @Volatile var handSeen = false
    /** نقاط الإيد بإحداثيات الشاشة للرسم فوق الكاميرا. */
    @Volatile var handViewPts: FloatArray? = null
    @Volatile var handPinching = false
    @Volatile var handEvent: String? = null
    @Volatile var handEventSeq = 0
    private var lastHandSeq = -1
    private val cursorNode = Node()
    private var cursorVisible = false
    private var cursorPinch = false

    private var viewW = 1
    private var viewH = 1
    private var geometryChanged = true
    private var lastNanos = 0L

    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val vp = FloatArray(16)
    private val invVp = FloatArray(16)
    private val board = FloatArray(16)
    private val boardInv = FloatArray(16)
    private val tmp = FloatArray(16)
    private val identity = FloatArray(16).also { Matrix.setIdentityM(it, 0) }

    private var anchor: Anchor? = null
    private var reticlePose: Pose? = null
    private val reticleNode = Node()
    private val previewRoot = Node()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        try {
            bg.init(); planes.init(); shader.init()
            if (game == null) {
                Models.build(13, 13)
                game = Game()
                reticleNode.mesh = Models.reticle
                previewRoot.add(Node(Models.castle))
                cursorNode.mesh = Models.cursor
            } else {
                Mesh.invalidateAll() // سياق جديد: نعيد رفع المجسمات ونحافظ على اللعبة
            }
            session?.setCameraTextureName(bg.textureId)
        } catch (t: Throwable) {
            Log.e("IbraCastle", "init", t)
            errorText = t.message
        }
        geometryChanged = true
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewW = width; viewH = height
        GLES20.glViewport(0, 0, width, height)
        geometryChanged = true
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val s = session ?: return
        val g = game ?: return

        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0.016f else ((now - lastNanos) / 1e9f)
        lastNanos = now

        try {
            if (geometryChanged) {
                s.setDisplayGeometry(rotationProvider(), viewW, viewH)
                geometryChanged = false
            }
            s.setCameraTextureName(bg.textureId)
            val frame = s.update()
            val cam = frame.camera
            bg.draw(frame)

            tracking = cam.trackingState == TrackingState.TRACKING
            if (!tracking) { drainCommandsWithoutTracking(); return }

            cam.getProjectionMatrix(proj, 0, 0.02f, 40f)
            cam.getViewMatrix(view, 0)
            Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
            Matrix.invertM(invVp, 0, vp, 0)

            if (!placed) {
                updateReticle(frame)
                planes.draw(s.getAllTrackables(Plane::class.java), vp)
                drawReticle()
                processCommands(frame, g)
                return
            }

            val a = anchor
            if (a != null && a.trackingState == TrackingState.TRACKING) {
                a.pose.toMatrix(tmp, 0)
                Matrix.scaleM(board, 0, tmp, 0, boardScale, boardScale, boardScale)
                Matrix.invertM(boardInv, 0, board, 0)
            }

            processCommands(frame, g)
            updateHand(frame, g)

            val camPos = cam.pose.translation
            val camLocal = transformPoint(boardInv, camPos[0], camPos[1], camPos[2])
            g.update(dt, camLocal)

            drawNode(g.root, board)
            if (cursorVisible && handMode) {
                val pulse = if (cursorPinch) 0.8f else 1f + 0.08f * kotlin.math.sin(System.nanoTime() / 1.5e8).toFloat()
                cursorNode.setScale(pulse)
                cursorNode.tint = if (cursorPinch) floatArrayOf(0.6f, 1.4f, 0.6f) else floatArrayOf(1f, 1f, 1f)
                drawNode(cursorNode, board)
            }
        } catch (t: Throwable) {
            Log.e("IbraCastle", "frame", t)
        }
    }

    private fun drainCommandsWithoutTracking() {
        // نحتفظ بأوامر البناء (السحب) حتى ترجع المتابعة، ونرمي اللمسات القديمة
        val keep = ArrayList<Cmd>()
        while (true) {
            val c = commands.poll() ?: break
            if (c is Cmd.StartWave || c is Cmd.Restart || c is Cmd.GhostBegin || c is Cmd.GhostEnd) keep.add(c)
        }
        commands.addAll(keep)
    }

    private fun updateReticle(frame: Frame) {
        hasPose = false
        for (hit in frame.hitTest(viewW / 2f, viewH / 2f)) {
            val t = hit.trackable
            if (t is Plane && t.type == Plane.Type.HORIZONTAL_UPWARD_FACING && t.isPoseInPolygon(hit.hitPose)) {
                reticlePose = hit.hitPose
                hasPose = true
                break
            }
        }
    }

    private fun drawReticle() {
        val p = reticlePose ?: return
        if (!hasPose) return
        val tr = p.translation
        Matrix.setIdentityM(tmp, 0)
        Matrix.translateM(tmp, 0, tr[0], tr[1], tr[2])
        val m = FloatArray(16)
        Matrix.scaleM(m, 0, tmp, 0, boardScale, boardScale, boardScale)
        reticleNode.scale = V3(13f, 1f, 13f)
        val pulse = 0.85f + 0.15f * kotlin.math.sin(System.nanoTime() / 2.5e8).toFloat()
        previewRoot.setScale(pulse)
        previewRoot.alpha = 0.75f
        drawNode(reticleNode, m)
        drawNode(previewRoot, m)
    }

    private fun drawNode(n: Node, parent: FloatArray) {
        shader.begin()
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        n.draw(shader, parent, vp, 0)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)
        n.draw(shader, parent, vp, 1)
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
        shader.end()
    }

    private fun processCommands(frame: Frame, g: Game) {
        while (true) {
            val c = commands.poll() ?: break
            when (c) {
                is Cmd.Place -> place(g)
                is Cmd.Tap -> {
                    if (!placed) place(g)
                    else screenRay(c.x, c.y)?.let { g.tap(it) }
                }
                is Cmd.GhostBegin -> if (placed) g.beginGhost(c.type)
                is Cmd.GhostMove -> if (placed) g.moveGhost(groundPoint(c.x, c.y))
                is Cmd.GhostEnd -> if (placed) { g.moveGhost(groundPoint(c.x, c.y)); g.endGhost(c.place) }
                is Cmd.StartWave -> g.startWave()
                is Cmd.Restart -> g.restart()
            }
        }
    }

    private fun place(g: Game) {
        val s = session ?: return
        val p = reticlePose ?: return
        if (!hasPose || placed) return
        val tr = p.translation
        anchor = s.createAnchor(Pose(tr, floatArrayOf(0f, 0f, 0f, 1f)))
        anchor!!.pose.toMatrix(tmp, 0)
        Matrix.scaleM(board, 0, tmp, 0, boardScale, boardScale, boardScale)
        Matrix.invertM(boardInv, 0, board, 0)
        placed = true
        g.onPlaced()
    }

    /** شعاع من نقطة الشاشة بإحداثيات الساحة. */
    private fun screenRay(x: Float, y: Float): Ray? {
        val nx = 2f * x / viewW - 1f
        val ny = 1f - 2f * y / viewH
        val near = unproject(nx, ny, -1f)
        val far = unproject(nx, ny, 1f)
        val o = transformPoint(boardInv, near.x, near.y, near.z)
        val f = transformPoint(boardInv, far.x, far.y, far.z)
        val d = (f - o).norm()
        if (d.lenSq() < 1e-8f) return null
        return Ray(o, d)
    }

    // ───────────── الإيد ─────────────
    private fun updateHand(frame: Frame, g: Game) {
        val h = hand
        if (!handMode || h == null || !h.ready) {
            if (handSeen) { gestures.lost(this); handSeen = false; handViewPts = null; cursorVisible = false }
            return
        }
        h.submit(frame)
        val now = System.currentTimeMillis()
        if (now - h.latestTime > 400) {
            if (handSeen) { gestures.lost(this); handSeen = false; handViewPts = null; cursorVisible = false }
            return
        }
        if (h.seq == lastHandSeq) return
        lastHandSeq = h.seq
        val img = h.latestPoints() ?: return
        val view = FloatArray(42)
        frame.transformCoordinates2d(Coordinates2d.IMAGE_PIXELS, img, Coordinates2d.VIEW, view)
        handViewPts = view
        handSeen = true
        gestures.update(view, h.latestTime, viewH.toFloat(), this)
        handPinching = gestures.pinching
    }

    private fun handGround(x: Float, y: Float): V3? = screenRay(x, y)?.hitPlaneY(0f)

    override fun onCursor(x: Float, y: Float, pinching: Boolean) {
        val p = handGround(x, y)
        val g = game
        cursorVisible = p != null && g != null && g.inBounds(g.cellOf(p))
        if (p != null) cursorNode.pos = V3(p.x, 0.05f, p.z)
        cursorPinch = pinching
    }

    override fun onHit(x: Float, y: Float) {
        val g = game ?: return
        val r = screenRay(x, y) ?: return
        if (g.handHit(r)) { handEvent = "hit"; handEventSeq++ }
    }

    override fun onSelect(type: BType) {
        selectedType = type
        handEvent = "select"; handEventSeq++
    }

    override fun onPinchStart(x: Float, y: Float) {
        val g = game ?: return
        g.beginGhost(selectedType)
        g.moveGhost(handGround(x, y))
    }

    override fun onPinchMove(x: Float, y: Float) { game?.moveGhost(handGround(x, y)) }

    override fun onPinchEnd(x: Float, y: Float, place: Boolean) {
        val g = game ?: return
        if (place) g.moveGhost(handGround(x, y))
        g.endGhost(place)
    }

    override fun onThumbsUp() {
        val g = game ?: return
        if (g.state == GameState.BUILDING) { g.startWave(); handEvent = "wave"; handEventSeq++ }
    }

    private fun groundPoint(x: Float, y: Float): V3? {
        // نرفع الشبح شوية فوق الإصبع حتى يبين
        return screenRay(x, y - viewH * 0.06f)?.hitPlaneY(0f)
    }

    private fun unproject(nx: Float, ny: Float, nz: Float): V3 {
        val inV = floatArrayOf(nx, ny, nz, 1f)
        val out = FloatArray(4)
        Matrix.multiplyMV(out, 0, invVp, 0, inV, 0)
        val w = if (kotlin.math.abs(out[3]) < 1e-9f) 1f else out[3]
        return V3(out[0] / w, out[1] / w, out[2] / w)
    }

    private fun transformPoint(m: FloatArray, x: Float, y: Float, z: Float): V3 {
        val out = FloatArray(4)
        Matrix.multiplyMV(out, 0, m, 0, floatArrayOf(x, y, z, 1f), 0)
        return V3(out[0], out[1], out[2])
    }

    @Suppress("unused")
    private fun unusedIdentity() = identity
}
