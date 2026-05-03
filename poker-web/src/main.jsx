import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { WebSocketProvider } from './context/WebSocketContext';
import App from './App';
import './i18n';
import './index.css';

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <WebSocketProvider>
      <App />
    </WebSocketProvider>
  </StrictMode>
);
