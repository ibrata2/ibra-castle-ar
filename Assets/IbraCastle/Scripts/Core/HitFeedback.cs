using UnityEngine;

namespace IbraCastle
{
    /// <summary>
    /// إحساس كرتوني عند الضربة: نطة حجم (squash) + وميض أبيض. ضعه على القلعة/الأعداء/المباني.
    /// </summary>
    public class HitFeedback : MonoBehaviour
    {
        [SerializeField] Transform visual;
        [SerializeField] float punch = 0.18f;
        [SerializeField] float duration = 0.15f;
        [SerializeField] Color flashColor = Color.white;

        Health health;
        Renderer[] renderers;
        MaterialPropertyBlock mpb;
        Vector3 baseScale;
        float t = -1f;

        static readonly int BaseColorId = Shader.PropertyToID("_BaseColor");
        static readonly int ColorId = Shader.PropertyToID("_Color");
        static readonly int EmissionId = Shader.PropertyToID("_EmissionColor");

        void Awake()
        {
            health = GetComponent<Health>();
            if (visual == null) visual = transform;
            baseScale = visual.localScale;
            renderers = visual.GetComponentsInChildren<Renderer>();
            mpb = new MaterialPropertyBlock();
        }

        void OnEnable() { if (health != null) health.Damaged += OnDamaged; }
        void OnDisable() { if (health != null) health.Damaged -= OnDamaged; }

        void OnDamaged(float _) => t = 0f;

        void Update()
        {
            if (t < 0f) return;
            t += Time.deltaTime;
            float k = Mathf.Clamp01(t / duration);
            float wave = Mathf.Sin(k * Mathf.PI);
            visual.localScale = new Vector3(baseScale.x * (1 + punch * wave), baseScale.y * (1 - punch * wave), baseScale.z * (1 + punch * wave));

            foreach (var r in renderers)
            {
                if (r == null) continue;
                r.GetPropertyBlock(mpb);
                mpb.SetColor(EmissionId, flashColor * wave);
                r.SetPropertyBlock(mpb);
            }

            if (k >= 1f)
            {
                visual.localScale = baseScale;
                foreach (var r in renderers) if (r != null) r.SetPropertyBlock(null);
                t = -1f;
            }
        }
    }
}
