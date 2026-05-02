import { useState, useEffect, useCallback, useRef } from 'react';
import { useWebSocket } from '../context/WebSocketContext';
import Board from '../components/Board';
import Player from '../components/Player';
import ActionBar from '../components/ActionBar';
import HintPanel from '../components/HintPanel';
import styles from './Game.module.css';

export default function Game({ roomId, roomName, initialRoomState, onLeave }) {
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

    // User-specific room state (sent on join/create to avoid race condition)
    const unsub5 = subscribe('/user/queue/room-state', (msg) => {
      const data = JSON.parse(msg.body);
      setRoomState(data);
    });

    // Request room state on mount if not already available
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
    return mins > 0 ? `${mins}分${remainSecs}秒` : `${remainSecs}秒`;
  };

  const showWaitingRoom = !inGame && roomState;

  return (
    <div className={styles.game}>
      <header className={styles.header}>
        <span className={styles.roomName}>房间: {roomState?.roomName || roomName || roomId}</span>
        {inGame && <span className={styles.phase}>{gameState?.phase}</span>}
        <button className={styles.leaveBtn} onClick={onLeave}>离开房间</button>
      </header>

      {showWaitingRoom ? (
        <div className={styles.waitingRoom}>
          {/* Session Summary (shown inline if available) */}
          {sessionSummary && (
            <div className={styles.inlineSummary}>
              <h3 className={styles.inlineSummaryTitle}>上局总结</h3>
              <div className={styles.inlineSummaryMeta}>
                <span>总局数: {sessionSummary.totalHands}</span>
                <span>时长: {formatDuration(sessionSummary.durationMs)}</span>
              </div>
              <div className={styles.inlineSummaryTable}>
                <div className={styles.inlineSummaryHeader}>
                  <span>玩家</span>
                  <span>起始</span>
                  <span>最终</span>
                  <span>净盈亏</span>
                </div>
                {sessionSummary.players
                  .sort((a, b) => b.finalChips - a.finalChips)
                  .map(p => (
                  <div key={p.seatId} className={`${styles.inlineSummaryRow} ${p.busted ? styles.bustedRow : ''}`}>
                    <span className={styles.summaryName}>
                      {p.seatId}{p.seatId === userId && ' (你)'}{p.busted && ' 💀'}
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

          {/* Player List */}
          <div className={styles.playerList}>
            <h3 className={styles.playerListTitle}>
              玩家 ({roomState.players.length}/{roomState.maxPlayers})
            </h3>
            {roomState.players.map(p => (
              <div key={p.seatId} className={styles.playerSlot}>
                <span className={styles.slotName}>
                  {p.seatId}
                  {p.host && <span className={styles.hostBadge}>房主</span>}
                  {p.ai && <span className={p.agentType === 'react' ? styles.aiBadgeReact : styles.aiBadgeSimple}>
                    {p.agentType === 'react' ? 'ReAct' : 'Simple'}
                  </span>}
                  {p.seatId === userId && ' (你)'}
                </span>
                <span className={`${styles.readyStatus} ${p.ready ? styles.ready : styles.notReady}`}>
                  {p.ready ? '已准备' : '未准备'}
                </span>
                {isHost && p.ai && (
                  <button className={styles.removeBotBtn} onClick={() => handleRemoveBot(p.seatId)}>
                    移除
                  </button>
                )}
              </div>
            ))}
          </div>

          {/* Actions */}
          <div className={styles.waitingActions}>
            {!isHost && (
              <button
                className={myReady ? styles.unreadyBtn : styles.readyBtn}
                onClick={handleReady}
              >
                {myReady ? '取消准备' : '准备'}
              </button>
            )}
            {isHost && (
              <>
                <button
                  className={styles.addBotBtn}
                  onClick={() => handleAddBot('simple')}
                  disabled={roomState.players.length >= roomState.maxPlayers}
                >
                  + Simple AI
                </button>
                <button
                  className={styles.addBotBtnReact}
                  onClick={() => handleAddBot('react')}
                  disabled={roomState.players.length >= roomState.maxPlayers}
                >
                  + ReAct Agent
                </button>
                <button
                  className={styles.startBtn}
                  onClick={handleStartGame}
                  disabled={!canStart}
                >
                  {sessionSummary ? '开始新一局' : '开始游戏'}
                </button>
              </>
            )}
            {isHost && !canStart && roomState.players.length < 2 && (
              <div className={styles.waitingHint}>请添加AI或等待其他玩家加入</div>
            )}
            {isHost && !canStart && roomState.players.length >= 2 && (
              <div className={styles.waitingHint}>等待所有玩家准备</div>
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
                  {gameResult.winnerSeatId === userId ? '你赢了!' : `${gameResult.winnerSeatId} 赢了`}
                </span>
                {gameResult.chipChanges && (() => {
                  const winnerGain = gameResult.chipChanges[gameResult.winnerSeatId];
                  return (
                    <>
                      <span className={styles.resultPot}>
                        赢得 ${winnerGain >= 0 ? winnerGain : gameResult.potAmount}
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
                  {isHost ? '点击"开始下一局"' : '等待房主开始下一局'}
                </div>
                {isHost && (
                  <button className={styles.startBtn} onClick={handleStartGame}>开始下一局</button>
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
