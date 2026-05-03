import { useTranslation } from 'react-i18next';
import Card from './Card';
import PlayerHUD from './PlayerHUD';
import styles from './Player.module.css';

export default function Player({ player, isCurrentPlayer, isSelf, showCards }) {
  const { t } = useTranslation(['common']);

  if (!player) return <div className={styles.empty} />;

  const stateLabel = player.folded ? t('common:folded')
    : player.allIn ? t('common:allInStatus')
    : isCurrentPlayer ? t('common:thinking')
    : '';

  return (
    <div className={`${styles.player} ${isCurrentPlayer ? styles.active : ''} ${isSelf ? styles.self : ''}`}>
      <div className={styles.name}>
        {player.seatId}
        {player.agentType && (
          <span className={player.agentType === 'react' ? styles.agentReact : styles.agentSimple}>
            {player.agentType === 'react' ? 'ReAct' : 'Simple'}
          </span>
        )}
      </div>
      <div className={styles.chips}>${player.chips}</div>
      {stateLabel && <div className={styles.state}>{stateLabel}</div>}
      {!player.agentType && <PlayerHUD playerId={player.seatId} isSelf={isSelf} />}
      <div className={styles.cards}>
        {player.holeCards && player.holeCards.length > 0 ? (
          player.holeCards.map((c, i) => (
            <Card key={i} rank={c.rank} suit={c.suit} />
          ))
        ) : (
          <>
            <Card hidden />
            <Card hidden />
          </>
        )}
      </div>
      {player.roundContribution > 0 && (
        <div className={styles.bet}>{t('common:bet')}: ${player.roundContribution}</div>
      )}
      {player.handRankDisplay && (
        <div className={styles.handRank}>{player.handRankDisplay}</div>
      )}
    </div>
  );
}
