using UnityEngine;

namespace IbraCastle
{
    /// <summary>
    /// سهم موجّه بقوس خفيف. يتحرك بإحداثيات الساحة (ابن للـ Board).
    /// </summary>
    public class Projectile : MonoBehaviour
    {
        [SerializeField, Tooltip("مربع/ثانية")] float speed = 9f;
        [SerializeField] float arcHeight = 0.6f;
        [SerializeField] float maxLifetime = 3f;
        [SerializeField] GameObject hitVfx;

        EnemyAI target;
        float damage;
        Vector3 startLocal, lastTargetLocal;
        float travel, totalDist, life;

        public void Launch(EnemyAI t, float dmg)
        {
            target = t;
            damage = dmg;
            startLocal = transform.localPosition;
            lastTargetLocal = TargetLocal();
            totalDist = Mathf.Max(0.01f, Vector3.Distance(startLocal, lastTargetLocal));
            travel = 0f;
            life = 0f;
        }

        Vector3 TargetLocal()
        {
            var gm = GameManager.Instance;
            if (target != null && !target.IsDead && gm != null) lastTargetLocal = gm.ToBoardLocal(target.AimPoint);
            return lastTargetLocal;
        }

        void Update()
        {
            life += Time.deltaTime;
            if (life > maxLifetime) { SimplePool.Despawn(gameObject); return; }

            var end = TargetLocal();
            totalDist = Mathf.Max(totalDist, 0.01f);
            travel += speed * Time.deltaTime;
            float k = Mathf.Clamp01(travel / totalDist);

            var prev = transform.localPosition;
            var pos = Vector3.Lerp(startLocal, end, k);
            pos.y += Mathf.Sin(k * Mathf.PI) * arcHeight * Mathf.Min(1f, totalDist / 3f);
            transform.localPosition = pos;

            var vel = pos - prev;
            if (vel.sqrMagnitude > 1e-8f) transform.localRotation = Quaternion.LookRotation(vel);

            if (k >= 1f) Hit();
        }

        void Hit()
        {
            if (target != null && !target.IsDead) target.Health.TakeDamage(damage);
            var gm = GameManager.Instance;
            if (hitVfx != null && gm != null) SimplePool.Spawn(hitVfx, gm.Board, transform.localPosition, Quaternion.identity);
            target = null;
            SimplePool.Despawn(gameObject);
        }
    }
}
