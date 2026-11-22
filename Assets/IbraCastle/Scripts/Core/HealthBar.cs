using UnityEngine;
using UnityEngine.UI;

namespace IbraCastle
{
    /// <summary>
    /// شريط صحة فوق الرأس (World Space Canvas + Image نوعها Filled) يواجه الكاميرا دائماً.
    /// </summary>
    public class HealthBar : MonoBehaviour
    {
        [SerializeField] Image fill;
        [SerializeField] Gradient colorByHealth;
        [SerializeField] bool hideWhenFull = true;
        [SerializeField] GameObject root;

        Camera cam;

        void Awake() => EnsureSetup();

        void EnsureSetup()
        {
            if (root == null) root = gameObject;
            if (colorByHealth == null || colorByHealth.colorKeys.Length < 2)
            {
                colorByHealth = new Gradient();
                colorByHealth.SetKeys(
                    new[] { new GradientColorKey(new Color(1f, .25f, .2f), 0f),
                            new GradientColorKey(new Color(1f, .85f, .2f), .5f),
                            new GradientColorKey(new Color(.3f, 1f, .35f), 1f) },
                    new[] { new GradientAlphaKey(1f, 0f), new GradientAlphaKey(1f, 1f) });
            }
        }

        public void Set(float normalized)
        {
            EnsureSetup();
            normalized = Mathf.Clamp01(normalized);
            if (fill != null)
            {
                fill.fillAmount = normalized;
                fill.color = colorByHealth.Evaluate(normalized);
            }
            if (hideWhenFull && root != null) root.SetActive(normalized < 0.999f && normalized > 0f);
        }

        void LateUpdate()
        {
            if (cam == null) cam = Camera.main;
            if (cam == null) return;
            transform.rotation = Quaternion.LookRotation(transform.position - cam.transform.position, cam.transform.up);
        }
    }
}
