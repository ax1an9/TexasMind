import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { usePlayerStats } from '../hooks/usePlayerStats';
import styles from './PlayerHUD.module.css';

export default function PlayerHUD({ playerId, isSelf }) {
  const { t } = useTranslation(['common']);
  const { stats } = usePlayerStats(playerId);
  const [expanded, setExpanded] = useState(false);

  if (!stats || !stats.publicStats) return null;

  const pub = stats.publicStats;
  const priv = stats.privateStats;

  return (
    <div className={styles.hud}>
      <div className={styles.statRow}>
        <span className={styles.label}>VPIP</span>
        <span className={styles.value}>{(pub.vpip * 100).toFixed(0)}%</span>
      </div>
      <div className={styles.statRow}>
        <span className={styles.label}>PFR</span>
        <span className={styles.value}>{(pub.pfr * 100).toFixed(0)}%</span>
      </div>
      <div className={styles.hands}>{stats.allTimeHands} {t('common:hands')}</div>

      {isSelf && priv && (
        <>
          <button className={styles.expandBtn} onClick={() => setExpanded(!expanded)}>
            {expanded ? t('common:collapse') : t('common:expand')}
          </button>
          {expanded && (
            <div className={styles.privateStats}>
              <div className={styles.statRow}>
                <span className={styles.label}>3Bet</span>
                <span className={styles.value}>{(priv.threeBet * 100).toFixed(0)}%</span>
              </div>
              <div className={styles.statRow}>
                <span className={styles.label}>AF</span>
                <span className={styles.value}>{priv.af.toFixed(1)}</span>
              </div>
              <div className={styles.statRow}>
                <span className={styles.label}>WTSD</span>
                <span className={styles.value}>{(priv.wtsd * 100).toFixed(0)}%</span>
              </div>
              <div className={styles.statRow}>
                <span className={styles.label}>W$SD</span>
                <span className={styles.value}>{(priv.wsd * 100).toFixed(0)}%</span>
              </div>
              <div className={styles.statRow}>
                <span className={styles.label}>Fold CB</span>
                <span className={styles.value}>{(priv.foldToCbet * 100).toFixed(0)}%</span>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}
