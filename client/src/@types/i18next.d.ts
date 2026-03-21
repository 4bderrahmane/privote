import 'i18next';
import common from '../../public/locales/en/common.json';
import auth from '../../public/locales/en/auth.json';
import nav from '../../public/locales/en/nav.json';
import settings from '../../public/locales/en/settings.json';
import dashboard from '../../public/locales/en/dashboard.json';

declare module 'i18next' {
    interface CustomTypeOptions {
        defaultNS: 'common';
        resources: {
            common: typeof common;
            auth: typeof auth;
            nav: typeof nav;
            settings: typeof settings;
            dashboard: typeof dashboard;
        };
    }
}
