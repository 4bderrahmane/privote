import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import HttpBackend from 'i18next-http-backend';
import LanguageDetector from 'i18next-browser-languagedetector';

i18n
    .use(HttpBackend) // Tells i18next to load JSON from URL/Path
    .use(LanguageDetector)
    .use(initReactI18next)
    .init({
        fallbackLng: 'en',
        debug: import.meta.env.DEV,
        // This is where the magic happens
        // {{lng}} and {{ns}} are replaced automatically by i18next
        backend: {
            loadPath: '/locales/{{lng}}/{{ns}}.json',
        },
        load: 'languageOnly',
        // Optimization: explicitly state namespaces to avoid extra network requests
        ns: ['common', 'auth', 'nav', 'settings'],
        defaultNS: 'common',

        // interpolation: {
        //     escapeValue: false,
        // },
    });

export default i18n;
