import styles from './HintPanel.module.css';

export default function HintPanel({ hint, onRequestHint, canRequest }) {
  return (
    <div className={styles.panel}>
      <button
        className={styles.hintBtn}
        onClick={onRequestHint}
        disabled={!canRequest}
      >
        获取提示
      </button>
      {hint && (
        <div className={styles.result}>
          <span className={styles.label}>建议:</span>
          <span className={styles.action}>{hint.suggestedAction}</span>
          <span className={styles.detail}>
            手牌强度 {Math.round(hint.handStrength * 100)}%
            {hint.potOdds > 0 && ` · 底池赔率 ${Math.round(hint.potOdds * 100)}%`}
          </span>
          <span className={styles.reasoning}>{hint.reasoning}</span>
        </div>
      )}
    </div>
  );
}
