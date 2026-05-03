import { useState, useEffect, useCallback } from 'react';
import { useWebSocket } from '../context/WebSocketContext';

export function usePlayerStats(playerId) {
  const { subscribe, send } = useWebSocket();
  const [stats, setStats] = useState(null);
  const [style, setStyle] = useState(null);

  const fetchStats = useCallback(() => {
    if (!playerId) return;
    send('/app/player/stats', { playerId });
    send('/app/player/style', { playerId });
  }, [playerId, send]);

  useEffect(() => {
    if (!playerId) return;

    const unsub1 = subscribe('/user/queue/player-stats', (msg) => {
      const data = JSON.parse(msg.body);
      if (data.playerId === playerId) setStats(data);
    });

    const unsub2 = subscribe('/user/queue/player-style', (msg) => {
      const data = JSON.parse(msg.body);
      if (data.playerId === playerId) setStyle(data);
    });

    fetchStats();
    return () => { unsub1(); unsub2(); };
  }, [playerId, subscribe, fetchStats]);

  return { stats, style, refresh: fetchStats };
}
