import { useState } from 'react';
import styles from './CreateRoomModal.module.css';

export default function CreateRoomModal({ onClose, onSubmit }) {
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
        <h2 className={styles.title}>创建房间</h2>
        <form onSubmit={handleSubmit} className={styles.form}>
          <label className={styles.label}>
            房间名称
            <input value={form.roomName} onChange={e => update('roomName', e.target.value)}
              className={styles.input} required placeholder="输入房间名称" />
          </label>
          <div className={styles.row}>
            <label className={styles.label}>
              最大玩家
              <input type="number" min={2} max={9} value={form.maxPlayers}
                onChange={e => update('maxPlayers', Number(e.target.value))} className={styles.input} />
            </label>
            <label className={styles.label}>
              小盲
              <input type="number" min={1} value={form.smallBlind}
                onChange={e => update('smallBlind', Number(e.target.value))} className={styles.input} />
            </label>
            <label className={styles.label}>
              大盲
              <input type="number" min={1} value={form.bigBlind}
                onChange={e => update('bigBlind', Number(e.target.value))} className={styles.input} />
            </label>
          </div>
          <label className={styles.label}>
            初始筹码
            <input type="number" min={100} value={form.startingChips}
              onChange={e => update('startingChips', Number(e.target.value))} className={styles.input} />
          </label>
          <div className={styles.actions}>
            <button type="button" onClick={onClose} className={styles.cancel}>取消</button>
            <button type="submit" className={styles.submit}>创建</button>
          </div>
        </form>
      </div>
    </div>
  );
}
