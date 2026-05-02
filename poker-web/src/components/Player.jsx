import Card from './Card';
import styles from './Player.module.css';

export default function Player({ player, isCurrentPlayer, isSelf, showCards }) {
  if (!player) return <div className={styles.empty} />;

  const stateLabel = player.folded ? '已弃牌'
    : player.allIn ? 'ALL IN'
    : isCurrentPlayer ? '思考中...'
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
        <div className={styles.bet}>下注: ${player.roundContribution}</div>
      )}
      {player.handRankDisplay && (
        <div className={styles.handRank}>{player.handRankDisplay}</div>
      )}
    </div>
  );
}
