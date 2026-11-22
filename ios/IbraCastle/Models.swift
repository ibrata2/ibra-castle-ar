import SceneKit
import UIKit

/// يبني المجسمات من أشكال SceneKit الأساسية — ستايل كرتوني Low-Poly.
/// المقياس: مربع واحد = 1 وحدة، الأمام = +Z.
enum Axis { case x, y, z }

final class ModelBuilder {
    let node = SCNNode()
    private static var materials: [Int: SCNMaterial] = [:]

    static func mat(_ c: Int) -> SCNMaterial {
        if let m = materials[c] { return m }
        let m = SCNMaterial()
        m.diffuse.contents = UIColor(red: CGFloat((c >> 16) & 255) / 255, green: CGFloat((c >> 8) & 255) / 255,
                                     blue: CGFloat(c & 255) / 255, alpha: 1)
        m.lightingModel = .lambert
        m.isDoubleSided = false
        materials[c] = m
        return m
    }

    private func add(_ g: SCNGeometry, _ p: SIMD3<Float>, axis: Axis = .y, sy: Float = 1) {
        let n = SCNNode(geometry: g)
        n.simdPosition = p
        switch axis {
        case .y: break
        case .z: n.simdEulerAngles = SIMD3(Float.pi / 2, 0, 0)
        case .x: n.simdEulerAngles = SIMD3(0, 0, -Float.pi / 2)
        }
        if sy != 1 { n.simdScale = SIMD3(1, sy, 1) }
        node.addChildNode(n)
    }

    func box(_ cx: Float, _ cy: Float, _ cz: Float, _ sx: Float, _ sy: Float, _ sz: Float, _ color: Int, top: Int? = nil) {
        let b = SCNBox(width: CGFloat(sx), height: CGFloat(sy), length: CGFloat(sz), chamferRadius: 0)
        let m = ModelBuilder.mat(color)
        let t = ModelBuilder.mat(top ?? color)
        b.materials = [m, m, m, m, t, m] // front right back left top bottom
        add(b, SIMD3(cx, cy, cz))
    }

    private func offset(_ base: SIMD3<Float>, _ axis: Axis, _ d: Float) -> SIMD3<Float> {
        switch axis {
        case .y: return base + SIMD3(0, d, 0)
        case .z: return base + SIMD3(0, 0, d)
        case .x: return base + SIMD3(d, 0, 0)
        }
    }

    func cylinder(_ base: SIMD3<Float>, _ r: Float, _ h: Float, _ color: Int, seg: Int = 8, axis: Axis = .y, top: Int? = nil, r2: Float? = nil) {
        let g: SCNGeometry
        if let r2 = r2, r2 != r {
            let c = SCNCone(topRadius: CGFloat(r2), bottomRadius: CGFloat(r), height: CGFloat(h))
            c.radialSegmentCount = seg
            g = c
        } else {
            let c = SCNCylinder(radius: CGFloat(r), height: CGFloat(h))
            c.radialSegmentCount = seg
            g = c
        }
        let m = ModelBuilder.mat(color)
        g.materials = [m, ModelBuilder.mat(top ?? color), m]
        add(g, offset(base, axis, h / 2), axis: axis)
    }

    func cone(_ base: SIMD3<Float>, _ r: Float, _ h: Float, _ color: Int, seg: Int = 8, axis: Axis = .y) {
        let c = SCNCone(topRadius: 0, bottomRadius: CGFloat(r), height: CGFloat(h))
        c.radialSegmentCount = seg
        c.firstMaterial = ModelBuilder.mat(color)
        add(c, offset(base, axis, h / 2), axis: axis)
    }

    func sphere(_ c: SIMD3<Float>, _ r: Float, _ color: Int, seg: Int = 8, sy: Float = 1) {
        let s = SCNSphere(radius: CGFloat(r))
        s.segmentCount = max(seg, 6)
        s.firstMaterial = ModelBuilder.mat(color)
        add(s, c, sy: sy)
    }

    /// يدمج كل الأشكال بمجسم واحد (أسرع بالرسم).
    func build() -> SCNNode { node.flattenedClone() }
}

enum Models {
    static let GRASS = 0x7ED957, GRASS2 = 0x6CC64A, SAND = 0xE9C98A
    static let DIRT = 0xA9744A, DIRT_DARK = 0x7D5233
    static let STONE = 0xBFC4CC, STONE_DARK = 0x8E949E, STONE_LIGHT = 0xDADFE6
    static let ROOF = 0xE84A4A, WOOD = 0xA06D3D, WOOD_DARK = 0x6B4423
    static let GOLD = 0xFFC93C, SKIN = 0xF7CBA3, STEEL = 0xD9E0E8
    static let BLUE = 0x3D7BFF, GREEN = 0x37B24D, RED = 0xE03131
    static let PURPLE = 0x8E44DD, DARK = 0x2D2D36, WHITE = 0xFFFFFF

    static var ground: SCNNode!, castle: SCNNode!, wall: SCNNode!
    static var towerBase: SCNNode!, towerHead: SCNNode!, spikeBase: SCNNode!, spikes: SCNNode!
    static var arrow: SCNNode!, swordsman: SCNNode!, spearman: SCNNode!, knight: SCNNode!, boss: SCNNode!
    static var reticle: SCNNode!, puff: SCNNode!, spark: SCNNode!, coin: SCNNode!

    static func v(_ x: Float, _ y: Float, _ z: Float) -> SIMD3<Float> { SIMD3(x, y, z) }

    static func build(w: Int, h: Int) {
        ground = buildGround(w, h)
        castle = buildCastle()
        wall = buildWall()
        towerBase = buildTowerBase()
        towerHead = buildTowerHead()
        spikeBase = { let b = ModelBuilder()
            b.box(0, 0.04, 0, 0.9, 0.08, 0.9, WOOD_DARK, top: WOOD)
            for i in -1...1 { b.box(Float(i) * 0.3, 0.085, 0, 0.05, 0.01, 0.8, DARK) }
            return b.build() }()
        spikes = { let b = ModelBuilder()
            b.box(0, 0.02, 0, 0.78, 0.04, 0.78, STONE_DARK)
            for i in -1...1 { for j in -1...1 { b.cone(v(Float(i) * 0.26, 0.04, Float(j) * 0.26), 0.08, 0.32, STEEL, seg: 5) } }
            return b.build() }()
        arrow = { let b = ModelBuilder()
            b.box(0, 0, 0, 0.04, 0.04, 0.5, WOOD)
            b.cone(v(0, 0, 0.25), 0.05, 0.13, STEEL, seg: 4, axis: .z)
            b.box(0, 0.035, -0.22, 0.012, 0.06, 0.1, RED)
            b.box(0.035, 0, -0.22, 0.06, 0.012, 0.1, RED)
            return b.build() }()
        swordsman = buildSoldier(tunic: BLUE, helmet: STEEL, weapon: 0)
        spearman = buildSoldier(tunic: GREEN, helmet: WOOD, weapon: 1)
        knight = buildSoldier(tunic: STEEL, helmet: STEEL, weapon: 2, plume: RED, bulky: true)
        boss = buildBoss()
        reticle = { let b = ModelBuilder()
            let t: Float = 0.012, hh: Float = 0.004
            b.box(0, hh, 0.5, 1, hh, t, GOLD); b.box(0, hh, -0.5, 1, hh, t, GOLD)
            b.box(0.5, hh, 0, t, hh, 1, GOLD); b.box(-0.5, hh, 0, t, hh, 1, GOLD)
            for sx: Float in [-1, 1] { for sz: Float in [-1, 1] {
                b.box(sx * 0.46, hh * 2, sz * 0.5, 0.08, hh, t * 2.5, WHITE)
                b.box(sx * 0.5, hh * 2, sz * 0.46, t * 2.5, hh, 0.08, WHITE)
            } }
            return b.build() }()
        puff = { let b = ModelBuilder(); b.sphere(.zero, 0.5, 0xF1F3F5, seg: 6); return b.build() }()
        spark = { let b = ModelBuilder(); b.sphere(.zero, 0.5, 0xFFE066, seg: 6); return b.build() }()
        coin = { let b = ModelBuilder(); b.cylinder(v(-0.03, 0, 0), 0.14, 0.06, GOLD, seg: 10, axis: .x, top: 0xFFE38A); return b.build() }()
    }

    static func buildGround(_ w: Int, _ h: Int) -> SCNNode {
        let b = ModelBuilder()
        let ox = -Float(w) / 2, oz = -Float(h) / 2
        for x in 0..<w { for y in 0..<h {
            let edge = x == 0 || y == 0 || x == w - 1 || y == h - 1
            let c = edge ? SAND : ((x + y) % 2 == 0 ? GRASS : GRASS2)
            b.box(ox + Float(x) + 0.5, -0.06, oz + Float(y) + 0.5, 1, 0.12, 1, c)
        } }
        b.box(0, -0.32, 0, Float(w), 0.4, Float(h), DIRT)
        b.box(0, -0.7, 0, Float(w) - 1.2, 0.4, Float(h) - 1.2, DIRT_DARK)
        for (sx, sz) in [(1, 1), (-1, 1), (1, -1), (-1, -1)] {
            let cx = Float(sx) * (Float(w) / 2 - 0.5), cz = Float(sz) * (Float(h) / 2 - 0.5)
            b.cylinder(v(cx, 0, cz), 0.07, 0.3, WOOD_DARK, seg: 5)
            b.cone(v(cx, 0.25, cz), 0.3, 0.55, 0x2F9E44, seg: 6)
            b.cone(v(cx, 0.55, cz), 0.22, 0.4, 0x40C057, seg: 6)
        }
        return b.build()
    }

    static func buildCastle() -> SCNNode {
        let b = ModelBuilder()
        b.box(0, 0.45, 0, 2.5, 0.9, 2.5, STONE, top: STONE_LIGHT)
        for i in -2...2 {
            let p = Float(i) * 0.55
            b.box(p, 0.98, 1.2, 0.28, 0.16, 0.16, STONE_LIGHT)
            b.box(p, 0.98, -1.2, 0.28, 0.16, 0.16, STONE_LIGHT)
            b.box(1.2, 0.98, p, 0.16, 0.16, 0.28, STONE_LIGHT)
            b.box(-1.2, 0.98, p, 0.16, 0.16, 0.28, STONE_LIGHT)
        }
        for (sx, sz) in [(1, 1), (-1, 1), (1, -1), (-1, -1)] {
            let base = v(Float(sx) * 1.2, 0, Float(sz) * 1.2)
            b.cylinder(base, 0.42, 1.6, STONE, seg: 8, top: STONE_LIGHT)
            b.cylinder(base + v(0, 1.6, 0), 0.5, 0.12, STONE_DARK, seg: 8)
            b.cone(base + v(0, 1.72, 0), 0.52, 0.75, ROOF, seg: 8)
            b.box(base.x, 2.6, base.z, 0.02, 0.3, 0.02, WOOD_DARK)
            b.box(base.x + 0.1, 2.68, base.z, 0.2, 0.12, 0.01, GOLD)
        }
        b.box(0, 1.2, 0, 1.25, 1.1, 1.25, STONE, top: STONE_LIGHT)
        b.cone(v(0, 1.75, 0), 0.95, 0.9, ROOF, seg: 4)
        b.box(0, 2.85, 0, 0.03, 0.4, 0.03, WOOD_DARK)
        b.box(0.15, 2.95, 0, 0.3, 0.18, 0.01, GOLD)
        b.box(0, 0.32, 1.26, 0.7, 0.64, 0.04, WOOD_DARK)
        b.cylinder(v(0, 0.64, 1.24), 0.35, 0.06, WOOD_DARK, seg: 8, axis: .z)
        b.box(0, 0.32, 1.29, 0.04, 0.6, 0.02, DARK)
        for sx: Float in [-1, 1] { b.box(sx * 0.3, 1.35, 0.64, 0.16, 0.26, 0.02, DARK) }
        return b.build()
    }

    static func buildWall() -> SCNNode {
        let b = ModelBuilder()
        b.box(0, 0.36, 0, 0.92, 0.72, 0.92, STONE, top: STONE_LIGHT)
        b.box(0, 0.76, 0, 0.96, 0.08, 0.96, WOOD)
        for (x, y) in [(Float(-0.25), Float(0.2)), (0.2, 0.45), (-0.1, 0.58), (0.3, 0.15)] {
            for z: Float in [0.47, -0.47] { b.box(x, y, z, 0.28, 0.14, 0.02, STONE_DARK) }
        }
        return b.build()
    }

    static func buildTowerBase() -> SCNNode {
        let b = ModelBuilder()
        b.box(0, 0.12, 0, 0.9, 0.24, 0.9, STONE_DARK, top: STONE)
        for (sx, sz) in [(1, 1), (-1, 1), (1, -1), (-1, -1)] {
            b.box(Float(sx) * 0.3, 0.62, Float(sz) * 0.3, 0.12, 0.8, 0.12, WOOD)
        }
        b.box(0, 0.6, 0.3, 0.6, 0.06, 0.05, WOOD_DARK)
        b.box(0, 0.6, -0.3, 0.6, 0.06, 0.05, WOOD_DARK)
        b.box(0, 1.05, 0, 0.86, 0.1, 0.86, WOOD_DARK, top: WOOD)
        for i in -2...2 {
            b.box(Float(i) * 0.2, 1.17, 0.41, 0.06, 0.16, 0.04, WOOD)
            b.box(Float(i) * 0.2, 1.17, -0.41, 0.06, 0.16, 0.04, WOOD)
        }
        return b.build()
    }

    static func buildTowerHead() -> SCNNode {
        let b = ModelBuilder()
        b.cylinder(.zero, 0.28, 0.08, WOOD_DARK, seg: 8)
        b.box(0, 0.16, 0.05, 0.14, 0.12, 0.5, WOOD)
        b.box(0, 0.2, 0.22, 0.6, 0.05, 0.05, WOOD_DARK)
        b.box(0, 0.24, 0.12, 0.03, 0.03, 0.45, STEEL)
        for sx: Float in [-1, 1] { b.box(sx * 0.3, 0.35, -0.3, 0.04, 0.5, 0.04, WOOD) }
        b.cone(v(0, 0.58, 0), 0.55, 0.4, ROOF, seg: 4)
        return b.build()
    }

    static func buildSoldier(tunic: Int, helmet: Int, weapon: Int, plume: Int? = nil, bulky: Bool = false) -> SCNNode {
        let b = ModelBuilder()
        let w: Float = bulky ? 1.25 : 1
        b.box(-0.09 * w, 0.1, 0, 0.1 * w, 0.2, 0.12, DARK)
        b.box(0.09 * w, 0.1, 0, 0.1 * w, 0.2, 0.12, DARK)
        b.box(0, 0.33, 0, 0.34 * w, 0.28, 0.24 * w, tunic)
        b.box(0, 0.22, 0, 0.36 * w, 0.05, 0.26 * w, WOOD_DARK)
        b.box(0, 0.36, 0.125 * w, 0.1, 0.1, 0.01, GOLD)
        b.sphere(v(0, 0.6, 0), 0.17, SKIN)
        b.box(-0.055, 0.62, 0.155, 0.035, 0.05, 0.02, DARK)
        b.box(0.055, 0.62, 0.155, 0.035, 0.05, 0.02, DARK)
        b.sphere(v(0, 0.66, 0), 0.19, helmet, sy: 0.75)
        b.box(0, 0.6, 0.17, 0.04, 0.1, 0.03, helmet)
        if let p = plume { b.box(0, 0.86, -0.02, 0.05, 0.14, 0.22, p) }
        b.box(-0.22 * w, 0.33, 0, 0.08, 0.22, 0.08, tunic)
        b.box(0.22 * w, 0.33, 0, 0.08, 0.22, 0.08, tunic)
        switch weapon {
        case 0:
            b.box(0.24 * w, 0.42, 0.14, 0.03, 0.03, 0.34, STEEL)
            b.box(0.24 * w, 0.42, 0, 0.12, 0.03, 0.03, WOOD_DARK)
            b.cylinder(v(-0.27 * w, 0.33, 0), 0.13, 0.04, WOOD, seg: 8, axis: .x, top: GOLD)
        case 1:
            b.cylinder(v(0.25, 0, 0.06), 0.02, 0.95, WOOD, seg: 5)
            b.cone(v(0.25, 0.95, 0.06), 0.045, 0.14, STEEL, seg: 4)
        default:
            b.cylinder(v(0.3, 0.2, 0.12), 0.025, 0.4, WOOD_DARK, seg: 5)
            b.sphere(v(0.3, 0.62, 0.12), 0.07, STEEL, seg: 6)
            b.box(-0.32, 0.36, 0, 0.04, 0.38, 0.3, STEEL)
            b.box(-0.345, 0.36, 0, 0.01, 0.3, 0.06, RED)
            b.box(-0.345, 0.4, 0, 0.01, 0.06, 0.22, RED)
        }
        return b.build()
    }

    static func buildBoss() -> SCNNode {
        let b = ModelBuilder()
        b.box(-0.14, 0.13, 0, 0.16, 0.26, 0.16, DARK)
        b.box(0.14, 0.13, 0, 0.16, 0.26, 0.16, DARK)
        b.sphere(v(0, 0.48, 0), 0.3, PURPLE)
        b.box(0, 0.32, 0, 0.5, 0.06, 0.42, WOOD_DARK)
        b.sphere(v(0, 0.86, 0), 0.2, 0x9BD17A)
        b.box(-0.07, 0.88, 0.18, 0.06, 0.04, 0.02, RED)
        b.box(0.07, 0.88, 0.18, 0.06, 0.04, 0.02, RED)
        b.sphere(v(0, 0.95, 0), 0.22, DARK, sy: 0.6)
        b.cone(v(-0.18, 1.0, 0), 0.06, 0.25, 0xF8F0E3, seg: 5)
        b.cone(v(0.18, 1.0, 0), 0.06, 0.25, 0xF8F0E3, seg: 5)
        for sx: Float in [-1, 1] {
            b.sphere(v(sx * 0.3, 0.66, 0), 0.11, STONE_DARK, seg: 6)
            b.cone(v(sx * 0.33, 0.74, 0), 0.04, 0.12, STEEL, seg: 4)
            b.box(sx * 0.34, 0.46, 0.02, 0.11, 0.3, 0.11, 0x9BD17A)
        }
        b.box(-0.2, 0.62, -0.2, 0.5, 0.6, 0.04, 0x6741D9)
        b.cylinder(v(0.36, 0.3, 0.12), 0.03, 0.75, WOOD_DARK, seg: 5)
        b.box(0.36, 1.0, 0.24, 0.04, 0.26, 0.22, STEEL)
        return b.build()
    }

    /// شريط صحة: خلفية + تعبئة تتمدد من اليسار.
    static func makeBar() -> (SCNNode, SCNNode) {
        let bg = SCNNode(geometry: { let g = SCNBox(width: 0.66, height: 0.1, length: 0.02, chamferRadius: 0); g.firstMaterial = unlit(0x2D2D36); return g }())
        let fillHolder = SCNNode()
        fillHolder.simdPosition = SIMD3(-0.32, 0, 0)
        let fill = SCNNode(geometry: { let g = SCNBox(width: 1, height: 0.07, length: 0.03, chamferRadius: 0); g.firstMaterial = unlit(0x51CF66); return g }())
        fill.simdPosition = SIMD3(0.5, 0, 0)
        fillHolder.addChildNode(fill)
        fillHolder.simdScale = SIMD3(0.64, 1, 1)
        bg.addChildNode(fillHolder)
        let c = SCNBillboardConstraint()
        c.freeAxes = .Y
        bg.constraints = [c]
        return (bg, fillHolder)
    }

    static func unlit(_ c: Int) -> SCNMaterial {
        let m = SCNMaterial()
        m.diffuse.contents = UIColor(red: CGFloat((c >> 16) & 255) / 255, green: CGFloat((c >> 8) & 255) / 255,
                                     blue: CGFloat(c & 255) / 255, alpha: 1)
        m.lightingModel = .constant
        return m
    }
}
