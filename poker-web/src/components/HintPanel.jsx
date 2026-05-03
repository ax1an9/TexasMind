import { useState, useEffect } from 'react';
import styles from './HintPanel.module.css';

const VIEW_MODE_KEY = 'poker-hint-view-mode';
const MODES = ['simple', 'standard', 'detailed'];
const MODE_LABELS = { simple: '简洁', standard: '标准', detailed: '详细' };
const ACTION_LABELS = { FOLD: '弃牌', CHECK: '过牌', CALL: '跟注', RAISE: '加注' };
const ACTION_ICONS = { FOLD: '✕', CHECK: '✓', CALL: '$', RAISE: '↑' };
const ACTION_ICON_STYLES = {
  FOLD: styles.iconFold,
  CHECK: styles.iconCheck,
  CALL: styles.iconCall,
  RAISE: styles.iconRaise,
};

function getInitialMode() {
  try {
    const saved = localStorage.getItem(VIEW_MODE_KEY);
    if (saved && MODES.includes(saved)) return saved;
  } catch (_) {}
  return 'standard';
}

export default function HintPanel({ hint, onRequestHint, canRequest }) {
  const [viewMode, setViewMode] = useState(getInitialMode);

  useEffect(() => {
    try { localStorage.setItem(VIEW_MODE_KEY, viewMode); } catch (_) {}
  }, [viewMode]);

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
        <div className={styles.modeToggle}>
          {MODES.map(m => (
            <button
              key={m}
              className={`${styles.modeBtn} ${viewMode === m ? styles.modeBtnActive : ''}`}
              onClick={() => setViewMode(m)}
            >
              {MODE_LABELS[m]}
            </button>
          ))}
        </div>
      )}

      {hint && (
        <div className={styles.content}>
          {viewMode === 'simple' && <SimpleView hint={hint} />}
          {viewMode === 'standard' && <StandardView hint={hint} />}
          {viewMode === 'detailed' && <DetailedView hint={hint} />}
        </div>
      )}
    </div>
  );
}

function SimpleView({ hint }) {
  const action = hint.suggestedAction;
  return (
    <div className={styles.simpleView}>
      <div className={`${styles.actionIcon} ${ACTION_ICON_STYLES[action] || ''}`}>
        {ACTION_ICONS[action] || '?'}
      </div>
      <span className={styles.actionLabel}>{ACTION_LABELS[action] || action}</span>
      <span className={styles.simpleReasoning}>{hint.simpleReasoning}</span>
    </div>
  );
}

function StandardView({ hint }) {
  const action = hint.suggestedAction;
  const strengthPct = Math.round(hint.handStrength * 100);
  const oddsPct = Math.round(hint.potOdds * 100);

  return (
    <div className={styles.standardView}>
      <div className={styles.actionRow}>
        <span className={styles.actionLabel}>{ACTION_LABELS[action] || action}</span>
      </div>
      <div className={styles.barGroup}>
        <div className={styles.barRow}>
          <span className={styles.barLabel}>手牌强度</span>
          <div className={styles.barTrack}>
            <div
              className={`${styles.barFill} ${styles.barStrength}`}
              style={{ width: `${strengthPct}%` }}
            />
          </div>
          <span className={styles.barValue}>{strengthPct}%</span>
        </div>
        {hint.potOdds > 0 && (
          <div className={styles.barRow}>
            <span className={styles.barLabel}>底池赔率</span>
            <div className={styles.barTrack}>
              <div
                className={`${styles.barFill} ${styles.barOdds}`}
                style={{ width: `${oddsPct}%` }}
              />
            </div>
            <span className={styles.barValue}>{oddsPct}%</span>
          </div>
        )}
      </div>
      <div className={styles.reasoning}>{hint.reasoning}</div>
    </div>
  );
}

function DetailedView({ hint }) {
  const action = hint.suggestedAction;
  const factors = hint.strengthFactors || [];
  const totalStrength = factors.reduce((sum, f) => sum + f.value, 0);
  const strengthPct = Math.round(hint.handStrength * 100);

  return (
    <div className={styles.detailedView}>
      <div className={styles.detailHeader}>
        <span className={styles.actionLabel}>{ACTION_LABELS[action] || action}</span>
        {hint.handRankName && <span className={styles.handRank}>{hint.handRankName}</span>}
      </div>

      {factors.length > 0 && (
        <div className={styles.factorSection}>
          <div className={styles.factorTitle}>手牌强度分析</div>
          {factors.map((f, i) => (
            <div key={i} className={styles.factorRow}>
              <span>
                <span className={styles.factorLabel}>{f.label}</span>
                {f.description && <span className={styles.factorDesc}>({f.description})</span>}
              </span>
              <span className={styles.factorValue}>+{(f.value * 100).toFixed(0)}%</span>
            </div>
          ))}
          <div className={styles.factorTotal}>
            <span className={styles.factorLabel}>总强度</span>
            <span className={styles.factorValue}>{strengthPct}%</span>
          </div>
        </div>
      )}

      {hint.potOdds > 0 && (
        <div className={styles.oddsMath}>
          底池赔率: <span className={styles.oddsFormula}>
            {hint.toCall} / ({hint.totalPot} + {hint.toCall}) = {Math.round(hint.potOdds * 100)}%
          </span>
          {hint.handStrength >= hint.potOdds
            ? ' — 强度高于赔率，跟注有利'
            : ' — 强度低于赔率，需谨慎'}
        </div>
      )}

      <div className={styles.reasoning}>{hint.reasoning}</div>
    </div>
  );
}
