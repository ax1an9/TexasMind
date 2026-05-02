import { useState } from 'react';
import styles from './ActionBar.module.css';

export default function ActionBar({ gameState, onAction, disabled }) {
  const [raiseAmount, setRaiseAmount] = useState(0);

  if (!gameState || disabled) {
    return <div className={styles.bar}><span className={styles.waiting}>等待其他玩家...</span></div>;
  }

  const currentBet = gameState.currentBet || 0;
  const self = gameState.players?.find(p => p.seatId === gameState.currentPlayerId);
  const toCall = self ? Math.max(0, currentBet - (self.roundContribution || 0)) : 0;
  const canCheck = toCall === 0;
  const minRaise = currentBet * 2 || 2;

  const handleRaise = () => {
    const amount = Math.max(minRaise, raiseAmount);
    onAction('RAISE', amount);
  };

  return (
    <div className={styles.bar}>
      <button className={`${styles.btn} ${styles.fold}`} onClick={() => onAction('FOLD', 0)}>
        弃牌
      </button>
      {canCheck ? (
        <button className={`${styles.btn} ${styles.check}`} onClick={() => onAction('CHECK', 0)}>
          过牌
        </button>
      ) : (
        <button className={`${styles.btn} ${styles.call}`} onClick={() => onAction('CALL', 0)}>
          跟注 ${toCall}
        </button>
      )}
      <div className={styles.raiseGroup}>
        <input
          type="range"
          min={minRaise}
          max={self?.chips || 100}
          value={raiseAmount || minRaise}
          onChange={e => setRaiseAmount(Number(e.target.value))}
          className={styles.slider}
        />
        <input
          type="number"
          min={minRaise}
          max={self?.chips || 100}
          value={raiseAmount || minRaise}
          onChange={e => setRaiseAmount(Number(e.target.value))}
          className={styles.amountInput}
        />
        <button className={`${styles.btn} ${styles.raise}`} onClick={handleRaise}>
          加注
        </button>
      </div>
      <button className={`${styles.btn} ${styles.allin}`} onClick={() => onAction('ALL_IN', 0)}>
        全下
      </button>
    </div>
  );
}
