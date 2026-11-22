# برومبتات المجسمات — IBRA CASTLE AR 🏰
لـ **Meshy** (Text to 3D) أو **Kaedim**. البرومبت بالإنكليزي لأن النتائج أدق.

## إعدادات Meshy الموصى بها
| الإعداد | القيمة |
|---|---|
| Art Style | **Cartoon** (أو Low Poly) |
| Topology | **Triangle** |
| Target Polycount | حسب كل قطعة (مكتوب تحت) |
| Texture | Base Color فقط، 1024 للقلعة و 512 للباقي — بدون PBR |
| Symmetry | On للجنود والأبراج |
| Rig + Animation | للجنود فقط: Auto-Rig (Humanoid) ثم Walk + Attack + Death |

**القاعدة الذهبية:** المشهد كله على الشاشة بنفس الوقت أقل من **10,000 مضلع** تقريباً لكل مجسم كبير، والجنود خفاف لأنهم يتكررون.

**الستايل الموحّد** — أضفه آخر كل برومبت حتى تطلع القطع متناسقة:
```
cartoon low-poly style, chunky proportions, flat shading, bright saturated colors, simple solid color blocks, no fine details, mobile game asset, clean silhouette, isolated on white background
```

---

## 1) القلعة الرئيسية — 6,000–8,000 مضلع
```
A small cute medieval castle keep, square stone base with four round corner towers topped with red cone roofs and small yellow flags, big wooden arched gate with iron studs, light grey stone blocks, crenellated walls, compact and stubby proportions, footprint square, cartoon low-poly style, chunky proportions, flat shading, bright saturated colors, simple solid color blocks, no fine details, mobile game asset, clean silhouette, isolated on white background
```
📐 القاعدة مربعة بالضبط (تغطي 3×3 مربعات). المركز (Pivot) بأسفل الوسط.

## 2) الجندي العادي (Swordsman) — 1,500–2,000 مضلع
```
A chubby cartoon medieval foot soldier, oversized round helmet with nose guard, blue tunic with simple yellow emblem, small round wooden shield on left arm, short sword in right hand, big head small body, T-pose, cartoon low-poly style, chunky proportions, flat shading, bright saturated colors, simple solid color blocks, no fine details, mobile game asset, clean silhouette, isolated on white background
```
🎬 Animations: Walking, Sword Slash, Death (Fall back).

## 3) حامل الرمح (Spearman — أسرع وأضعف) — 1,500 مضلع
```
A skinny cartoon medieval spearman, leather cap, green tunic, long wooden spear with iron tip held upright, light brown boots, big head small body, T-pose, cartoon low-poly style, chunky proportions, flat shading, bright saturated colors, simple solid color blocks, no fine details, mobile game asset, clean silhouette, isolated on white background
```
🎬 Animations: Run, Spear Thrust, Death.

## 4) الفارس الثقيل (Heavy Knight — بطيء وقوي) — 2,500 مضلع
```
A bulky cartoon medieval knight in full shiny steel armor, bucket great helm with red plume, large rectangular shield with a red cross, heavy mace, wide shoulders, short legs, T-pose, cartoon low-poly style, chunky proportions, flat shading, bright saturated colors, simple solid color blocks, no fine details, mobile game asset, clean silhouette, isolated on white background
```
🎬 Animations: Heavy Walk, Mace Smash, Death.

## 5) الزعيم (Boss — كل 5 موجات) — 4,000 مضلع
```
A giant cartoon medieval warlord ogre-like captain, horned dark iron helmet, purple cape, huge two-handed battle axe, spiked shoulder pads, big belly, angry funny face, T-pose, cartoon low-poly style, chunky proportions, flat shading, bright saturated colors, simple solid color blocks, no fine details, mobile game asset, clean silhouette, isolated on white background
```
🎬 Animations: Stomp Walk, Axe Swing, Death. (بالـ Unity كبّره 1.6 مرة)

## 6) الجدار — 300–600 مضلع
```
A single short medieval stone wall segment, one block wide, chunky grey and beige stones with a wooden top rim, slightly cracked edges, cube-like footprint, cartoon low-poly style, chunky proportions, flat shading, bright saturated colors, simple solid color blocks, no fine details, mobile game asset, clean silhouette, isolated on white background
```
📐 يملأ مربع واحد 1×1 بالضبط.

## 7) برج السهام — 1,500–2,500 مضلع
**جزئين منفصلين** حتى الرأس يدور:

القاعدة:
```
A small medieval wooden archer tower base, square stone foundation with four wooden log legs and a wooden platform on top, ladder on one side, no roof, cartoon low-poly style, chunky proportions, flat shading, bright saturated colors, simple solid color blocks, no fine details, mobile game asset, clean silhouette, isolated on white background
```
الرأس (يدور):
```
A cartoon crossbow ballista turret head mounted on a round wooden swivel plate, small red pointed roof canopy above, loaded arrow facing forward, cartoon low-poly style, chunky proportions, flat shading, bright saturated colors, simple solid color blocks, no fine details, mobile game asset, clean silhouette, isolated on white background
```

## 8) فخ المسامير — 400–800 مضلع
**جزئين:** القاعدة ثابتة + المسامير تتحرك لفوق وجوه.
```
A flat square wooden trapdoor frame with dark iron bolts, flush with the ground, cartoon low-poly style, chunky proportions, flat shading, bright saturated colors, simple solid color blocks, no fine details, mobile game asset, clean silhouette, isolated on white background
```
```
A cluster of nine shiny silver conical metal spikes on a thin square plate, cartoon low-poly style, chunky proportions, flat shading, bright saturated colors, simple solid color blocks, no fine details, mobile game asset, clean silhouette, isolated on white background
```

## 9) السهم — أقل من 100 مضلع
```
A single cartoon arrow, wooden shaft, grey triangular tip, red feather fletching, very simple, cartoon low-poly style, flat shading, bright colors, mobile game asset, isolated on white background
```

## 10) أرضية الساحة — 500–1,500 مضلع
```
A square floating island game board, flat top covered in bright green grass with a few tiny flowers, dirt and rock cliff sides underneath tapering down, cartoon low-poly style, chunky proportions, flat shading, bright saturated colors, simple solid color blocks, mobile game asset, isolated on white background
```
📐 السطح العلوي مستوي بالكامل (حتى البناء يكون مضبوط).

## 11) زينة (اختياري) — 100–300 مضلع لكل وحدة
```
A set of cartoon low-poly decorations: a round green tree, a pine tree, a small grey boulder, a bush with red berries, a wooden barrel, flat shading, bright saturated colors, mobile game asset, isolated on white background
```

## 12) عملة الذهب (مؤثر القتل) — 100 مضلع
```
A shiny cartoon gold coin with a small crown embossed, thick rim, cartoon low-poly style, flat shading, bright yellow, mobile game asset, isolated on white background
```

---

## Kaedim
Kaedim يشتغل من **صورة** مو نص: ولّد صورة مرجعية بأي مولّد صور بنفس البرومبتات فوق (أضف `front view, orthographic, full body`)، ارفعها لـ Kaedim، واختر:
- Quality: **Game-Ready / Low Poly**
- Polycount: نفس الأرقام فوق
- Texture: Stylized / Hand-painted

## بعد التحميل (Unity)
1. صدّر **FBX** (أو GLB مع glTFast).
2. Scale Factor بحيث **مربع واحد = 1 وحدة**: الجندي طوله ≈ 0.8، الجدار ≈ 0.9، البرج ≈ 1.8، القلعة عرضها 3.
3. Mesh Compression: Medium، Read/Write: Off، Generate Lightmap UVs: Off.
4. الجنود: Rig = Humanoid، وفعّل **GPU Skinning** وخلّي **Optimize Game Objects** On.
5. الخامات: URP **Simple Lit** أو Unlit Toon — بدون Normal Maps.
