import { createContext, useContext, useRef, useState, useCallback, useEffect } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client/dist/sockjs';

const WebSocketContext = createContext(null);

export function WebSocketProvider({ children }) {
  const clientRef = useRef(null);
  const [connected, setConnected] = useState(false);
  const [userId, setUserId] = useState(null);
  const subscriptionsRef = useRef(new Map());

  const connect = useCallback((uid) => {
    if (clientRef.current?.active) return;

    const client = new Client({
      webSocketFactory: () => new SockJS('/ws/poker'),
      connectHeaders: { userId: uid },
      onConnect: () => {
        setConnected(true);
        setUserId(uid);
        // Re-subscribe any pending subscriptions
        subscriptionsRef.current.forEach((callback, destination) => {
          const sub = client.subscribe(destination, callback);
          subscriptionsRef.current.set(destination + '_sub', sub);
        });
      },
      onDisconnect: () => {
        setConnected(false);
      },
      onStompError: (frame) => {
        console.error('STOMP error:', frame.headers['message']);
      }
    });

    client.activate();
    clientRef.current = client;
  }, []);

  const disconnect = useCallback(() => {
    if (clientRef.current) {
      subscriptionsRef.current.clear();
      clientRef.current.deactivate();
      clientRef.current = null;
      setConnected(false);
      setUserId(null);
    }
  }, []);

  const subscribe = useCallback((destination, callback) => {
    if (!clientRef.current?.active) {
      // Store for later subscription
      subscriptionsRef.current.set(destination, callback);
      return () => subscriptionsRef.current.delete(destination);
    }

    const sub = clientRef.current.subscribe(destination, callback);
    return () => {
      sub.unsubscribe();
      subscriptionsRef.current.delete(destination);
    };
  }, []);

  const send = useCallback((destination, body, headers = {}) => {
    if (!clientRef.current?.active) {
      console.warn('Cannot send, STOMP not connected');
      return;
    }
    clientRef.current.publish({
      destination,
      body: JSON.stringify(body),
      headers: { 'content-type': 'application/json', ...headers }
    });
  }, []);

  useEffect(() => {
    return () => {
      if (clientRef.current) {
        clientRef.current.deactivate();
      }
    };
  }, []);

  return (
    <WebSocketContext.Provider value={{ connected, userId, connect, disconnect, subscribe, send }}>
      {children}
    </WebSocketContext.Provider>
  );
}

export function useWebSocket() {
  const ctx = useContext(WebSocketContext);
  if (!ctx) throw new Error('useWebSocket must be used within WebSocketProvider');
  return ctx;
}
