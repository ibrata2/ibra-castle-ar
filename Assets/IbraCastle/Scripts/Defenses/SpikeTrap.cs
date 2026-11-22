using UnityEngine;

namespace IbraCastle
{
    /// <summary>
    /// فخ المسامير: الأعداء يمشون فوقه، المسامير تطلع وتجرح كل من بالمربع.
    /// يتآكل بعد عدد معين من الضربات (0 = ما يتآكل).
    /// </summary>
    // ضعه على نفس الكائن اللي عليه Building (بدون RequireComponent حتى ما يعرقل نسخة الشبح)
    public class SpikeTrap : MonoBehaviour
    {
        [SerializeField] float damage = 8f;
        [SerializeField] float interval = 1f;
        [SerializeField, Tooltip("بالمربعات")] float radius = 0.55f;
        [SerializeField] int usesBeforeBreak = 40;

        [Header("Animation")]
        [SerializeField] Transform spikes;
        [SerializeField] float spikesDownY = -0.25f;
        [SerializeField] float spikesUpY = 0f;
        [SerializeField] AudioClip stabSfx;

        float timer;
        float animT = 1f;
        int usesLeft;
        Building building;

        void Awake()
        {
            building = GetComponent<Building>();
            usesLeft = usesBeforeBreak;
            SetSpikes(0f);
        }

        void Update()
        {
            AnimateSpikes();

            var gm = GameManager.Instance;
            if (gm == null || !gm.IsPlaying) return;

            timer -= Time.deltaTime;
            if (timer > 0f) return;

            float r2 = radius * radius;
            bool hitAny = false;
            // نسخة عكسية لأن القتل يحذف من القائمة
            for (int i = EnemyAI.All.Count - 1; i >= 0; i--)
            {
                if (i >= EnemyAI.All.Count) continue;
                var e = EnemyAI.All[i];
                var d = e.transform.localPosition - transform.localPosition;
                d.y = 0f;
                if (d.sqrMagnitude > r2) continue;
                e.Health.TakeDamage(damage);
                hitAny = true;
            }

            if (!hitAny) return;

            timer = interval;
            animT = 0f;
            if (stabSfx != null) AudioSource.PlayClipAtPoint(stabSfx, transform.position, 0.6f);

            if (usesBeforeBreak > 0 && --usesLeft <= 0 && building != null) building.Demolish();
        }

        void AnimateSpikes()
        {
            if (spikes == null || animT >= 1f) return;
            animT += Time.deltaTime / 0.45f;
            // طلعة سريعة ونزلة بطيئة
            float k = animT < 0.15f ? animT / 0.15f : 1f - (animT - 0.15f) / 0.85f;
            SetSpikes(Mathf.Clamp01(k));
        }

        void SetSpikes(float k)
        {
            if (spikes == null) return;
            var p = spikes.localPosition;
            p.y = Mathf.Lerp(spikesDownY, spikesUpY, k);
            spikes.localPosition = p;
        }
    }
}
