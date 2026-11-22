using System;
using UnityEngine;

namespace IbraCastle
{
    /// <summary>
    /// نظام الصحة العام — يُستخدم للقلعة، الأعداء، الجدران والأبراج.
    /// Generic health component used by castle, enemies and buildings.
    /// </summary>
    public class Health : MonoBehaviour
    {
        [SerializeField] float maxHealth = 100f;
        [SerializeField] HealthBar healthBar;

        public float Max => maxHealth;
        public float Current { get; private set; }
        public bool IsDead { get; private set; }
        public float Normalized => maxHealth > 0 ? Current / maxHealth : 0f;

        /// <summary>(current, max)</summary>
        public event Action<float, float> Changed;
        public event Action<float> Damaged;
        public event Action<Health> Died;

        void Awake()
        {
            Current = maxHealth;
            if (healthBar == null) healthBar = GetComponentInChildren<HealthBar>(true);
        }

        void Start() => Refresh();

        /// <summary>تغيير الصحة القصوى (مثلاً تقوية الأعداء مع كل موجة).</summary>
        public void SetMax(float newMax, bool refill = true)
        {
            maxHealth = Mathf.Max(1f, newMax);
            if (refill) Current = maxHealth;
            else Current = Mathf.Min(Current, maxHealth);
            Refresh();
        }

        public void TakeDamage(float amount)
        {
            if (IsDead || amount <= 0f) return;
            Current = Mathf.Max(0f, Current - amount);
            Damaged?.Invoke(amount);
            Refresh();
            if (Current <= 0f)
            {
                IsDead = true;
                Died?.Invoke(this);
            }
        }

        public void Heal(float amount)
        {
            if (IsDead || amount <= 0f) return;
            Current = Mathf.Min(maxHealth, Current + amount);
            Refresh();
        }

        void Refresh()
        {
            Changed?.Invoke(Current, maxHealth);
            if (healthBar != null) healthBar.Set(Normalized);
        }
    }
}
