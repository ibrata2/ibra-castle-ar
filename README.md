# IBRA CASTLE AR 🏰
لعبة دفاع عن القلعة بالواقع المعزز للموبايل (Android + iPhone) — Unity + AR Foundation.

ضع قلعتك على الطاولة أو الأرض، ابنِ جدران وأبراج سهام وفخاخ مسامير، والمس الجنود حتى تضربهم قبل ما يوصلون للقلعة.

**برمجة وتطوير ابراهيم الخزرجي** — [ibraprogram.netlify.app](https://ibraprogram.netlify.app/) · [Telegram](https://t.me/ibraprogram) · TikTok: ibraprogram

---

## المميزات
- وضع القلعة على أي سطح مستوي (AR Plane Detection + ARAnchor) مع سلايدر حجم الساحة
- ذكاء أعداء بـ **A\*** على شبكة 13×13: يلفون حول الجدران، وإذا سدّيت كل الطرق يكسرونها
- 3 دفاعات بالسحب والإفلات: 🧱 جدار — 🏹 برج سهام — 📌 فخ مسامير
- الضرب باللمس على الجنود
- 10 موجات متصاعدة + زعيم كل 5 موجات + ذهب على كل قتلة ومكافأة نهاية الموجة
- واجهة: عداد الموجة، الذهب، الأعداء المتبقين، صحة القلعة، شاشة خسارة/فوز، قسم "حول"
- مُحسّنة للموبايل: Object Pooling، بدون Physics للحركة، 60fps

## الملفات
```
Assets/IbraCastle/Scripts/
├── Core/       GameManager · Health · HealthBar · PointerInput · SimplePool · AutoDespawn · HitFeedback
├── AR/         ARCastlePlacer
├── Building/   GridManager (شبكة + A*) · Building · BuildManager (السحب للبناء)
├── Defenses/   ArrowTower · SpikeTrap
├── Enemies/    EnemyAI · EnemySpawner
├── Combat/     TapShooter · Projectile
└── UI/         UIManager · BuildButton
MODEL_PROMPTS_AR.md   ← برومبتات Meshy / Kaedim لكل المجسمات
```

---

## الإعداد خطوة بخطوة

### 1) المشروع والحزم
- **Unity 6 LTS** — قالب **Universal 3D (URP)** أو قالب **AR Mobile** (أسهل، يجيب كل شي جاهز)
- Package Manager:
  - `AR Foundation` 6.x
  - `Google ARCore XR Plugin` (أندرويد) + `Apple ARKit XR Plugin` (آيفون)
  - `Input System` (أو خلّي Active Input Handling = Both)
  - `TextMeshPro`
  - للعربي: **RTLTMPro** — `https://github.com/pnarimani/RTLTMPro.git` (Add package from git URL)
- انسخ مجلد `Assets/IbraCastle` لمشروعك.

### 2) Project Settings
- **XR Plug-in Management**: فعّل ARCore (Android) و ARKit (iOS)
- **Android**: Minimum API 24، Scripting Backend = IL2CPP، Target = ARM64
- **iOS**: Camera Usage Description = `اللعبة تحتاج الكاميرا للواقع المعزز`، Target 13+
- **Tags & Layers**: أضف طبقة `Enemy`

### 3) المشهد
```
AR Session
XR Origin (AR)               ← فيه ARRaycastManager + ARPlaneManager (+ Plane Prefab)
 └ Camera Offset / Main Camera
Reticle                      ← Quad أو حلقة 1×1 شفافة (تنمط لحجم الساحة)
Systems                      ← GameManager · BuildManager · EnemySpawner · TapShooter · ARCastlePlacer
Canvas (Screen Space)        ← UIManager + اللوحات
EventSystem                  ← (Input System UI Input Module)
```

### 4) بريفاب الساحة `Board` (مهم)
```
Board            ← GridManager هنا على الجذر (Width 13، Height 13، Cell 1، Castle Size 3)
 ├ Ground        ← مجسم الأرضية، حجمه 13×13، سطحه على Y=0
 └ Castle        ← بالمركز (0,0,0): Health (500) + HitFeedback + HealthBar (اختياري)
```
اسحب `Castle` لحقل **Castle** بالـ GridManager. شغّل الـ Gizmos تشوف الشبكة: أصفر = القلعة، أحمر = حافة الظهور، أخضر = للبناء.

> **وحدة القياس:** مربع واحد = 1 وحدة. كل شي يتصمم بهالمقياس، والـ AR يصغّر الساحة كلها مرة وحدة (Board Scale = 0.06 ≈ 78 سم).

### 5) بريفابات الدفاعات
| البريفاب | المكوّنات | القيم المقترحة |
|---|---|---|
| **Wall** | Building (Walkable ✗، Path Cost 25) + Health 150 + HitFeedback + BoxCollider | الكلفة 10 |
| **ArrowTower** | Building (Walkable ✗، Path Cost 40) + Health 120 + ArrowTower + HitFeedback | الكلفة 50 — Range 3.5، Rate 1.2، Damage 10. اربط Rotating Head (الرأس) و Fire Point (طرف القوس) |
| **SpikeTrap** | Building (Walkable ✓، Path Cost 1) + SpikeTrap | الكلفة 30 — Damage 8، Interval 1، Uses 40. اربط Spikes (جزء المسامير) |
| **Arrow** | Projectile | Speed 9 |

بـ **BuildManager** أضف 3 عناصر بـ Buildings (الاسم، البريفاب، الكلفة، الأيقونة) بنفس الترتيب: 0 جدار، 1 برج، 2 فخ.
**Ghost Material**: خامة URP Unlit، Surface = Transparent.

### 6) بريفابات الأعداء
| النوع | Health | Speed | Damage | Gold | Unlock | Weight | Budget |
|---|---|---|---|---|---|---|---|
| Swordsman | 30 | 1.2 | 8 | 5 | 1 | 10 | 1 |
| Spearman | 20 | 1.8 | 5 | 4 | 2 | 7 | 1 |
| Heavy Knight | 90 | 0.7 | 20 | 12 | 4 | 4 | 3 |
| Boss ✔isBoss | 400 | 0.6 | 40 | 60 | — | — | كل 5 موجات |

كل عدو: **EnemyAI + Health + HitFeedback + CapsuleCollider** على طبقة **Enemy** + HealthBar صغير فوق الراس.
- **Visual**: الابن اللي فيه المجسم (للهزّة الكرتونية إذا ماكو Animator)
- **Animator** (اختياري): Float `Speed` (0 وقوف / 1 مشي)، Triggers `Attack` و `Die`

وبـ **TapShooter**: Enemy Mask = Enemy.

### 7) الواجهة
- **Placement Panel**: نص التعليمات + زر "ضع القلعة" + سلايدر الحجم
- **HUD**: ذهب، الموجة، الأعداء، شريط صحة القلعة (Image نوعها Filled)، زر "ابدأ الموجة"، Toast
- **Build Bar**: 3 أزرار عليها `BuildButton` (Index 0/1/2) + CanvasGroup
- **Game Over / Victory**: زر "العب مرة ثانية" ← `UIManager.Restart`
- **About (حول)**: النص "برمجة وتطوير ابراهيم الخزرجي" + أزرار ← `OpenSite` / `OpenTelegram` / `OpenTikTok`

> استخدم **RTLTextMeshPro** بدل TextMeshPro للنصوص العربية، مع خط عربي (مثل Cairo أو Tajawal) كـ TMP Font Asset.

### 8) التجربة
- **بالمحرر**: شغّل Play — يستخدم أرضية وهمية، اضغط بالماوس حتى تنزل القلعة. (أو فعّل XR Simulation من XR Plug-in Management)
- **على الموبايل**: File ▸ Build Profiles ▸ Android ▸ Build And Run

---

## كيف يشتغل ذكاء الأعداء
- الساحة شبكة، القلعة بالوسط، الأعداء يظهرون من الحافات.
- كل عدو يحسب **A\*** للقلعة. المربع الفاضي كلفته 1، الجدار 25، البرج 40.
- يعني إذا اللفة حول الجدار أقل من 25 خطوة → يلف. إذا سدّيت كل شي → الطريق "الأرخص" يمر من الجدار، فيوقف ويكسره.
- أي بناء أو هدم → كل الأعداء يعيدون الحساب فوراً.
- الفخ ينمشى عليه، فالأعداء ما يتجنبونه 😈

## نصائح الأداء
- كل مجسم أقل من 10k مضلع، الجنود 1.5–2.5k
- خامة وحدة لكل مجسم (Atlas) — بدون Normal Maps
- URP Asset: Shadows = Hard، Distance صغيرة (2م)، MSAA 2x، HDR Off
- فعّل **GPU Instancing** على الخامات و **SRP Batcher**

## أفكار للنسخة الجاية
بيع المباني بالضغط المطوّل · ترقية الأبراج · برج مدفع بضرر منطقة · أعداء يطيرون · مستويات خرائط مختلفة · محفظة نقاط ولوحة أبطال
