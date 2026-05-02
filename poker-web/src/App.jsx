import { useState } from 'react';
import { useWebSocket } from './context/WebSocketContext';
import Lobby from './pages/Lobby';
import Game from './pages/Game';
import styles from './App.module.css';

export default function App() {
  const { connected, connect, send, userId } = useWebSocket();
  const [inputId, setInputId] = useState('');
  const [currentRoom, setCurrentRoom] = useState(null);
  const [initialRoomState, setInitialRoomState] = useState(null);

  if (!connected) {
    return (
      <div className={styles.login}>
        <div className={styles.loginCard}>
          <h1 className={styles.loginTitle}>Texas Hold'em</h1>
          <p className={styles.loginSub}>输入昵称加入游戏</p>
          <form onSubmit={(e) => { e.preventDefault(); if (inputId.trim()) connect(inputId.trim()); }}>
            <input
              className={styles.loginInput}
              value={inputId}
              onChange={e => setInputId(e.target.value)}
              placeholder="你的昵称"
              autoFocus
            />
            <button type="submit" className={styles.loginBtn}>连接</button>
          </form>
        </div>
      </div>
    );
  }

  if (currentRoom) {
    return (
      <Game
        roomId={currentRoom.roomId}
        roomName={currentRoom.roomName}
        initialRoomState={initialRoomState}
        onLeave={() => {
          send('/app/room/leave', { roomId: currentRoom.roomId });
          setCurrentRoom(null);
          setInitialRoomState(null);
        }}
      />
    );
  }

  return <Lobby onJoinRoom={(roomId, roomName, roomData) => {
    setCurrentRoom({ roomId, roomName });
    setInitialRoomState(roomData || null);
  }} />;
}
