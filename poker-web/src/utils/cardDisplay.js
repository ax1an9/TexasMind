const SUIT_SYMBOLS = {
  HEARTS: '♥',
  DIAMONDS: '♦',
  CLUBS: '♣',
  SPADES: '♠'
};

const SUIT_COLORS = {
  HEARTS: '#e74c3c',
  DIAMONDS: '#e74c3c',
  CLUBS: '#2c3e50',
  SPADES: '#2c3e50'
};

const RANK_DISPLAY = {
  TWO: '2', THREE: '3', FOUR: '4', FIVE: '5', SIX: '6',
  SEVEN: '7', EIGHT: '8', NINE: '9', TEN: '10',
  JACK: 'J', QUEEN: 'Q', KING: 'K', ACE: 'A'
};

export function getSuitSymbol(suit) {
  return SUIT_SYMBOLS[suit] || suit;
}

export function getSuitColor(suit) {
  return SUIT_COLORS[suit] || '#2c3e50';
}

export function getRankDisplay(rank) {
  return RANK_DISPLAY[rank] || rank;
}

export function isRedSuit(suit) {
  return suit === 'HEARTS' || suit === 'DIAMONDS';
}
