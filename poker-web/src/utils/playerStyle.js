import i18n from '../i18n';

const STYLE_KEYS = {
  'tight-passive': { name: 'Rock', emoji: '🪨', descKey: 'styleRock' },
  'tight-aggressive': { name: 'TAG', emoji: '🎯', descKey: 'styleTAG' },
  'medium-passive': { name: 'Calling Station', emoji: '📞', descKey: 'styleMediumPassive' },
  'medium-aggressive': { name: 'TAG', emoji: '🎯', descKey: 'styleMediumAggressive' },
  'loose-passive': { name: 'Calling Station', emoji: '📞', descKey: 'styleLoosePassive' },
  'loose-aggressive': { name: 'LAG', emoji: '🔥', descKey: 'styleLooseAggressive' },
};

export function classifyPlayerStyle(vpip, pfr) {
  const tightness = vpip < 0.14 ? 'tight' : vpip < 0.23 ? 'medium' : 'loose';
  const pfrVpipRatio = vpip > 0 ? pfr / vpip : 0;
  const aggressiveness = pfrVpipRatio > 0.75 ? 'aggressive' : 'passive';

  const style = STYLE_KEYS[`${tightness}-${aggressiveness}`];
  if (!style) {
    return { name: 'Unknown', emoji: '❓', desc: i18n.t('profile:insufficientData') };
  }
  return { name: style.name, emoji: style.emoji, desc: i18n.t(`profile:${style.descKey}`) };
}

export function formatPercent(value) {
  return (value * 100).toFixed(1) + '%';
}
