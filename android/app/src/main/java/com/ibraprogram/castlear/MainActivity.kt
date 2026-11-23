package com.ibraprogram.castlear

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.UnavailableException

/**
 * IBRA CASTLE AR — الشاشة الرئيسية: كاميرا الواقع المعزز + الواجهة العربية.
 * برمجة وتطوير ابراهيم الخزرجي
 */
class MainActivity : AppCompatActivity() {

    companion object {
        const val GOLD = 0xFFD4AF37.toInt()
        const val GOLD_SOFT = 0xFFC9A84C.toInt()
        const val PANEL = 0xD9101014.toInt()
        const val SITE = "https://ibraprogram.netlify.app/"
        const val TELEGRAM = "https://t.me/ibraprogram"
        const val TIKTOK = "https://www.tiktok.com/@ibraprogram"
    }

    private lateinit var surface: GLSurfaceView
    private lateinit var renderer: GameRenderer
    private var session: Session? = null
    private var installRequested = false

    // الواجهة
    private lateinit var hud: LinearLayout
    private lateinit var goldText: TextView
    private lateinit var waveText: TextView
    private lateinit var enemiesText: TextView
    private lateinit var hpBar: ProgressBar
    private lateinit var hint: TextView
    private lateinit var toast: TextView
    private lateinit var placePanel: LinearLayout
    private lateinit var placeButton: Button
    private lateinit var buildBar: LinearLayout
    private lateinit var waveButton: Button
    private lateinit var endPanel: LinearLayout
    private lateinit var endTitle: TextView
    private lateinit var endSub: TextView
    private val buildButtons = HashMap<BType, View>()
    private lateinit var handButton: TextView
    private lateinit var handHelp: TextView
    private lateinit var handStatus: TextView
    private var handTracker: HandTracker? = null
    private var lastHandEventSeq = 0

    private val ui = Handler(Looper.getMainLooper())
    private var lastToastSeq = 0
    private var lastHitSeq = 0
    private var toastHideAt = 0L
    private val surfaceLoc = IntArray(2)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        renderer = GameRenderer { displayRotation() }
        surface = GLSurfaceView(this).apply {
            preserveEGLContextOnPause = true
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
        handTracker = HandTracker(this)
        renderer.hand = handTracker
        setContentView(buildUi())
        ui.post(poll)
    }

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int = (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation

    // ───────────── دورة حياة ARCore ─────────────
    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
            return
        }
        if (session == null) {
            try {
                when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> { installRequested = true; return }
                    else -> {}
                }
                val s = Session(this)
                val cfg = Config(s).apply {
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    focusMode = Config.FocusMode.AUTO
                    lightEstimationMode = Config.LightEstimationMode.DISABLED
                }
                s.configure(cfg)
                session = s
                renderer.session = s
            } catch (e: UnavailableException) {
                showFatal("هذا الجهاز ما يدعم الواقع المعزز (ARCore)\n${e.javaClass.simpleName}")
                return
            } catch (e: Exception) {
                showFatal("تعذر تشغيل الواقع المعزز: ${e.message}")
                return
            }
        }
        try {
            session?.resume()
        } catch (e: Exception) {
            showFatal("الكاميرا مشغولة أو غير متاحة")
            session = null
            return
        }
        surface.onResume()
    }

    override fun onPause() {
        super.onPause()
        surface.onPause()
        session?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        ui.removeCallbacksAndMessages(null)
        handTracker?.close()
        session?.close()
        session = null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED)
            showFatal("اللعبة تحتاج إذن الكاميرا حتى تشتغل بالواقع المعزز")
    }

    private fun showFatal(msg: String) {
        hint.text = msg
        hint.visibility = View.VISIBLE
        placePanel.visibility = View.GONE
    }

    // ───────────── تحديث الواجهة ─────────────
    private val poll = object : Runnable {
        override fun run() {
            refresh()
            ui.postDelayed(this, 100)
        }
    }

    private fun refresh() {
        val g = renderer.game ?: return
        val state = g.state
        val playing = state == GameState.BUILDING || state == GameState.WAVE

        if (!renderer.placed) {
            hud.visibility = View.GONE
            buildBar.visibility = View.GONE
            waveButton.visibility = View.GONE
            if (session != null) {
                placePanel.visibility = View.VISIBLE
                hint.visibility = View.VISIBLE
                hint.text = when {
                    !renderer.tracking -> "حرّك الموبايل ببطء حتى تشتغل الكاميرا 📱"
                    !renderer.hasPose -> "وجّه الكاميرا على طاولة أو أرض مستوية…"
                    else -> "المس الشاشة أو اضغط «ضع القلعة» 🏰"
                }
                placeButton.isEnabled = renderer.hasPose
                placeButton.alpha = if (renderer.hasPose) 1f else 0.45f
            }
        } else {
            placePanel.visibility = View.GONE
            hud.visibility = View.VISIBLE
            hint.visibility = if (!renderer.tracking) View.VISIBLE else View.GONE
            if (!renderer.tracking) hint.text = "المتابعة ضاعت — رجّع الكاميرا على الساحة"
            buildBar.visibility = if (playing) View.VISIBLE else View.GONE
            waveButton.visibility = if (state == GameState.BUILDING) View.VISIBLE else View.GONE
            waveButton.text = "ابدأ الموجة ${g.wave + 1} ⚔"
        }

        goldText.text = "💰 ${g.gold}"
        waveText.text = "🌊 ${g.wave}/${Game.TOTAL_WAVES}"
        enemiesText.text = "⚔ ${g.enemiesLeft}"
        hpBar.progress = (g.castleHp / Game.CASTLE_HP * 1000).toInt()

        for ((t, v) in buildButtons) v.alpha = if (g.gold >= t.cost) 1f else 0.4f

        if (g.toastSeq != lastToastSeq) {
            lastToastSeq = g.toastSeq
            toast.text = g.toast
            toast.visibility = View.VISIBLE
            toastHideAt = System.currentTimeMillis() + 2200
        }
        if (toast.visibility == View.VISIBLE && System.currentTimeMillis() > toastHideAt) toast.visibility = View.GONE

        if (g.hitSeq != lastHitSeq) { lastHitSeq = g.hitSeq; vibrate(18) }

        // ── وضع الإيد ──
        val handOn = renderer.handMode
        val handErr = handTracker?.error
        handButton.visibility = if (renderer.placed && handErr == null) View.VISIBLE else View.GONE
        handButton.alpha = if (handOn) 1f else 0.5f
        handButton.background = rounded(if (handOn) 0xFF2B2410.toInt() else PANEL, 22f, if (handOn) GOLD else GOLD_SOFT)
        handHelp.visibility = if (renderer.placed && handOn && playing && handErr == null) View.VISIBLE else View.GONE
        handStatus.visibility = handHelp.visibility
        handStatus.text = when {
            handTracker?.ready != true -> "✋ جاري تجهيز التعرف على الإيد…"
            renderer.handPinching -> "🤏 ماسك ${renderer.selectedType.label}"
            renderer.handSeen -> "✋ شايف إيدك — المختار: ${renderer.selectedType.label}"
            else -> "✋ دخّل إيدك قدام الكاميرا"
        }
        handStatus.setTextColor(if (renderer.handSeen) 0xFF8CE99A.toInt() else Color.WHITE)
        for ((t, v) in buildButtons) {
            val sel = handOn && t == renderer.selectedType
            v.background = rounded(0xFF1C1C22.toInt(), 16f, if (sel) 0xFFFFD43B.toInt() else GOLD_SOFT)
            v.scaleX = if (sel) 1.06f else 1f; v.scaleY = v.scaleX
        }
        if (renderer.handEventSeq != lastHandEventSeq) {
            lastHandEventSeq = renderer.handEventSeq
            when (renderer.handEvent) {
                "select" -> { showToast("${emojiOf(renderer.selectedType)} ${renderer.selectedType.label} — اكمش حتى تبني"); vibrate(12) }
                "wave" -> { showToast("👍 بدأت الموجة!"); vibrate(30) }
                "hit" -> vibrate(25)
            }
        }

        when (state) {
            GameState.GAME_OVER -> {
                endPanel.visibility = View.VISIBLE
                endTitle.text = "سقطت القلعة 💥"
                endSub.text = "صمدت لحد الموجة ${g.wave}"
            }
            GameState.VICTORY -> {
                endPanel.visibility = View.VISIBLE
                endTitle.text = "انتصرت! 👑"
                endSub.text = "صدّيت كل الموجات العشرة"
            }
            else -> endPanel.visibility = View.GONE
        }
    }

    private fun emojiOf(t: BType) = when (t) { BType.WALL -> "☝️"; BType.TOWER -> "✌️"; BType.SPIKE -> "🤟" }

    private fun showToast(msg: String) {
        toast.text = msg
        toast.visibility = View.VISIBLE
        toastHideAt = System.currentTimeMillis() + 2200
    }

    @Suppress("DEPRECATION")
    private fun vibrate(ms: Long) {
        try {
            val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, 80))
            else v.vibrate(ms)
        } catch (_: Exception) {}
    }

    // ───────────── بناء الواجهة ─────────────
    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private fun dpi(v: Float) = dp(v).toInt()

    private fun rounded(color: Int, radius: Float = 16f, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius)
        if (stroke != null) setStroke(dpi(1.5f), stroke)
    }

    private fun label(text: String, size: Float = 15f, color: Int = GOLD, bold: Boolean = true) = TextView(this).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
    }

    private fun goldButton(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextColor(Color.BLACK)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        setTypeface(typeface, Typeface.BOLD)
        background = rounded(GOLD, 22f)
        setPadding(dpi(22f), dpi(10f), dpi(22f), dpi(10f))
        setOnClickListener { onClick() }
    }

    private fun chip(v: TextView): TextView = v.apply {
        background = rounded(PANEL, 14f, GOLD_SOFT)
        setPadding(dpi(10f), dpi(6f), dpi(10f), dpi(6f))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildUi(): View {
        val root = FrameLayout(this)
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        root.addView(surface, FrameLayout.LayoutParams(-1, -1))

        surface.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_DOWN) {
                if (!renderer.placed) renderer.commands.add(Cmd.Place)
                else renderer.commands.add(Cmd.Tap(e.x, e.y))
            }
            true
        }

        // ── العداد العلوي ──
        hud = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(12f), dpi(14f), dpi(12f), 0)
            visibility = View.GONE
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        goldText = chip(label("💰 120"))
        waveText = chip(label("🌊 0/10"))
        enemiesText = chip(label("⚔ 0"))
        val lp = { LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dpi(6f) } }
        row.addView(goldText, lp()); row.addView(waveText, lp()); row.addView(enemiesText, lp())
        row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        hud.addView(row)
        val hpRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = rounded(PANEL, 14f, GOLD_SOFT)
            setPadding(dpi(10f), dpi(6f), dpi(10f), dpi(6f))
        }
        hpRow.addView(label("🏰", 15f))
        hpBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000; progress = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(0xFF51CF66.toInt())
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0xFF333333.toInt())
            scaleY = 2.2f
        }
        hpRow.addView(hpBar, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dpi(8f) })
        hud.addView(hpRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(8f) })
        root.addView(hud, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        // زر حول
        val about = label("ⓘ", 22f).apply {
            background = rounded(PANEL, 22f, GOLD_SOFT)
            setOnClickListener { showAbout() }
        }
        root.addView(about, FrameLayout.LayoutParams(dpi(44f), dpi(44f), Gravity.TOP or Gravity.START).apply {
            topMargin = dpi(96f); marginStart = dpi(12f)
        })

        // ── هيكل الإيد فوق الكاميرا ──
        root.addView(HandOverlay(this, { if (renderer.handMode) renderer.handViewPts else null }, { renderer.handPinching }),
            FrameLayout.LayoutParams(-1, -1))

        // زر وضع الإيد
        handButton = label("✋", 22f).apply {
            setOnClickListener {
                renderer.handMode = !renderer.handMode
                showToast(if (renderer.handMode) "✋ التحكم بالإيد شغّال" else "👆 التحكم باللمس فقط")
            }
            visibility = View.GONE
        }
        root.addView(handButton, FrameLayout.LayoutParams(dpi(44f), dpi(44f), Gravity.TOP or Gravity.START).apply {
            topMargin = dpi(148f); marginStart = dpi(12f)
        })
        handStatus = label("", 13f, Color.WHITE).apply {
            background = rounded(PANEL, 12f)
            setPadding(dpi(10f), dpi(4f), dpi(10f), dpi(4f))
            visibility = View.GONE
        }
        root.addView(handStatus, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dpi(100f) })
        handHelp = label("☝️ جدار   ✌️ برج   🤟 فخ\n🤏 اكمش وحرّك وافتح = بناء   👋 اضرب بسرعة   👍 الموجة", 11.5f, Color.WHITE, false).apply {
            background = rounded(0xB0101014.toInt(), 12f)
            setPadding(dpi(10f), dpi(5f), dpi(10f), dpi(5f))
            visibility = View.GONE
        }
        root.addView(handHelp, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dpi(178f) })

        // ── التلميح والتوست ──
        hint = label("", 17f, Color.WHITE).apply {
            background = rounded(PANEL, 18f)
            setPadding(dpi(18f), dpi(12f), dpi(18f), dpi(12f))
        }
        root.addView(hint, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER).apply { leftMargin = dpi(24f); rightMargin = dpi(24f) })
        toast = label("", 18f).apply {
            background = rounded(PANEL, 18f, GOLD)
            setPadding(dpi(18f), dpi(10f), dpi(18f), dpi(10f))
            visibility = View.GONE
        }
        root.addView(toast, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL or Gravity.TOP).apply { topMargin = dpi(150f) })

        // ── لوحة وضع القلعة ──
        placePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = rounded(PANEL, 22f, GOLD_SOFT)
            setPadding(dpi(18f), dpi(14f), dpi(18f), dpi(16f))
        }
        placePanel.addView(label("IBRA CASTLE AR", 20f))
        placePanel.addView(label("حجم الساحة", 14f, Color.WHITE, false), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(8f) })
        val seek = SeekBar(this).apply {
            max = 100; progress = 33
            progressTintList = android.content.res.ColorStateList.valueOf(GOLD)
            thumbTintList = android.content.res.ColorStateList.valueOf(GOLD)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    renderer.boardScale = 0.03f + (0.12f - 0.03f) * p / 100f
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        renderer.boardScale = 0.03f + 0.09f * 0.33f
        placePanel.addView(seek, LinearLayout.LayoutParams(-1, -2))
        placeButton = goldButton("ضع القلعة 🏰") { renderer.commands.add(Cmd.Place) }
        placePanel.addView(placeButton, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(8f) })
        root.addView(placePanel, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(dpi(16f), 0, dpi(16f), dpi(28f)) })

        // ── شريط البناء ──
        buildBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(PANEL, 22f, GOLD_SOFT)
            setPadding(dpi(8f), dpi(8f), dpi(8f), dpi(8f))
            visibility = View.GONE
        }
        for ((t, emoji) in listOf(BType.WALL to "🧱", BType.TOWER to "🏹", BType.SPIKE to "📌")) {
            val b = buildButton(t, emoji)
            buildButtons[t] = b
            buildBar.addView(b, LinearLayout.LayoutParams(0, dpi(84f), 1f).apply { setMargins(dpi(4f), 0, dpi(4f), 0) })
        }
        root.addView(buildBar, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(dpi(12f), 0, dpi(12f), dpi(20f)) })
        root.addView(label("اسحب الدفاع وافلته على الساحة • المس الجنود لضربهم", 12f, Color.WHITE, false).apply {
            setShadowLayer(4f, 0f, 1f, Color.BLACK)
        }, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { bottomMargin = dpi(4f) })

        waveButton = goldButton("ابدأ الموجة ⚔") { renderer.commands.add(Cmd.StartWave) }.apply { visibility = View.GONE }
        root.addView(waveButton, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dpi(124f) })

        // ── شاشة النهاية ──
        endPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(0xEE0B0B0E.toInt(), 26f, GOLD)
            setPadding(dpi(24f), dpi(24f), dpi(24f), dpi(24f))
            visibility = View.GONE
            isClickable = true
        }
        endTitle = label("", 28f)
        endSub = label("", 16f, Color.WHITE, false)
        endPanel.addView(endTitle)
        endPanel.addView(endSub, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(8f) })
        endPanel.addView(goldButton("العب مرة ثانية 🔄") { renderer.commands.add(Cmd.Restart) },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(18f) })
        endPanel.addView(label("برمجة وتطوير ابراهيم الخزرجي", 13f, GOLD_SOFT, false),
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(14f) })
        root.addView(endPanel, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER).apply { setMargins(dpi(28f), 0, dpi(28f), 0) })

        return root
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildButton(t: BType, emoji: String): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(0xFF1C1C22.toInt(), 16f, GOLD_SOFT)
        }
        box.addView(label(emoji, 26f))
        box.addView(label(t.label, 13f, Color.WHITE))
        box.addView(label("💰 ${t.cost}", 12f))
        box.setOnTouchListener { v, e ->
            surface.getLocationOnScreen(surfaceLoc)
            val x = e.rawX - surfaceLoc[0]
            val y = e.rawY - surfaceLoc[1]
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.scaleX = 1.1f; v.scaleY = 1.1f
                    renderer.commands.add(Cmd.GhostBegin(t))
                    renderer.commands.add(Cmd.GhostMove(x, y))
                }
                MotionEvent.ACTION_MOVE -> renderer.commands.add(Cmd.GhostMove(x, y))
                MotionEvent.ACTION_UP -> {
                    v.scaleX = 1f; v.scaleY = 1f
                    renderer.commands.add(Cmd.GhostEnd(x, y, true))
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.scaleX = 1f; v.scaleY = 1f
                    renderer.commands.add(Cmd.GhostEnd(x, y, false))
                }
            }
            true
        }
        return box
    }

    private fun showAbout() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dpi(22f), dpi(22f), dpi(22f), dpi(18f))
            background = rounded(0xFF0E0E12.toInt(), 22f, GOLD)
        }
        box.addView(label("🏰 IBRA CASTLE AR", 22f))
        box.addView(label("دافع عن قلعتك بالواقع المعزز", 14f, Color.WHITE, false), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(6f) })
        box.addView(label("برمجة وتطوير ابراهيم الخزرجي", 16f), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(16f) })
        val links = listOf("🌐 موقع تطبيقاتي" to SITE, "✈️ تليكرام ibraprogram" to TELEGRAM, "🎵 تيك توك ibraprogram" to TIKTOK)
        for ((txt, url) in links) {
            box.addView(goldButton(txt) { open(url) }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(10f) })
        }
        box.addView(label("الإصدار 1.0", 12f, 0xFF888888.toInt(), false), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(12f) })
        val dlg = AlertDialog.Builder(this).setView(box).create()
        dlg.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dlg.show()
    }

    private fun open(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
    }

    @Suppress("unused")
    private fun unusedVG(): ViewGroup? = null
}
