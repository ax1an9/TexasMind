import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useWebSocket } from '../context/WebSocketContext';
import CreateRoomModal from '../components/CreateRoomModal';
import LanguageSwitcher from '../components/LanguageSwitcher';
import styles from './Lobby.module.css';

export default function Lobby({ onJoinRoom, onOpenProfile }) {
  const { t } = useTranslation(['lobby', 'common']);
  const { send, subscribe, connected, userId } = useWebSocket();
  const [rooms, setRooms] = useState([]);
  const [showCreate, setShowCreate] = useState(false);

  useEffect(() => {
    if (!connected) return;
    const unsub1 = subscribe('/topic/lobby', (msg) => {
      const data = JSON.parse(msg.body);
      if (data.rooms) setRooms(data.rooms);
    });
    const unsub2 = subscribe('/user/queue/room-created', (msg) => {
      const data = JSON.parse(msg.body);
      if (data.roomId) onJoinRoom(data.roomId, data.roomName || data.roomId, data);
    });
    const unsub3 = subscribe('/user/queue/room-list', (msg) => {
      const data = JSON.parse(msg.body);
      if (data.rooms) setRooms(data.rooms);
    });
    send('/app/lobby/rooms', {});
    return () => { unsub1(); unsub2(); unsub3(); };
  }, [connected, subscribe, onJoinRoom, send]);

  const handleCreate = (form) => {
    send('/app/room/create', form);
    setShowCreate(false);
  };

  const handleJoin = (roomId, roomName) => {
    send('/app/room/join', { roomId });
    onJoinRoom(roomId, roomName || roomId);
  };

  const statusMap = {
    WAITING: t('lobby:statusWaiting'),
    PLAYING: t('lobby:statusPlaying'),
    GAME_OVER: t('lobby:statusEnded'),
  };

  return (
    <div className={styles.lobby}>
      <header className={styles.header}>
        <h1 className={styles.title}>Texas Hold'em</h1>
        <div className={styles.headerRight}>
          <LanguageSwitcher />
          <div className={styles.user} onClick={() => onOpenProfile && onOpenProfile(userId)} style={{ cursor: 'pointer' }}>
            <span className={styles.dot} />
            {userId}
          </div>
        </div>
      </header>

      <div className={styles.content}>
        <div className={styles.sectionHeader}>
          <h2 className={styles.sectionTitle}>{t('lobby:roomList')}</h2>
          <button className={styles.createBtn} onClick={() => setShowCreate(true)}>
            {t('lobby:createRoom')}
          </button>
        </div>

        {rooms.length === 0 ? (
          <div className={styles.empty}>{t('lobby:noRooms')}</div>
        ) : (
          <div className={styles.roomList}>
            {rooms.map(room => (
              <div key={room.roomId} className={styles.roomCard}>
                <div className={styles.roomInfo}>
                  <span className={styles.roomName}>{room.name}</span>
                  <span className={styles.roomMeta}>
                    {room.currentPlayers}/{room.maxPlayers} {t('lobby:players')}
                  </span>
                  <span className={`${styles.status} ${styles[room.status.toLowerCase()]}`}>
                    {statusMap[room.status] || t('lobby:statusClosed')}
                  </span>
                </div>
                <button
                  className={styles.joinBtn}
                  onClick={() => handleJoin(room.roomId, room.name)}
                  disabled={room.status !== 'WAITING' || room.currentPlayers >= room.maxPlayers}
                >
                  {t('lobby:join')}
                </button>
              </div>
            ))}
          </div>
        )}
      </div>

      {showCreate && (
        <CreateRoomModal
          onClose={() => setShowCreate(false)}
          onSubmit={handleCreate}
        />
      )}
    </div>
  );
}
