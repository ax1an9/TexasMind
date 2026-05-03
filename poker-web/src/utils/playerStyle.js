export function classifyPlayerStyle(vpip, pfr) {
  const tightness = vpip < 0.14 ? 'tight' : vpip < 0.23 ? 'medium' : 'loose';
  const pfrVpipRatio = vpip > 0 ? pfr / vpip : 0;
  const aggressiveness = pfrVpipRatio > 0.75 ? 'aggressive' : 'passive';

  const styleMap = {
    'tight-passive': { name: 'Rock', emoji: '🪨', desc: '紧而被动，只玩好牌但下注保守' },
    'tight-aggressive': { name: 'TAG', emoji: '🎯', desc: '紧而激进，精选好牌积极下注' },
    'medium-passive': { name: 'Calling Station', emoji: '📞', desc: '中等频率，倾向于跟注' },
    'medium-aggressive': { name: 'TAG', emoji: '🎯', desc: '中等频率，积极下注' },
    'loose-passive': { name: 'Calling Station', emoji: '📞', desc: '松而被动，玩很多牌但很少加注' },
    'loose-aggressive': { name: 'LAG', emoji: '🔥', desc: '松而激进，频繁下注施压' },
  };

  return styleMap[`${tightness}-${aggressiveness}`] || { name: 'Unknown', emoji: '❓', desc: '数据不足' };
}

export function formatPercent(value) {
  return (value * 100).toFixed(1) + '%';
}
