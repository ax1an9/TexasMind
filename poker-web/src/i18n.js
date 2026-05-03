import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';

import zhCNCommon from './locales/zh-CN/common.json';
import zhCNGame from './locales/zh-CN/game.json';
import zhCNLobby from './locales/zh-CN/lobby.json';
import zhCNProfile from './locales/zh-CN/profile.json';
import zhCNHint from './locales/zh-CN/hint.json';
import zhCNRoom from './locales/zh-CN/room.json';

import zhTWCommon from './locales/zh-TW/common.json';
import zhTWGame from './locales/zh-TW/game.json';
import zhTWLobby from './locales/zh-TW/lobby.json';
import zhTWProfile from './locales/zh-TW/profile.json';
import zhTWHint from './locales/zh-TW/hint.json';
import zhTWRoom from './locales/zh-TW/room.json';

import enCommon from './locales/en/common.json';
import enGame from './locales/en/game.json';
import enLobby from './locales/en/lobby.json';
import enProfile from './locales/en/profile.json';
import enHint from './locales/en/hint.json';
import enRoom from './locales/en/room.json';

const resources = {
  'zh-CN': {
    common: zhCNCommon,
    game: zhCNGame,
    lobby: zhCNLobby,
    profile: zhCNProfile,
    hint: zhCNHint,
    room: zhCNRoom,
  },
  'zh-TW': {
    common: zhTWCommon,
    game: zhTWGame,
    lobby: zhTWLobby,
    profile: zhTWProfile,
    hint: zhTWHint,
    room: zhTWRoom,
  },
  en: {
    common: enCommon,
    game: enGame,
    lobby: enLobby,
    profile: enProfile,
    hint: enHint,
    room: enRoom,
  },
};

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources,
    fallbackLng: 'zh-CN',
    defaultNS: 'common',
    ns: ['common', 'game', 'lobby', 'profile', 'hint', 'room'],
    interpolation: {
      escapeValue: false,
    },
    detection: {
      order: ['localStorage', 'navigator'],
      lookupLocalStorage: 'poker-lang',
      caches: ['localStorage'],
    },
  });

export default i18n;
