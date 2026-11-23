import ARKit
import UIKit
import Vision

/// يتعرف على الإيد من صورة كاميرا ARKit باستخدام Vision (مدمج بالآيفون، بدون إنترنت).
/// النتيجة: 21 نقطة بترتيب MediaPipe، بإحداثيات الصورة الأصلية (0..1، الأصل فوق-يسار).
final class HandTracker {
    private let queue = DispatchQueue(label: "ibra.hand", qos: .userInteractive)
    private var busy = false
    private let request: VNDetectHumanHandPoseRequest = {
        let r = VNDetectHumanHandPoseRequest()
        r.maximumHandCount = 1
        return r
    }()

    /// يُستدعى على الخيط الرئيسي.
    var onResult: (([CGPoint]?) -> Void)?

    static let joints: [VNHumanHandPoseObservation.JointName] = [
        .wrist,
        .thumbCMC, .thumbMP, .thumbIP, .thumbTip,
        .indexMCP, .indexPIP, .indexDIP, .indexTip,
        .middleMCP, .middlePIP, .middleDIP, .middleTip,
        .ringMCP, .ringPIP, .ringDIP, .ringTip,
        .littleMCP, .littlePIP, .littleDIP, .littleTip
    ]

    func submit(_ frame: ARFrame) {
        guard !busy else { return }
        busy = true
        let buffer = frame.capturedImage
        queue.async { [weak self] in
            guard let self = self else { return }
            var pts: [CGPoint]? = nil
            // .right = صورة الكاميرا الخلفية معدولة للوضع العمودي
            let handler = VNImageRequestHandler(cvPixelBuffer: buffer, orientation: .right, options: [:])
            do {
                try handler.perform([self.request])
                if let obs = self.request.results?.first { pts = HandTracker.extract(obs) }
            } catch {}
            DispatchQueue.main.async {
                self.busy = false
                self.onResult?(pts)
            }
        }
    }

    private static func extract(_ obs: VNHumanHandPoseObservation) -> [CGPoint]? {
        guard let all = try? obs.recognizedPoints(.all) else { return nil }
        var out: [CGPoint] = []
        out.reserveCapacity(21)
        for j in joints {
            guard let p = all[j], p.confidence > 0.25 else { return nil }
            // Vision: صورة معدولة، الأصل تحت-يسار ← الصورة الأصلية (أفقية)، الأصل فوق-يسار
            out.append(CGPoint(x: 1 - p.location.y, y: 1 - p.location.x))
        }
        return out
    }
}

protocol GestureListener: AnyObject {
    func onCursor(_ p: CGPoint, pinching: Bool)
    func onHit(_ p: CGPoint)
    func onSelect(_ t: BType)
    func onPinchStart(_ p: CGPoint)
    func onPinchMove(_ p: CGPoint)
    func onPinchEnd(_ p: CGPoint, place: Bool)
    func onThumbsUp()
}

/// ☝️ ✌️ 🤟 = اختيار جدار/برج/فخ • 🤏 كمشة = مسك وتحريك وإفلات • 👋 حركة سريعة = ضربة • 👍 = الموجة
final class GestureEngine {
    private(set) var pinching = false
    private(set) var fingers = 0
    private var last = CGPoint.zero
    private var lastT: CFTimeInterval = 0
    private var speed: CGFloat = 0
    private var lastHitT: CFTimeInterval = 0
    private var candidate = 0
    private var candidateSince: CFTimeInterval = 0
    private var selected = 0
    private var thumbSince: CFTimeInterval = 0
    private var thumbFired = false
    private var pinchP = CGPoint.zero

    private func d(_ p: [CGPoint], _ a: Int, _ b: Int) -> CGFloat { hypot(p[a].x - p[b].x, p[a].y - p[b].y) }

    func update(_ p: [CGPoint], t: CFTimeInterval, viewH: CGFloat, _ l: GestureListener) {
        let hs = d(p, 0, 9)
        guard hs > 8 else { return }
        func ext(_ tip: Int, _ pip: Int) -> Bool { d(p, 0, tip) > d(p, 0, pip) * 1.12 }
        fingers = [ext(8, 6), ext(12, 10), ext(16, 14), ext(20, 18)].filter { $0 }.count
        let thumbExt = d(p, 4, 17) > d(p, 3, 17) * 1.08

        let pinchD = d(p, 4, 8) / hs
        let mid = CGPoint(x: (p[4].x + p[8].x) / 2, y: (p[4].y + p[8].y) / 2)
        if !pinching && pinchD < 0.3 {
            pinching = true; pinchP = mid
            l.onPinchStart(mid)
        } else if pinching && pinchD > 0.55 {
            pinching = false
            l.onPinchEnd(pinchP, place: true)
        } else if pinching {
            pinchP.x += (mid.x - pinchP.x) * 0.5
            pinchP.y += (mid.y - pinchP.y) * 0.5
            l.onPinchMove(pinchP)
        }

        let tip = p[8]
        if lastT != 0 {
            let dt = max(t - lastT, 0.001)
            let v = hypot(tip.x - last.x, tip.y - last.y) / CGFloat(dt)
            speed = speed * 0.4 + v * 0.6
        }
        last = tip; lastT = t

        l.onCursor(pinching ? pinchP : tip, pinching: pinching)

        if !pinching && speed > viewH * 1.5 && t - lastHitT > 0.26 {
            lastHitT = t
            l.onHit(tip)
        }

        let calm = speed < viewH * 0.6
        if !pinching && calm && (1...3).contains(fingers) && pinchD > 0.45 {
            if fingers != candidate { candidate = fingers; candidateSince = t }
            else if t - candidateSince > 0.45 && selected != fingers {
                selected = fingers
                l.onSelect(fingers == 1 ? .wall : fingers == 2 ? .tower : .spike)
            }
        } else { candidate = 0 }

        let thumbsUp = fingers == 0 && thumbExt && (p[0].y - p[4].y) > hs * 0.8 && !pinching
        if thumbsUp {
            if thumbSince == 0 { thumbSince = t }
            if !thumbFired && t - thumbSince > 0.7 { thumbFired = true; l.onThumbsUp() }
        } else { thumbSince = 0; thumbFired = false }
    }

    func lost(_ l: GestureListener) {
        if pinching { pinching = false; l.onPinchEnd(pinchP, place: false) }
        lastT = 0; speed = 0; candidate = 0; thumbSince = 0; thumbFired = false
    }
}

/// يرسم هيكل الإيد (ذهبي) فوق الكاميرا.
final class HandOverlayView: UIView {
    var points: [CGPoint]? { didSet { setNeedsDisplay() } }
    var pinching = false
    private let bones: [(Int, Int)] = [(0, 1), (1, 2), (2, 3), (3, 4), (0, 5), (5, 6), (6, 7), (7, 8),
                                       (5, 9), (9, 10), (10, 11), (11, 12), (9, 13), (13, 14), (14, 15), (15, 16),
                                       (13, 17), (0, 17), (17, 18), (18, 19), (19, 20)]

    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = false
        backgroundColor = .clear
        isUserInteractionEnabled = false
        contentMode = .redraw
    }
    required init?(coder: NSCoder) { fatalError() }

    override func draw(_ rect: CGRect) {
        guard let p = points, p.count == 21, let c = UIGraphicsGetCurrentContext() else { return }
        c.setLineCap(.round)
        c.setLineWidth(3)
        c.setStrokeColor(GameViewController.gold.withAlphaComponent(0.8).cgColor)
        for (a, b) in bones { c.move(to: p[a]); c.addLine(to: p[b]) }
        c.strokePath()
        c.setFillColor(UIColor.white.cgColor)
        for q in p { c.fillEllipse(in: CGRect(x: q.x - 3, y: q.y - 3, width: 6, height: 6)) }
        let tipColor = pinching ? UIColor(red: 0.32, green: 0.81, blue: 0.4, alpha: 1) : GameViewController.gold
        c.setFillColor(tipColor.cgColor)
        c.fillEllipse(in: CGRect(x: p[8].x - 8, y: p[8].y - 8, width: 16, height: 16))
        c.fillEllipse(in: CGRect(x: p[4].x - 6, y: p[4].y - 6, width: 12, height: 12))
    }
}
