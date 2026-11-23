import ARKit
import SceneKit
import UIKit

/// IBRA CASTLE AR — شاشة اللعبة: كاميرا ARKit + SceneKit + الواجهة العربية.
/// برمجة وتطوير ابراهيم الخزرجي
final class GameViewController: UIViewController, ARSCNViewDelegate, ARCoachingOverlayViewDelegate, GestureListener {

    static let gold = UIColor(red: 0.83, green: 0.69, blue: 0.22, alpha: 1)
    static let goldSoft = UIColor(red: 0.79, green: 0.66, blue: 0.30, alpha: 1)
    static let panel = UIColor(red: 0.06, green: 0.06, blue: 0.08, alpha: 0.85)
    static let site = "https://ibraprogram.netlify.app/"
    static let telegram = "https://t.me/ibraprogram"
    static let tiktok = "https://www.tiktok.com/@ibraprogram"

    private let sceneView = ARSCNView()
    private let game: Game
    private let anchorNode = SCNNode()
    private let boardNode = SCNNode()
    private let reticleNode = SCNNode()
    private var boardAnchor: ARAnchor?
    private var boardScale: Float = 0.06
    private var placed = false
    private var hasPose = false
    private var reticleTransform = matrix_identity_float4x4
    private var displayLink: CADisplayLink?
    private var lastTime: CFTimeInterval = 0
    private var trackingOK = false
    private let haptic = UIImpactFeedbackGenerator(style: .light)

    // الواجهة
    private let hud = UIStackView()
    private let goldLabel = UILabel(), waveLabel = UILabel(), enemiesLabel = UILabel()
    private let hpBar = UIProgressView(progressViewStyle: .bar)
    private let hint = UILabel()
    private let toast = UILabel()
    private let placePanel = UIStackView()
    private let placeButton = UIButton(type: .system)
    private let buildBar = UIStackView()
    private let waveButton = UIButton(type: .system) // احتياطي
    private let endPanel = UIStackView()
    private let endTitle = UILabel(), endSub = UILabel()
    private var buildButtons: [BType: BuildButtonView] = [:]
    private var toastHideAt: CFTimeInterval = 0

    // التحكم بالإيد
    private let hand = HandTracker()
    private let gestures = GestureEngine()
    private var handMode = true
    private var selectedType: BType = .tower
    private var handSeen = false
    private var lastHandTime: CFTimeInterval = 0
    private let overlay = HandOverlayView()
    private let cursorNode = SCNNode()
    private let handButton = UIButton(type: .system)
    private let handHelp = UILabel()
    private let handStatus = UILabel()

    override init(nibName: String?, bundle: Bundle?) {
        Models.build(w: 13, h: 13)
        game = Game()
        super.init(nibName: nil, bundle: nil)
    }
    required init?(coder: NSCoder) { fatalError() }

    override var prefersStatusBarHidden: Bool { true }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        view.semanticContentAttribute = .forceRightToLeft

        sceneView.frame = view.bounds
        sceneView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        sceneView.delegate = self
        sceneView.automaticallyUpdatesLighting = false
        sceneView.autoenablesDefaultLighting = false
        sceneView.antialiasingMode = .multisampling2X
        view.addSubview(sceneView)
        setupLights()

        anchorNode.addChildNode(boardNode)
        boardNode.addChildNode(game.root)
        reticleNode.addChildNode(Models.reticle.clone())
        let preview = Models.castle.clone()
        preview.opacity = 0.8
        reticleNode.addChildNode(preview)
        reticleNode.isHidden = true
        sceneView.scene.rootNode.addChildNode(reticleNode)

        // حلقة ذهبية على الساحة تحت إصبعك
        let ring = SCNTorus(ringRadius: 0.38, pipeRadius: 0.05)
        ring.firstMaterial = Models.unlit(0xFFC93C)
        cursorNode.geometry = ring
        cursorNode.isHidden = true
        boardNode.addChildNode(cursorNode)
        hand.onResult = { [weak self] pts in self?.handleHand(pts) }

        game.onToast = { [weak self] msg in self?.showToast(msg) }
        game.onHit = { [weak self] in self?.haptic.impactOccurred() }

        buildUI()

        let coaching = ARCoachingOverlayView()
        coaching.session = sceneView.session
        coaching.goal = .horizontalPlane
        coaching.activatesAutomatically = true
        coaching.delegate = self
        coaching.frame = view.bounds
        coaching.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.insertSubview(coaching, aboveSubview: sceneView)
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        guard ARWorldTrackingConfiguration.isSupported else {
            hint.text = "هذا الجهاز ما يدعم الواقع المعزز (ARKit)"
            hint.isHidden = false
            placePanel.isHidden = true
            return
        }
        let cfg = ARWorldTrackingConfiguration()
        cfg.planeDetection = placed ? [] : [.horizontal]
        cfg.environmentTexturing = .none
        sceneView.session.run(cfg)
        UIApplication.shared.isIdleTimerDisabled = true
        displayLink = CADisplayLink(target: self, selector: #selector(tick))
        displayLink?.add(to: .main, forMode: .common)
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        displayLink?.invalidate(); displayLink = nil
        sceneView.session.pause()
    }

    private func setupLights() {
        let sun = SCNNode()
        sun.light = SCNLight()
        sun.light!.type = .directional
        sun.light!.intensity = 900
        sun.simdEulerAngles = SIMD3(-Float.pi / 3, Float.pi / 5, 0)
        sceneView.scene.rootNode.addChildNode(sun)
        let amb = SCNNode()
        amb.light = SCNLight()
        amb.light!.type = .ambient
        amb.light!.intensity = 520
        sceneView.scene.rootNode.addChildNode(amb)
    }

    // MARK: حلقة اللعبة
    @objc private func tick(_ link: CADisplayLink) {
        let now = link.timestamp
        let dt = lastTime == 0 ? 1.0 / 60 : now - lastTime
        lastTime = now

        if !placed { updateReticle() }
        else {
            if handMode, let f = sceneView.session.currentFrame { hand.submit(f) }
            if handSeen && (now - lastHandTime > 0.4 || !handMode) {
                handSeen = false; gestures.lost(self); overlay.points = nil; cursorNode.isHidden = true
            }
            if !cursorNode.isHidden {
                let s = gestures.pinching ? 0.8 : 1 + 0.08 * sin(Float(now) * 7)
                cursorNode.simdScale = SIMD3(repeating: Float(s))
            }
            game.update(Float(dt))
        }
        refreshUI()
    }

    private func updateReticle() {
        let center = CGPoint(x: sceneView.bounds.midX, y: sceneView.bounds.midY)
        hasPose = false
        if let q = sceneView.raycastQuery(from: center, allowing: .existingPlaneGeometry, alignment: .horizontal),
           let r = sceneView.session.raycast(q).first {
            var t = matrix_identity_float4x4
            t.columns.3 = r.worldTransform.columns.3
            reticleTransform = t
            hasPose = true
        }
        reticleNode.isHidden = !hasPose
        if hasPose {
            reticleNode.simdTransform = reticleTransform
            let pulse = Float(0.85 + 0.15 * sin(CACurrentMediaTime() * 4))
            let s = boardScale
            reticleNode.childNodes[0].simdScale = SIMD3(13 * s, s, 13 * s)
            reticleNode.childNodes[1].simdScale = SIMD3(repeating: s * pulse)
        }
    }

    private func placeBoard() {
        guard !placed, hasPose else { return }
        placed = true
        reticleNode.isHidden = true
        boardNode.simdScale = SIMD3(repeating: boardScale)
        let a = ARAnchor(name: "board", transform: reticleTransform)
        boardAnchor = a
        sceneView.session.add(anchor: a)
        // نوقف كشف الأسطح ونخفيها
        let cfg = ARWorldTrackingConfiguration()
        cfg.planeDetection = []
        sceneView.session.run(cfg)
        sceneView.scene.rootNode.enumerateChildNodes { n, _ in if n.name == "plane" { n.isHidden = true } }
        game.onPlaced()
    }

    // MARK: ARSCNViewDelegate
    func renderer(_ renderer: SCNSceneRenderer, nodeFor anchor: ARAnchor) -> SCNNode? {
        if anchor.name == "board" { return anchorNode }
        return nil
    }

    func renderer(_ renderer: SCNSceneRenderer, didAdd node: SCNNode, for anchor: ARAnchor) {
        guard let p = anchor as? ARPlaneAnchor, !placed else { return }
        let plane = SCNPlane(width: CGFloat(p.extent.x), height: CGFloat(p.extent.z))
        let m = SCNMaterial()
        m.diffuse.contents = UIColor(red: 1, green: 0.82, blue: 0.35, alpha: 0.28)
        m.lightingModel = .constant
        plane.firstMaterial = m
        let pn = SCNNode(geometry: plane)
        pn.name = "plane"
        pn.simdPosition = p.center
        pn.simdEulerAngles.x = -.pi / 2
        node.addChildNode(pn)
    }

    func renderer(_ renderer: SCNSceneRenderer, didUpdate node: SCNNode, for anchor: ARAnchor) {
        guard let p = anchor as? ARPlaneAnchor, let pn = node.childNodes.first(where: { $0.name == "plane" }),
              let plane = pn.geometry as? SCNPlane else { return }
        plane.width = CGFloat(p.extent.x)
        plane.height = CGFloat(p.extent.z)
        pn.simdPosition = p.center
        pn.isHidden = placed
    }

    func session(_ session: ARSession, cameraDidChangeTrackingState camera: ARCamera) {
        if case .normal = camera.trackingState { trackingOK = true } else { trackingOK = false }
    }

    // MARK: الأشعة
    private func screenRay(_ p: CGPoint) -> Ray {
        let near = sceneView.unprojectPoint(SCNVector3(Float(p.x), Float(p.y), 0))
        let far = sceneView.unprojectPoint(SCNVector3(Float(p.x), Float(p.y), 1))
        let o = boardNode.simdConvertPosition(SIMD3(near.x, near.y, near.z), from: nil)
        let f = boardNode.simdConvertPosition(SIMD3(far.x, far.y, far.z), from: nil)
        return Ray(origin: o, dir: simd_normalize(f - o))
    }

    // MARK: الإيد
    private func handleHand(_ pts: [CGPoint]?) {
        guard placed, handMode, let pts = pts, let frame = sceneView.session.currentFrame else { return }
        let size = sceneView.bounds.size
        let t = frame.displayTransform(for: .portrait, viewportSize: size)
        let view = pts.map { p -> CGPoint in
            let n = p.applying(t)
            return CGPoint(x: n.x * size.width, y: n.y * size.height)
        }
        handSeen = true
        lastHandTime = CACurrentMediaTime()
        overlay.pinching = gestures.pinching
        overlay.points = view
        gestures.update(view, t: lastHandTime, viewH: size.height, self)
    }

    private func handGround(_ p: CGPoint) -> V3? { screenRay(p).hitPlaneY(0) }

    func onCursor(_ p: CGPoint, pinching: Bool) {
        guard let g = handGround(p), game.inBounds(game.cellOf(g)) else { cursorNode.isHidden = true; return }
        cursorNode.isHidden = false
        cursorNode.simdPosition = V3(g.x, 0.06, g.z)
        cursorNode.geometry?.firstMaterial = Models.unlit(pinching ? 0x51CF66 : 0xFFC93C)
    }

    func onHit(_ p: CGPoint) {
        if game.handHit(screenRay(p)) { UIImpactFeedbackGenerator(style: .medium).impactOccurred() }
    }

    func onSelect(_ t: BType) {
        selectedType = t
        showToast("\(emoji(t)) \(t.label) — اكمش حتى تبني")
        haptic.impactOccurred()
    }

    func onPinchStart(_ p: CGPoint) { game.beginGhost(selectedType); game.moveGhost(handGround(p)) }
    func onPinchMove(_ p: CGPoint) { game.moveGhost(handGround(p)) }
    func onPinchEnd(_ p: CGPoint, place: Bool) {
        if place { game.moveGhost(handGround(p)) }
        game.endGhost(place: place)
    }
    func onThumbsUp() {
        if game.state == .building {
            game.startWave()
            showToast("👍 بدأت الموجة!")
            UINotificationFeedbackGenerator().notificationOccurred(.success)
        }
    }

    private func emoji(_ t: BType) -> String { t == .wall ? "☝️" : t == .tower ? "✌️" : "🤟" }

    @objc private func toggleHand() {
        handMode.toggle()
        showToast(handMode ? "✋ التحكم بالإيد شغّال" : "👆 التحكم باللمس فقط")
    }

    private func groundPoint(_ p: CGPoint) -> V3? {
        screenRay(CGPoint(x: p.x, y: p.y - sceneView.bounds.height * 0.06)).hitPlaneY(0)
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let t = touches.first else { return }
        let p = t.location(in: sceneView)
        if !placed { placeBoard(); return }
        game.tap(screenRay(p))
    }

    // MARK: الواجهة
    private func refreshUI() {
        let playing = game.state == .building || game.state == .wave
        if !placed {
            hud.isHidden = true; buildBar.isHidden = true; waveButtonView.isHidden = true
            placePanel.isHidden = false
            hint.isHidden = false
            hint.text = hasPose ? "المس الشاشة أو اضغط «ضع القلعة» 🏰" : "وجّه الكاميرا على طاولة أو أرض مستوية…"
            placeButton.isEnabled = hasPose
            placeButton.alpha = hasPose ? 1 : 0.45
        } else {
            placePanel.isHidden = true
            hud.isHidden = false
            hint.isHidden = trackingOK
            if !trackingOK { hint.text = "المتابعة ضاعت — رجّع الكاميرا على الساحة" }
            buildBar.isHidden = !playing
            waveButtonView.isHidden = game.state != .building
            waveButtonView.setTitle("ابدأ الموجة \(game.wave + 1) ⚔", for: .normal)
        }
        goldLabel.text = " 💰 \(game.gold) "
        waveLabel.text = " 🌊 \(game.wave)/\(Game.totalWaves) "
        enemiesLabel.text = " ⚔ \(game.enemiesLeft) "
        hpBar.progress = game.castleHp / Game.castleMaxHp
        for (t, b) in buildButtons {
            b.alpha = game.gold >= t.cost ? 1 : 0.4
            let sel = handMode && t == selectedType
            b.layer.borderColor = (sel ? UIColor(red: 1, green: 0.83, blue: 0.23, alpha: 1) : GameViewController.goldSoft).cgColor
            b.layer.borderWidth = sel ? 3 : 1.5
        }
        handButton.isHidden = !placed
        handButton.alpha = handMode ? 1 : 0.5
        handButton.layer.borderColor = (handMode ? GameViewController.gold : GameViewController.goldSoft).cgColor
        handHelp.isHidden = !(placed && handMode && playing)
        handStatus.isHidden = handHelp.isHidden
        handStatus.text = gestures.pinching ? "  🤏 ماسك \(selectedType.label)  "
            : handSeen ? "  ✋ شايف إيدك — المختار: \(selectedType.label)  " : "  ✋ دخّل إيدك قدام الكاميرا  "
        handStatus.textColor = handSeen ? UIColor(red: 0.55, green: 0.91, blue: 0.6, alpha: 1) : .white
        if !toast.isHidden && CACurrentMediaTime() > toastHideAt { toast.isHidden = true }

        switch game.state {
        case .gameOver:
            endPanel.isHidden = false
            endTitle.text = "سقطت القلعة 💥"
            endSub.text = "صمدت لحد الموجة \(game.wave)"
        case .victory:
            endPanel.isHidden = false
            endTitle.text = "انتصرت! 👑"
            endSub.text = "صدّيت كل الموجات العشرة"
        default:
            endPanel.isHidden = true
        }
    }

    private func showToast(_ msg: String) {
        toast.text = "  \(msg)  "
        toast.isHidden = false
        toastHideAt = CACurrentMediaTime() + 2.2
    }

    private func makeLabel(_ text: String, size: CGFloat, color: UIColor = GameViewController.gold, bold: Bool = true) -> UILabel {
        let l = UILabel()
        l.text = text
        l.textColor = color
        l.font = bold ? .boldSystemFont(ofSize: size) : .systemFont(ofSize: size)
        l.textAlignment = .center
        l.numberOfLines = 0
        return l
    }

    private func chip(_ l: UILabel) {
        l.font = .boldSystemFont(ofSize: 15)
        l.textColor = GameViewController.gold
        l.backgroundColor = GameViewController.panel
        l.layer.cornerRadius = 12
        l.layer.borderWidth = 1.5
        l.layer.borderColor = GameViewController.goldSoft.cgColor
        l.clipsToBounds = true
        l.textAlignment = .center
        l.heightAnchor.constraint(equalToConstant: 32).isActive = true
    }

    private func goldButton(_ title: String, action: Selector) -> UIButton {
        let b = UIButton(type: .system)
        b.setTitle(title, for: .normal)
        b.setTitleColor(.black, for: .normal)
        b.titleLabel?.font = .boldSystemFont(ofSize: 18)
        b.backgroundColor = GameViewController.gold
        b.layer.cornerRadius = 22
        b.contentEdgeInsets = UIEdgeInsets(top: 10, left: 22, bottom: 10, right: 22)
        b.addTarget(self, action: action, for: .touchUpInside)
        return b
    }

    private func panelStyle(_ v: UIView, radius: CGFloat = 22, border: UIColor = GameViewController.goldSoft) {
        v.backgroundColor = GameViewController.panel
        v.layer.cornerRadius = radius
        v.layer.borderWidth = 1.5
        v.layer.borderColor = border.cgColor
    }

    private func buildUI() {
        func pin(_ v: UIView) { v.translatesAutoresizingMaskIntoConstraints = false; view.addSubview(v) }
        let safe = view.safeAreaLayoutGuide

        // العداد العلوي
        hud.axis = .vertical; hud.spacing = 8
        let row = UIStackView(arrangedSubviews: [goldLabel, waveLabel, enemiesLabel, UIView()])
        row.axis = .horizontal; row.spacing = 6
        [goldLabel, waveLabel, enemiesLabel].forEach(chip)
        let hpRow = UIStackView(arrangedSubviews: [makeLabel("🏰", size: 15), hpBar])
        hpRow.axis = .horizontal; hpRow.spacing = 8; hpRow.alignment = .center
        hpRow.isLayoutMarginsRelativeArrangement = true
        hpRow.layoutMargins = UIEdgeInsets(top: 6, left: 10, bottom: 6, right: 10)
        panelStyle(hpRow, radius: 12)
        hpBar.progressTintColor = UIColor(red: 0.32, green: 0.81, blue: 0.4, alpha: 1)
        hpBar.trackTintColor = UIColor(white: 0.2, alpha: 1)
        hpBar.layer.cornerRadius = 4; hpBar.clipsToBounds = true
        hpBar.heightAnchor.constraint(equalToConstant: 8).isActive = true
        hud.addArrangedSubview(row); hud.addArrangedSubview(hpRow)
        hud.isHidden = true
        pin(hud)
        NSLayoutConstraint.activate([
            hud.topAnchor.constraint(equalTo: safe.topAnchor, constant: 8),
            hud.leadingAnchor.constraint(equalTo: safe.leadingAnchor, constant: 12),
            hud.trailingAnchor.constraint(equalTo: safe.trailingAnchor, constant: -12)
        ])

        // زر حول
        let about = UIButton(type: .system)
        about.setTitle("ⓘ", for: .normal)
        about.titleLabel?.font = .boldSystemFont(ofSize: 22)
        about.setTitleColor(GameViewController.gold, for: .normal)
        panelStyle(about, radius: 22)
        about.addTarget(self, action: #selector(showAbout), for: .touchUpInside)
        pin(about)
        NSLayoutConstraint.activate([
            about.widthAnchor.constraint(equalToConstant: 44), about.heightAnchor.constraint(equalToConstant: 44),
            about.topAnchor.constraint(equalTo: safe.topAnchor, constant: 92),
            about.leadingAnchor.constraint(equalTo: safe.leadingAnchor, constant: 12)
        ])

        // هيكل الإيد
        overlay.frame = view.bounds
        overlay.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(overlay)

        // زر وضع الإيد
        handButton.setTitle("✋", for: .normal)
        handButton.titleLabel?.font = .systemFont(ofSize: 22)
        panelStyle(handButton, radius: 22)
        handButton.addTarget(self, action: #selector(toggleHand), for: .touchUpInside)
        handButton.isHidden = true
        pin(handButton)
        NSLayoutConstraint.activate([
            handButton.widthAnchor.constraint(equalToConstant: 44), handButton.heightAnchor.constraint(equalToConstant: 44),
            handButton.topAnchor.constraint(equalTo: about.bottomAnchor, constant: 10),
            handButton.leadingAnchor.constraint(equalTo: safe.leadingAnchor, constant: 12)
        ])
        handStatus.font = .boldSystemFont(ofSize: 13)
        handStatus.textAlignment = .center
        handStatus.backgroundColor = GameViewController.panel
        handStatus.layer.cornerRadius = 11; handStatus.clipsToBounds = true
        handStatus.isHidden = true
        pin(handStatus)
        NSLayoutConstraint.activate([
            handStatus.topAnchor.constraint(equalTo: hud.bottomAnchor, constant: 8),
            handStatus.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            handStatus.heightAnchor.constraint(equalToConstant: 26)
        ])
        handHelp.text = "☝️ جدار   ✌️ برج   🤟 فخ\n🤏 اكمش وحرّك وافتح = بناء   👋 اضرب بسرعة   👍 الموجة"
        handHelp.numberOfLines = 2
        handHelp.font = .systemFont(ofSize: 11.5)
        handHelp.textColor = .white
        handHelp.textAlignment = .center
        handHelp.backgroundColor = UIColor(red: 0.06, green: 0.06, blue: 0.08, alpha: 0.7)
        handHelp.layer.cornerRadius = 12; handHelp.clipsToBounds = true
        handHelp.isHidden = true
        pin(handHelp)

        // التلميح
        hint.textColor = .white
        hint.font = .boldSystemFont(ofSize: 17)
        hint.textAlignment = .center
        hint.numberOfLines = 0
        hint.backgroundColor = GameViewController.panel
        hint.layer.cornerRadius = 16; hint.clipsToBounds = true
        pin(hint)
        NSLayoutConstraint.activate([
            hint.centerYAnchor.constraint(equalTo: view.centerYAnchor, constant: -60),
            hint.leadingAnchor.constraint(equalTo: safe.leadingAnchor, constant: 28),
            hint.trailingAnchor.constraint(equalTo: safe.trailingAnchor, constant: -28),
            hint.heightAnchor.constraint(greaterThanOrEqualToConstant: 52)
        ])

        toast.font = .boldSystemFont(ofSize: 18)
        toast.textColor = GameViewController.gold
        toast.textAlignment = .center
        panelStyle(toast, radius: 16, border: GameViewController.gold)
        toast.clipsToBounds = true
        toast.isHidden = true
        pin(toast)
        NSLayoutConstraint.activate([
            toast.topAnchor.constraint(equalTo: safe.topAnchor, constant: 150),
            toast.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            toast.heightAnchor.constraint(equalToConstant: 44)
        ])

        // لوحة الوضع
        placePanel.axis = .vertical; placePanel.spacing = 8; placePanel.alignment = .fill
        placePanel.isLayoutMarginsRelativeArrangement = true
        placePanel.layoutMargins = UIEdgeInsets(top: 14, left: 18, bottom: 16, right: 18)
        panelStyle(placePanel)
        let slider = UISlider()
        slider.minimumValue = 0.03; slider.maximumValue = 0.12; slider.value = boardScale
        slider.minimumTrackTintColor = GameViewController.gold
        slider.thumbTintColor = GameViewController.gold
        slider.addTarget(self, action: #selector(scaleChanged(_:)), for: .valueChanged)
        placeButton.setTitle("ضع القلعة 🏰", for: .normal)
        placeButton.setTitleColor(.black, for: .normal)
        placeButton.titleLabel?.font = .boldSystemFont(ofSize: 18)
        placeButton.backgroundColor = GameViewController.gold
        placeButton.layer.cornerRadius = 22
        placeButton.heightAnchor.constraint(equalToConstant: 48).isActive = true
        placeButton.addTarget(self, action: #selector(placeTapped), for: .touchUpInside)
        placePanel.addArrangedSubview(makeLabel("IBRA CASTLE AR", size: 20))
        placePanel.addArrangedSubview(makeLabel("حجم الساحة", size: 14, color: .white, bold: false))
        placePanel.addArrangedSubview(slider)
        placePanel.addArrangedSubview(placeButton)
        pin(placePanel)
        NSLayoutConstraint.activate([
            placePanel.leadingAnchor.constraint(equalTo: safe.leadingAnchor, constant: 16),
            placePanel.trailingAnchor.constraint(equalTo: safe.trailingAnchor, constant: -16),
            placePanel.bottomAnchor.constraint(equalTo: safe.bottomAnchor, constant: -16)
        ])

        // شريط البناء
        buildBar.axis = .horizontal; buildBar.distribution = .fillEqually; buildBar.spacing = 8
        buildBar.isLayoutMarginsRelativeArrangement = true
        buildBar.layoutMargins = UIEdgeInsets(top: 8, left: 8, bottom: 8, right: 8)
        panelStyle(buildBar)
        for t in BType.allCases {
            let b = BuildButtonView(type: t)
            b.onBegin = { [weak self] p in guard let s = self else { return }
                s.game.beginGhost(t); s.game.moveGhost(s.groundPoint(b.convert(p, to: s.sceneView))) }
            b.onMove = { [weak self] p in guard let s = self else { return }
                s.game.moveGhost(s.groundPoint(b.convert(p, to: s.sceneView))) }
            b.onEnd = { [weak self] p, place in guard let s = self else { return }
                s.game.moveGhost(s.groundPoint(b.convert(p, to: s.sceneView))); s.game.endGhost(place: place) }
            buildButtons[t] = b
            buildBar.addArrangedSubview(b)
        }
        buildBar.isHidden = true
        pin(buildBar)
        NSLayoutConstraint.activate([
            buildBar.leadingAnchor.constraint(equalTo: safe.leadingAnchor, constant: 12),
            buildBar.trailingAnchor.constraint(equalTo: safe.trailingAnchor, constant: -12),
            buildBar.bottomAnchor.constraint(equalTo: safe.bottomAnchor, constant: -22),
            buildBar.heightAnchor.constraint(equalToConstant: 100)
        ])
        let tip = makeLabel("اسحب الدفاع وافلته على الساحة • المس الجنود لضربهم", size: 12, color: .white, bold: false)
        tip.shadowColor = .black; tip.shadowOffset = CGSize(width: 0, height: 1)
        pin(tip)
        NSLayoutConstraint.activate([
            tip.topAnchor.constraint(equalTo: buildBar.bottomAnchor, constant: 3),
            tip.centerXAnchor.constraint(equalTo: view.centerXAnchor)
        ])

        let wb = goldButton("ابدأ الموجة ⚔", action: #selector(startWave))
        wb.isHidden = true
        pin(wb)
        NSLayoutConstraint.activate([
            wb.bottomAnchor.constraint(equalTo: buildBar.topAnchor, constant: -14),
            wb.centerXAnchor.constraint(equalTo: view.centerXAnchor)
        ])
        waveButtonRef = wb
        NSLayoutConstraint.activate([
            handHelp.bottomAnchor.constraint(equalTo: wb.topAnchor, constant: -10),
            handHelp.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            handHelp.leadingAnchor.constraint(greaterThanOrEqualTo: safe.leadingAnchor, constant: 12)
        ])

        // شاشة النهاية
        endPanel.axis = .vertical; endPanel.spacing = 10; endPanel.alignment = .fill
        endPanel.isLayoutMarginsRelativeArrangement = true
        endPanel.layoutMargins = UIEdgeInsets(top: 24, left: 24, bottom: 24, right: 24)
        panelStyle(endPanel, radius: 26, border: GameViewController.gold)
        endTitle.font = .boldSystemFont(ofSize: 28); endTitle.textColor = GameViewController.gold; endTitle.textAlignment = .center
        endSub.font = .systemFont(ofSize: 16); endSub.textColor = .white; endSub.textAlignment = .center
        endPanel.addArrangedSubview(endTitle)
        endPanel.addArrangedSubview(endSub)
        endPanel.addArrangedSubview(goldButton("العب مرة ثانية 🔄", action: #selector(restart)))
        endPanel.addArrangedSubview(makeLabel("برمجة وتطوير ابراهيم الخزرجي", size: 13, color: GameViewController.goldSoft, bold: false))
        endPanel.isHidden = true
        pin(endPanel)
        NSLayoutConstraint.activate([
            endPanel.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            endPanel.leadingAnchor.constraint(equalTo: safe.leadingAnchor, constant: 28),
            endPanel.trailingAnchor.constraint(equalTo: safe.trailingAnchor, constant: -28)
        ])
    }

    private var waveButtonRef: UIButton?
    private var waveButtonView: UIButton { waveButtonRef ?? waveButton }

    @objc private func scaleChanged(_ s: UISlider) { boardScale = s.value }
    @objc private func placeTapped() { placeBoard() }
    @objc private func startWave() { game.startWave() }
    @objc private func restart() { game.restart() }

    @objc private func showAbout() {
        let vc = AboutViewController()
        vc.modalPresentationStyle = .overFullScreen
        vc.modalTransitionStyle = .crossDissolve
        present(vc, animated: true)
    }

    func coachingOverlayViewDidDeactivate(_ coachingOverlayView: ARCoachingOverlayView) {}
}

/// زر بشريط البناء: اسحبه وافلته على الساحة.
final class BuildButtonView: UIView {
    let type: BType
    var onBegin: ((CGPoint) -> Void)?
    var onMove: ((CGPoint) -> Void)?
    var onEnd: ((CGPoint, Bool) -> Void)?

    init(type: BType) {
        self.type = type
        super.init(frame: .zero)
        backgroundColor = UIColor(red: 0.11, green: 0.11, blue: 0.13, alpha: 1)
        layer.cornerRadius = 16
        layer.borderWidth = 1.5
        layer.borderColor = GameViewController.goldSoft.cgColor
        let e = UILabel(); e.text = type.emoji; e.font = .systemFont(ofSize: 28); e.textAlignment = .center
        let n = UILabel(); n.text = type.label; n.font = .boldSystemFont(ofSize: 13); n.textColor = .white; n.textAlignment = .center
        let c = UILabel(); c.text = "💰 \(type.cost)"; c.font = .boldSystemFont(ofSize: 12); c.textColor = GameViewController.gold; c.textAlignment = .center
        let st = UIStackView(arrangedSubviews: [e, n, c])
        st.axis = .vertical; st.spacing = 2
        st.isUserInteractionEnabled = false
        st.translatesAutoresizingMaskIntoConstraints = false
        addSubview(st)
        NSLayoutConstraint.activate([st.centerXAnchor.constraint(equalTo: centerXAnchor), st.centerYAnchor.constraint(equalTo: centerYAnchor)])
    }
    required init?(coder: NSCoder) { fatalError() }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let t = touches.first else { return }
        transform = CGAffineTransform(scaleX: 1.1, y: 1.1)
        onBegin?(t.location(in: self))
    }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let t = touches.first else { return }
        onMove?(t.location(in: self))
    }
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        transform = .identity
        guard let t = touches.first else { return }
        onEnd?(t.location(in: self), true)
    }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        transform = .identity
        onEnd?(touches.first?.location(in: self) ?? .zero, false)
    }
}

/// قسم "حول"
final class AboutViewController: UIViewController {
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(white: 0, alpha: 0.6)
        view.semanticContentAttribute = .forceRightToLeft
        let box = UIStackView()
        box.axis = .vertical; box.spacing = 10; box.alignment = .fill
        box.isLayoutMarginsRelativeArrangement = true
        box.layoutMargins = UIEdgeInsets(top: 22, left: 22, bottom: 18, right: 22)
        box.backgroundColor = UIColor(red: 0.055, green: 0.055, blue: 0.07, alpha: 1)
        box.layer.cornerRadius = 22
        box.layer.borderWidth = 1.5
        box.layer.borderColor = GameViewController.gold.cgColor

        func label(_ t: String, _ size: CGFloat, _ color: UIColor, bold: Bool = true) -> UILabel {
            let l = UILabel(); l.text = t; l.textColor = color; l.textAlignment = .center; l.numberOfLines = 0
            l.font = bold ? .boldSystemFont(ofSize: size) : .systemFont(ofSize: size); return l
        }
        box.addArrangedSubview(label("🏰 IBRA CASTLE AR", 22, GameViewController.gold))
        box.addArrangedSubview(label("دافع عن قلعتك بالواقع المعزز", 14, .white, bold: false))
        box.addArrangedSubview(label("برمجة وتطوير ابراهيم الخزرجي", 17, GameViewController.gold))
        for (title, url) in [("🌐 موقع تطبيقاتي", GameViewController.site),
                             ("✈️ تليكرام ibraprogram", GameViewController.telegram),
                             ("🎵 تيك توك ibraprogram", GameViewController.tiktok)] {
            let b = UIButton(type: .system)
            b.setTitle(title, for: .normal)
            b.setTitleColor(.black, for: .normal)
            b.titleLabel?.font = .boldSystemFont(ofSize: 17)
            b.backgroundColor = GameViewController.gold
            b.layer.cornerRadius = 20
            b.heightAnchor.constraint(equalToConstant: 44).isActive = true
            b.addAction(UIAction { _ in if let u = URL(string: url) { UIApplication.shared.open(u) } }, for: .touchUpInside)
            box.addArrangedSubview(b)
        }
        let close = UIButton(type: .system)
        close.setTitle("إغلاق", for: .normal)
        close.setTitleColor(.white, for: .normal)
        close.addAction(UIAction { [weak self] _ in self?.dismiss(animated: true) }, for: .touchUpInside)
        box.addArrangedSubview(close)
        box.addArrangedSubview(label("الإصدار 1.1", 12, .gray, bold: false))

        box.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(box)
        NSLayoutConstraint.activate([
            box.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            box.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 28),
            box.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -28)
        ])
    }
}
