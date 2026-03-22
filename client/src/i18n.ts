import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import HttpBackend from 'i18next-http-backend';
import LanguageDetector from 'i18next-browser-languagedetector';

const localeVersion = String(import.meta.env.VITE_LOCALE_VERSION ?? Date.now());

i18n
    .use(HttpBackend)
    .use(LanguageDetector)
    .use(initReactI18next)
    .init({
        fallbackLng: 'en',
        debug: import.meta.env.DEV,
        backend: {
            loadPath: `/locales/{{lng}}/{{ns}}.json?v=${localeVersion}`,
        },
        load: 'languageOnly',
        ns: ['common', 'auth', 'nav', 'settings', 'elections', 'dashboard', 'parties'],
        defaultNS: 'common',
    });

export default i18n;
