import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import styles from './CreateRoomModal.module.css';

export default function CreateRoomModal({ onClose, onSubmit }) {
  const { t } = useTranslation(['room', 'common']);
  const [form, setForm] = useState({
    roomName: '',
    maxPlayers: 6,
    smallBlind: 1,
    bigBlind: 2,
    startingChips: 200
  });

  const handleSubmit = (e) => {
    e.preventDefault();
    onSubmit(form);
  };

  const update = (key, value) => setForm(prev => ({ ...prev, [key]: value }));

  return (
    <div className={styles.overlay} onClick={onClose}>
      <div className={styles.modal} onClick={e => e.stopPropagation()}>
        <h2 className={styles.title}>{t('room:createRoom')}</h2>
        <form onSubmit={handleSubmit} className={styles.form}>
          <label className={styles.label}>
            {t('room:roomName')}
            <input value={form.roomName} onChange={e => update('roomName', e.target.value)}
              className={styles.input} required placeholder={t('room:roomNamePlaceholder')} />
          </label>
          <div className={styles.row}>
            <label className={styles.label}>
              {t('room:maxPlayers')}
              <input type="number" min={2} max={9} value={form.maxPlayers}
                onChange={e => update('maxPlayers', Number(e.target.value))} className={styles.input} />
            </label>
            <label className={styles.label}>
              {t('room:smallBlind')}
              <input type="number" min={1} value={form.smallBlind}
                onChange={e => update('smallBlind', Number(e.target.value))} className={styles.input} />
            </label>
            <label className={styles.label}>
              {t('room:bigBlind')}
              <input type="number" min={1} value={form.bigBlind}
                onChange={e => update('bigBlind', Number(e.target.value))} className={styles.input} />
            </label>
          </div>
          <label className={styles.label}>
            {t('room:startingChips')}
            <input type="number" min={100} value={form.startingChips}
              onChange={e => update('startingChips', Number(e.target.value))} className={styles.input} />
          </label>
          <div className={styles.actions}>
            <button type="button" onClick={onClose} className={styles.cancel}>{t('common:cancel')}</button>
            <button type="submit" className={styles.submit}>{t('common:confirm')}</button>
          </div>
        </form>
      </div>
    </div>
  );
}
