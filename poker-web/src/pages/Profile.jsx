import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { usePlayerStats } from '../hooks/usePlayerStats';
import { classifyPlayerStyle, formatPercent } from '../utils/playerStyle';
import styles from './Profile.module.css';

export default function Profile({ playerId, onBack }) {
  const { t } = useTranslation(['profile', 'common']);
  const { stats, style } = usePlayerStats(playerId);
  const [window, setWindow] = useState('allTime');

  if (!stats) {
    return (
      <div className={styles.profile}>
        <div className={styles.header}>
          <button className={styles.backBtn} onClick={onBack}>{t('common:back')}</button>
          <span className={styles.playerName}>{playerId}</span>
        </div>
        <div className={styles.empty}>{t('profile:noData')}</div>
      </div>
    );
  }

  const styleInfo = style ? classifyPlayerStyle(
    stats.publicStats?.vpip || 0,
    stats.publicStats?.pfr || 0
  ) : null;

  const pub = stats.publicStats || {};
  const priv = stats.privateStats || {};

  const statCards = [
    { label: 'VPIP', value: formatPercent(pub.vpip || 0), quality: getVpipQuality(pub.vpip) },
    { label: 'PFR', value: formatPercent(pub.pfr || 0), quality: 'avg' },
    { label: '3Bet', value: formatPercent(priv.threeBet || 0), quality: 'avg' },
    { label: 'AF', value: (priv.af || 0).toFixed(2), quality: getAfQuality(priv.af) },
    { label: 'WTSD', value: formatPercent(priv.wtsd || 0), quality: 'avg' },
    { label: 'W$SD', value: formatPercent(priv.wsd || 0), quality: getWsdQuality(priv.wsd) },
    { label: 'Fold to CB', value: formatPercent(priv.foldToCbet || 0), quality: 'avg' },
    { label: t('profile:totalHands'), value: stats.allTimeHands || 0, quality: 'avg' },
  ];

  const timeLabels = { allTime: t('profile:timeAll'), recent: t('profile:timeRecent'), session: t('profile:timeSession') };

  return (
    <div className={styles.profile}>
      <div className={styles.header}>
        <button className={styles.backBtn} onClick={onBack}>{t('common:back')}</button>
        <span className={styles.playerName}>{stats.displayName || playerId}</span>
      </div>

      {styleInfo && (
        <div className={styles.styleCard}>
          <div style={{ fontSize: 48 }}>{styleInfo.emoji}</div>
          <div className={styles.styleName}>{styleInfo.name}</div>
          <div className={styles.styleDesc}>{styleInfo.desc}</div>
        </div>
      )}

      <div className={styles.windowTabs}>
        {['allTime', 'recent', 'session'].map(w => (
          <button
            key={w}
            className={`${styles.tab} ${window === w ? styles.active : ''}`}
            onClick={() => setWindow(w)}
          >
            {timeLabels[w]}
          </button>
        ))}
      </div>

      <div className={styles.statsGrid}>
        {statCards.map(card => (
          <div key={card.label} className={styles.statCard}>
            <div className={styles.statLabel}>{card.label}</div>
            <div className={`${styles.statValue} ${styles[card.quality]}`}>{card.value}</div>
          </div>
        ))}
      </div>
    </div>
  );
}

function getVpipQuality(v) { return !v ? 'avg' : v < 0.14 || v > 0.23 ? 'bad' : 'good'; }
function getAfQuality(v) { return !v ? 'avg' : v < 1.5 ? 'bad' : v > 2.0 ? 'good' : 'avg'; }
function getWsdQuality(v) { return !v ? 'avg' : v < 0.45 ? 'bad' : v > 0.55 ? 'good' : 'avg'; }
