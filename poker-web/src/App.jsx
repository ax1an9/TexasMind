import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useWebSocket } from './context/WebSocketContext';
import Lobby from './pages/Lobby';
import Game from './pages/Game';
import Profile from './pages/Profile';
import LanguageSwitcher from './components/LanguageSwitcher';
import styles from './App.module.css';

export default function App() {
  const { t } = useTranslation(['common']);
  const { connected, connect, send, userId } = useWebSocket();
  const [inputId, setInputId] = useState('');
  const [currentRoom, setCurrentRoom] = useState(null);
  const [initialRoomState, setInitialRoomState] = useState(null);
  const [viewingProfile, setViewingProfile] = useState(null);

  if (!connected) {
    return (
      <div className={styles.login}>
        <div className={styles.loginCard}>
          <h1 className={styles.loginTitle}>Texas Hold'em</h1>
          <p className={styles.loginSub}>{t('common:loginSubtitle')}</p>
          <form onSubmit={(e) => { e.preventDefault(); if (inputId.trim()) connect(inputId.trim()); }}>
            <input
              className={styles.loginInput}
              value={inputId}
              onChange={e => setInputId(e.target.value)}
              placeholder={t('common:nicknamePlaceholder')}
              autoFocus
            />
            <button type="submit" className={styles.loginBtn}>{t('common:connect')}</button>
          </form>
          <div className={styles.langRow}><LanguageSwitcher /></div>
        </div>
      </div>
    );
  }

  if (viewingProfile) {
    return <Profile playerId={viewingProfile} onBack={() => setViewingProfile(null)} />;
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

  return <Lobby
    onJoinRoom={(roomId, roomName, roomData) => {
      setCurrentRoom({ roomId, roomName });
      setInitialRoomState(roomData || null);
    }}
    onOpenProfile={(playerId) => setViewingProfile(playerId)}
  />;
}
