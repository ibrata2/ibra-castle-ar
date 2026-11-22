using TMPro;
using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.UI;

namespace IbraCastle
{
    /// <summary>
    /// زر بشريط البناء: اسحبه وافلته على الساحة. يتعتّم إذا الذهب ما يكفي.
    /// </summary>
    public class BuildButton : MonoBehaviour, IPointerDownHandler, IPointerUpHandler, IBeginDragHandler, IDragHandler, IEndDragHandler
    {
        [SerializeField] int buildingIndex;
        [SerializeField] TMP_Text nameText;
        [SerializeField] TMP_Text costText;
        [SerializeField] Image iconImage;
        [SerializeField] CanvasGroup group;
        [SerializeField] float pressedScale = 1.12f;

        BuildingData data;
        Vector3 baseScale;

        void Start()
        {
            baseScale = transform.localScale;
            if (group == null) group = GetComponent<CanvasGroup>();
            var bm = BuildManager.Instance;
            if (bm != null && buildingIndex >= 0 && buildingIndex < bm.Buildings.Length)
            {
                data = bm.Buildings[buildingIndex];
                if (nameText != null) nameText.text = data.displayName;
                if (costText != null) costText.text = data.cost.ToString();
                if (iconImage != null && data.icon != null) iconImage.sprite = data.icon;
            }
            if (GameManager.Instance != null)
            {
                GameManager.Instance.GoldChanged += OnGold;
                OnGold(GameManager.Instance.Gold);
            }
        }

        void OnDestroy()
        {
            if (GameManager.Instance != null) GameManager.Instance.GoldChanged -= OnGold;
        }

        void OnGold(int gold)
        {
            if (group != null && data != null) group.alpha = gold >= data.cost ? 1f : 0.45f;
        }

        public void OnPointerDown(PointerEventData e) => transform.localScale = baseScale * pressedScale;
        public void OnPointerUp(PointerEventData e) => transform.localScale = baseScale;

        public void OnBeginDrag(PointerEventData e) => BuildManager.Instance?.BeginDrag(buildingIndex, e.position);
        public void OnDrag(PointerEventData e) => BuildManager.Instance?.UpdateDrag(e.position);
        public void OnEndDrag(PointerEventData e) => BuildManager.Instance?.EndDrag(e.position);
    }
}
