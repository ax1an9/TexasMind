import { useTranslation } from 'react-i18next';
import Card from './Card';
import styles from './Board.module.css';

export default function Board({ cards = [], pot }) {
  const { t } = useTranslation(['common']);

  return (
    <div className={styles.board}>
      <div className={styles.cards}>
        {[0, 1, 2, 3, 4].map(i => (
          cards[i] ? (
            <Card key={i} rank={cards[i].rank} suit={cards[i].suit} />
          ) : (
            <div key={i} className={styles.placeholder} />
          )
        ))}
      </div>
      {pot != null && (
        <div className={styles.pot}>{t('common:pot')}: ${pot}</div>
      )}
    </div>
  );
}
