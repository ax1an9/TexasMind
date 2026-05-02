import { getSuitSymbol, getSuitColor, getRankDisplay } from '../utils/cardDisplay';
import styles from './Card.module.css';

export default function Card({ rank, suit, hidden = false }) {
  if (hidden || !rank) {
    return (
      <div className={`${styles.card} ${styles.back}`}>
        <div className={styles.backPattern} />
      </div>
    );
  }

  const color = getSuitColor(suit);
  const symbol = getSuitSymbol(suit);
  const display = getRankDisplay(rank);

  return (
    <div className={styles.card} style={{ color }}>
      <div className={styles.rank}>{display}</div>
      <div className={styles.suit}>{symbol}</div>
    </div>
  );
}
