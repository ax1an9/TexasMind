import { useState, useEffect } from 'react';
import { useWebSocket } from '../context/WebSocketContext';
import CreateRoomModal from '../components/CreateRoomModal';
import styles from './Lobby.module.css';

export default function Lobby({ onJoinRoom }) {
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
    // Request current room list on mount
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

  return (
    <div className={styles.lobby}>
      <header className={styles.header}>
        <h1 className={styles.title}>Texas Hold'em</h1>
        <div className={styles.user}>
          <span className={styles.dot} />
          {userId}
        </div>
      </header>

      <div className={styles.content}>
        <div className={styles.sectionHeader}>
          <h2 className={styles.sectionTitle}>房间列表</h2>
          <button className={styles.createBtn} onClick={() => setShowCreate(true)}>
            + 创建房间
          </button>
        </div>

        {rooms.length === 0 ? (
          <div className={styles.empty}>暂无房间，创建一个吧</div>
        ) : (
          <div className={styles.roomList}>
            {rooms.map(room => (
              <div key={room.roomId} className={styles.roomCard}>
                <div className={styles.roomInfo}>
                  <span className={styles.roomName}>{room.name}</span>
                  <span className={styles.roomMeta}>
                    {room.currentPlayers}/{room.maxPlayers} 玩家
                  </span>
                  <span className={`${styles.status} ${styles[room.status.toLowerCase()]}`}>
                    {room.status === 'WAITING' ? '等待中' : room.status === 'PLAYING' ? '游戏中' : room.status === 'GAME_OVER' ? '已结束' : '已关闭'}
                  </span>
                </div>
                <button
                  className={styles.joinBtn}
                  onClick={() => handleJoin(room.roomId, room.name)}
                  disabled={room.status !== 'WAITING' || room.currentPlayers >= room.maxPlayers}
                >
                  加入
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
