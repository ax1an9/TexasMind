import { useState, useEffect, useCallback, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { useWebSocket } from '../context/WebSocketContext';
import Board from '../components/Board';
import Player from '../components/Player';
import ActionBar from '../components/ActionBar';
import HintPanel from '../components/HintPanel';
import LanguageSwitcher from '../components/LanguageSwitcher';
import styles from './Game.module.css';

export default function Game({ roomId, roomName, initialRoomState, onLeave }) {
  const { t } = useTranslation(['game', 'common']);
  const { send, subscribe, userId } = useWebSocket();
  const [gameState, setGameState] = useState(null);
  const [gameResult, setGameResult] = useState(null);
  const [sessionSummary, setSessionSummary] = useState(null);
  const [roomState, setRoomState] = useState(initialRoomState || null);
  const [hint, setHint] = useState(null);
  const [isMyTurn, setIsMyTurn] = useState(false);
  const [lastPotAmount, setLastPotAmount] = useState(0);
  const prevPhaseRef = useRef(null);

  useEffect(() => {
    const unsub1 = subscribe('/user/queue/game-state', (msg) => {
      const data = JSON.parse(msg.body);
      setGameState(data);
      setGameResult(null);
      setIsMyTurn(data.currentPlayerId === userId);

      if (data.pot?.totalPot > 0) {
        setLastPotAmount(data.pot.totalPot);
      }

      const prev = prevPhaseRef.current;
      if (prev !== data.phase && (data.phase === 'FLOP' || data.phase === 'TURN' || data.phase === 'RIVER')) {
        send('/app/game/hint', { roomId });
      }
      prevPhaseRef.current = data.phase;
    });

    const unsub2 = subscribe('/user/queue/action-required', () => {
      setIsMyTurn(true);
    });

    const unsub3 = subscribe('/user/queue/hint-result', (msg) => {
      setHint(JSON.parse(msg.body));
    });

    const unsub4 = subscribe('/topic/room/' + roomId, (msg) => {
      const data = JSON.parse(msg.body);
      if (data.type === 'GAME_RESULT') {
        setGameResult(data);
      } else if (data.type === 'SESSION_SUMMARY') {
        setSessionSummary(data);
      } else if (data.type === 'ROOM_STATE') {
        setRoomState(data);
      }
    });

    const unsub5 = subscribe('/user/queue/room-state', (msg) => {
      const data = JSON.parse(msg.body);
      setRoomState(data);
    });

    send('/app/room/state', { roomId });

    return () => { unsub1(); unsub2(); unsub3(); unsub4(); unsub5(); };
  }, [roomId, subscribe, userId, send]);

  const handleAction = useCallback((actionType, amount) => {
    send('/app/game/action', { roomId, action: actionType, amount });
    setIsMyTurn(false);
    setHint(null);
  }, [send, roomId]);

  const handleStartGame = () => {
    send('/app/room/start', { roomId });
    setGameResult(null);
    setSessionSummary(null);
    setLastPotAmount(0);
    setHint(null);
    prevPhaseRef.current = null;
  };

  const handleReady = () => {
    const isReady = roomState?.players?.find(p => p.seatId === userId)?.ready;
    send(isReady ? '/app/room/unready' : '/app/room/ready', { roomId });
  };

  const handleAddBot = (agentType) => {
    send('/app/room/add-bot', { roomId, agentType });
  };

  const handleRemoveBot = (botId) => {
    send('/app/room/remove-bot', { roomId, botId });
  };

  const handleRequestHint = () => {
    send('/app/game/hint', { roomId });
  };

  const players = gameState?.players || [];
  const self = players.find(p => p.seatId === userId);
  const opponents = players.filter(p => p.seatId !== userId);
  const isSettled = gameState?.phase === 'SETTLED';
  const inGame = gameState && gameState.phase !== 'WAITING';
  const isHost = roomState?.hostId === userId;
  const myReady = roomState?.players?.find(p => p.seatId === userId)?.ready;
  const canStart = roomState?.canStart && isHost;

  const displayPot = (gameState?.pot?.totalPot > 0) ? gameState.pot.totalPot : lastPotAmount;

  const formatDuration = (ms) => {
    const secs = Math.floor(ms / 1000);
    const mins = Math.floor(secs / 60);
    const remainSecs = secs % 60;
    return mins > 0
      ? t('game:durationFormat', { min: mins, sec: remainSecs })
      : t('game:durationSec', { sec: remainSecs });
  };

  const showWaitingRoom = !inGame && roomState;

  return (
    <div className={styles.game}>
      <header className={styles.header}>
        <span className={styles.roomName}>{t('common:room')}: {roomState?.roomName || roomName || roomId}</span>
        {inGame && <span className={styles.phase}>{gameState?.phase}</span>}
        <div className={styles.headerRight}>
          <LanguageSwitcher />
          <button className={styles.leaveBtn} onClick={onLeave}>{t('game:leaveRoom')}</button>
        </div>
      </header>

      {showWaitingRoom ? (
        <div className={styles.waitingRoom}>
          {sessionSummary && (
            <div className={styles.inlineSummary}>
              <h3 className={styles.inlineSummaryTitle}>{t('game:lastSummary')}</h3>
              <div className={styles.inlineSummaryMeta}>
                <span>{t('game:totalHands')}: {sessionSummary.totalHands}</span>
                <span>{t('game:duration')}: {formatDuration(sessionSummary.durationMs)}</span>
              </div>
              <div className={styles.inlineSummaryTable}>
                <div className={styles.inlineSummaryHeader}>
                  <span>{t('game:playerHeader')}</span>
                  <span>{t('game:startingChips')}</span>
                  <span>{t('game:finalChips')}</span>
                  <span>{t('game:netProfit')}</span>
                </div>
                {sessionSummary.players
                  .sort((a, b) => b.finalChips - a.finalChips)
                  .map(p => (
                  <div key={p.seatId} className={`${styles.inlineSummaryRow} ${p.busted ? styles.bustedRow : ''}`}>
                    <span className={styles.summaryName}>
                      {p.seatId}{p.seatId === userId && t('game:youSuffix')}{p.busted && ' 💀'}
                    </span>
                    <span>{p.startingChips}</span>
                    <span>{p.finalChips}</span>
                    <span className={p.netChange >= 0 ? styles.positive : styles.negative}>
                      {p.netChange >= 0 ? '+' : ''}{p.netChange}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}

          <div className={styles.playerList}>
            <h3 className={styles.playerListTitle}>
              {t('game:playerCount', { current: roomState.players.length, max: roomState.maxPlayers })}
            </h3>
            {roomState.players.map(p => (
              <div key={p.seatId} className={styles.playerSlot}>
                <span className={styles.slotName}>
                  {p.seatId}
                  {p.host && <span className={styles.hostBadge}>{t('common:host')}</span>}
                  {p.ai && <span className={p.agentType === 'react' ? styles.aiBadgeReact : styles.aiBadgeSimple}>
                    {p.agentType === 'react' ? 'ReAct' : 'Simple'}
                  </span>}
                  {p.seatId === userId && t('game:youSuffix')}
                </span>
                <span className={`${styles.readyStatus} ${p.ready ? styles.ready : styles.notReady}`}>
                  {p.ready ? t('common:ready') : t('common:notReady')}
                </span>
                {isHost && p.ai && (
                  <button className={styles.removeBotBtn} onClick={() => handleRemoveBot(p.seatId)}>
                    {t('common:remove')}
                  </button>
                )}
              </div>
            ))}
          </div>

          <div className={styles.waitingActions}>
            {!isHost && (
              <button
                className={myReady ? styles.unreadyBtn : styles.readyBtn}
                onClick={handleReady}
              >
                {myReady ? t('game:unready') : t('game:readyBtn')}
              </button>
            )}
            {isHost && (
              <>
                <button
                  className={styles.addBotBtn}
                  onClick={() => handleAddBot('simple')}
                  disabled={roomState.players.length >= roomState.maxPlayers}
                >
                  {t('game:addSimpleAI')}
                </button>
                <button
                  className={styles.addBotBtnReact}
                  onClick={() => handleAddBot('react')}
                  disabled={roomState.players.length >= roomState.maxPlayers}
                >
                  {t('game:addReActAgent')}
                </button>
                <button
                  className={styles.startBtn}
                  onClick={handleStartGame}
                  disabled={!canStart}
                >
                  {sessionSummary ? t('game:startNewSession') : t('game:startGame')}
                </button>
              </>
            )}
            {isHost && !canStart && roomState.players.length < 2 && (
              <div className={styles.waitingHint}>{t('game:addBotHint')}</div>
            )}
            {isHost && !canStart && roomState.players.length >= 2 && (
              <div className={styles.waitingHint}>{t('game:waitingReady')}</div>
            )}
          </div>
        </div>
      ) : (
        <>
          <div className={styles.table}>
            <div className={styles.opponentsRow}>
              {opponents.map(p => (
                <Player
                  key={p.seatId}
                  player={p}
                  isCurrentPlayer={p.seatId === gameState?.currentPlayerId}
                  isSelf={false}
                />
              ))}
            </div>

            <Board cards={gameState?.board} pot={displayPot} />

            <div className={styles.selfRow}>
              {self && (
                <Player
                  player={self}
                  isCurrentPlayer={self.seatId === gameState?.currentPlayerId}
                  isSelf={true}
                />
              )}
            </div>

            {gameResult && (
              <div className={styles.resultBanner}>
                <span className={styles.resultTitle}>
                  {gameResult.winnerSeatId === userId
                    ? t('game:youWon')
                    : t('game:playerWon', { name: gameResult.winnerSeatId })}
                </span>
                {gameResult.chipChanges && (() => {
                  const winnerGain = gameResult.chipChanges[gameResult.winnerSeatId];
                  return (
                    <>
                      <span className={styles.resultPot}>
                        {t('game:wonPot', { amount: winnerGain >= 0 ? winnerGain : gameResult.potAmount })}
                      </span>
                      <span className={styles.chipChanges}>
                        {Object.entries(gameResult.chipChanges).map(([id, change]) => (
                          <span key={id} className={change >= 0 ? styles.positive : styles.negative}>
                            {id}: {change >= 0 ? '+' : ''}{change}
                          </span>
                        ))}
                      </span>
                    </>
                  );
                })()}
              </div>
            )}
          </div>

          <div className={styles.bottomPanel}>
            {isSettled ? (
              <div className={styles.waitingArea}>
                <div className={styles.waitingInfo}>
                  {isHost ? t('game:hostStartHint') : t('game:waitingHostAction')}
                </div>
                {isHost && (
                  <button className={styles.startBtn} onClick={handleStartGame}>{t('game:startNext')}</button>
                )}
              </div>
            ) : (
              <ActionBar
                gameState={isMyTurn ? gameState : null}
                onAction={handleAction}
                disabled={!isMyTurn}
              />
            )}
            {!isSettled && (
              <HintPanel
                hint={hint}
                onRequestHint={handleRequestHint}
                canRequest={true}
              />
            )}
          </div>
        </>
      )}
    </div>
  );
}
