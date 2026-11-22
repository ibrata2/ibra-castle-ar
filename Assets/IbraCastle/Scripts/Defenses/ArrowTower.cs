using UnityEngine;

namespace IbraCastle
{
    /// <summary>
    /// برج السهام: يختار العدو الأقرب للقلعة ضمن المدى، يدوّر الرأس ويرمي سهم موجّه.
    /// </summary>
    public class ArrowTower : MonoBehaviour
    {
        [SerializeField, Tooltip("بالمربعات")] float range = 3.5f;
        [SerializeField, Tooltip("سهم/ثانية")] float fireRate = 1.2f;
        [SerializeField] float damage = 10f;
        [SerializeField] Projectile arrowPrefab;
        [SerializeField] Transform firePoint;
        [SerializeField] Transform rotatingHead;
        [SerializeField] float turnSpeed = 540f;
        [SerializeField] AudioClip shootSfx;

        EnemyAI target;
        float cooldown;
        float retargetTimer;

        void Update()
        {
            var gm = GameManager.Instance;
            if (gm == null || !gm.IsPlaying) return;

            retargetTimer -= Time.deltaTime;
            if (retargetTimer <= 0f || !IsValid(target))
            {
                target = FindTarget();
                retargetTimer = 0.2f;
            }

            cooldown -= Time.deltaTime;
            if (target == null) return;

            AimAt(target.AimPoint);

            if (cooldown <= 0f)
            {
                Fire(gm);
                cooldown = 1f / Mathf.Max(0.05f, fireRate);
            }
        }

        bool IsValid(EnemyAI e) =>
            e != null && !e.IsDead && (e.transform.localPosition - transform.localPosition).sqrMagnitude <= range * range;

        EnemyAI FindTarget()
        {
            EnemyAI best = null;
            float bestRemaining = float.MaxValue;
            float r2 = range * range;
            foreach (var e in EnemyAI.All)
            {
                if (e.IsDead) continue;
                if ((e.transform.localPosition - transform.localPosition).sqrMagnitude > r2) continue;
                float rem = e.RemainingPath; // الأقرب للقلعة = الأخطر
                if (rem < bestRemaining) { bestRemaining = rem; best = e; }
            }
            return best;
        }

        void AimAt(Vector3 worldPoint)
        {
            if (rotatingHead == null) return;
            var dir = worldPoint - rotatingHead.position;
            dir = Vector3.ProjectOnPlane(dir, transform.up);
            if (dir.sqrMagnitude < 1e-6f) return;
            var look = Quaternion.LookRotation(dir, transform.up);
            rotatingHead.rotation = Quaternion.RotateTowards(rotatingHead.rotation, look, turnSpeed * Time.deltaTime);
        }

        void Fire(GameManager gm)
        {
            if (arrowPrefab == null) return;
            var origin = firePoint != null ? firePoint : transform;
            var p = SimplePool.Spawn(arrowPrefab, gm.Board, gm.ToBoardLocal(origin.position), Quaternion.identity);
            p.Launch(target, damage);
            if (shootSfx != null) AudioSource.PlayClipAtPoint(shootSfx, origin.position, 0.5f);
        }

        void OnDrawGizmosSelected()
        {
            Gizmos.color = new Color(1f, .8f, .2f, .6f);
            Gizmos.DrawWireSphere(transform.position, range * transform.lossyScale.x);
        }
    }
}
