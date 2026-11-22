using TMPro;
using UnityEngine;
using UnityEngine.UI;

namespace IbraCastle
{
    /// <summary>
    /// الواجهة: لوحة وضع القلعة، العداد (موجة/ذهب/أعداء/صحة القلعة)، شريط البناء،
    /// زر "ابدأ الموجة"، شاشات الخسارة والفوز، وقسم "حول".
    /// ملاحظة: للنص العربي استخدم RTLTextMeshPro (يرث من TMP_Text فيتربط هنا مباشرة).
    /// </summary>
    public class UIManager : MonoBehaviour
    {
        [Header("Refs")]
        [SerializeField] ARCastlePlacer placer;
        [SerializeField] EnemySpawner spawner;

        [Header("Panels")]
        [SerializeField] GameObject placementPanel;
        [SerializeField] GameObject hudPanel;
        [SerializeField] GameObject buildBar;
        [SerializeField] GameObject gameOverPanel;
        [SerializeField] GameObject victoryPanel;
        [SerializeField] GameObject aboutPanel;

        [Header("Placement")]
        [SerializeField] TMP_Text placementHint;
        [SerializeField] Button placeButton;
        [SerializeField] Slider scaleSlider;

        [Header("HUD")]
        [SerializeField] TMP_Text goldText;
        [SerializeField] TMP_Text waveText;
        [SerializeField] TMP_Text enemiesText;
        [SerializeField] Image castleHpFill;
        [SerializeField] TMP_Text castleHpText;
        [SerializeField] Button nextWaveButton;
        [SerializeField] TMP_Text nextWaveLabel;
        [SerializeField] TMP_Text toastText;

        [Header("End screens")]
        [SerializeField] TMP_Text gameOverWaveText;

        [Header("Texts (عربي)")]
        [SerializeField] string hintScanning = "حرّك الموبايل ببطء حتى يكشف سطح مستوي";
        [SerializeField] string hintReady = "المس الشاشة حتى تضع القلعة";
        [SerializeField] string waveFormat = "الموجة {0}/{1}";
        [SerializeField] string enemiesFormat = "الأعداء: {0}";
        [SerializeField] string startWaveText = "ابدأ الموجة ⚔";
        [SerializeField] string autoStartFormat = "الموجة الجاية بعد {0}";
        [SerializeField] string waveClearedFormat = "+{0} ذهب — صدّيت الموجة!";
        [SerializeField] string gameOverFormat = "صمدت لحد الموجة {0}";

        [Header("About — حول")]
        [SerializeField] string siteUrl = "https://ibraprogram.netlify.app/";
        [SerializeField] string telegramUrl = "https://t.me/ibraprogram";
        [SerializeField] string tiktokUrl = "https://www.tiktok.com/@ibraprogram";

        float toastTimer;
        GameManager gm;
        bool castleHooked;

        void Start()
        {
            gm = GameManager.Instance;
            gm.GoldChanged += OnGold;
            gm.StateChanged += OnState;
            if (spawner != null)
            {
                spawner.WaveStarted += (w, t) => RefreshWave();
                spawner.WaveCleared += (w, bonus) => Toast(string.Format(waveClearedFormat, bonus));
            }
            if (placeButton != null) placeButton.onClick.AddListener(() => placer.TryPlace());
            if (nextWaveButton != null) nextWaveButton.onClick.AddListener(() => spawner.StartNextWave());
            if (scaleSlider != null)
            {
                scaleSlider.value = 0.25f;
                placer.SetScaleNormalized(scaleSlider.value);
                scaleSlider.onValueChanged.AddListener(placer.SetScaleNormalized);
            }

            Show(placementPanel, true);
            Show(hudPanel, false);
            Show(buildBar, false);
            Show(gameOverPanel, false);
            Show(victoryPanel, false);
            Show(aboutPanel, false);
            if (toastText != null) toastText.gameObject.SetActive(false);
            OnGold(gm.Gold);
            RefreshWave();
        }

        void OnDestroy()
        {
            if (gm != null) { gm.GoldChanged -= OnGold; gm.StateChanged -= OnState; }
        }

        void Update()
        {
            if (gm.State == GameState.Placing)
            {
                bool ready = placer != null && placer.HasValidPose;
                if (placementHint != null) placementHint.text = ready ? hintReady : hintScanning;
                if (placeButton != null) placeButton.interactable = ready;
                return;
            }

            if (enemiesText != null && spawner != null)
                enemiesText.text = string.Format(enemiesFormat, spawner.EnemiesRemaining);

            if (nextWaveLabel != null && spawner != null && gm.State == GameState.Building)
                nextWaveLabel.text = spawner.AutoStartCountdown > 0f
                    ? string.Format(autoStartFormat, Mathf.CeilToInt(spawner.AutoStartCountdown))
                    : startWaveText;

            if (toastTimer > 0f)
            {
                toastTimer -= Time.deltaTime;
                if (toastTimer <= 0f && toastText != null) toastText.gameObject.SetActive(false);
            }
        }

        void OnState(GameState s)
        {
            if (!castleHooked && gm.Castle != null)
            {
                gm.Castle.Changed += OnCastleHp;
                OnCastleHp(gm.Castle.Current, gm.Castle.Max);
                castleHooked = true;
            }

            Show(placementPanel, false);
            bool playing = s == GameState.Building || s == GameState.WaveRunning;
            Show(hudPanel, playing);
            Show(buildBar, playing);
            if (nextWaveButton != null) nextWaveButton.gameObject.SetActive(s == GameState.Building);
            Show(gameOverPanel, s == GameState.GameOver);
            Show(victoryPanel, s == GameState.Victory);

            if (s == GameState.GameOver)
            {
                if (BuildManager.Instance != null) BuildManager.Instance.CancelDrag();
                if (gameOverWaveText != null && spawner != null)
                    gameOverWaveText.text = string.Format(gameOverFormat, spawner.CurrentWave);
            }
            RefreshWave();
        }

        void OnGold(int g)
        {
            if (goldText != null) goldText.text = g.ToString();
        }

        void OnCastleHp(float cur, float max)
        {
            if (castleHpFill != null) castleHpFill.fillAmount = max > 0 ? cur / max : 0;
            if (castleHpText != null) castleHpText.text = $"{Mathf.CeilToInt(cur)}/{Mathf.CeilToInt(max)}";
        }

        void RefreshWave()
        {
            if (waveText != null && spawner != null)
                waveText.text = string.Format(waveFormat, Mathf.Max(spawner.CurrentWave, 0), spawner.TotalWaves);
        }

        void Toast(string msg)
        {
            if (toastText == null) return;
            toastText.text = msg;
            toastText.gameObject.SetActive(true);
            toastTimer = 2.2f;
        }

        static void Show(GameObject go, bool on) { if (go != null) go.SetActive(on); }

        // ───── أزرار تربطها من الـ Inspector ─────
        public void Restart() => gm.Restart();
        public void OpenAbout() => Show(aboutPanel, true);
        public void CloseAbout() => Show(aboutPanel, false);
        public void OpenSite() => Application.OpenURL(siteUrl);
        public void OpenTelegram() => Application.OpenURL(telegramUrl);
        public void OpenTikTok() => Application.OpenURL(tiktokUrl);
    }
}
