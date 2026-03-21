import 'i18next';
import common from '../../public/locales/en/common.json';
import auth from '../../public/locales/en/auth.json';
import nav from '../../public/locales/en/nav.json';
import settings from '../../public/locales/en/settings.json';
import elections from '../../public/locales/en/elections.json';
import dashboard from '../../public/locales/en/dashboard.json';
import parties from '../../public/locales/en/parties.json';

declare module 'i18next' {
    interface CustomTypeOptions {
        defaultNS: 'common';
        resources: {
            common: typeof common;
            auth: typeof auth;
            nav: typeof nav;
            settings: typeof settings;
            elections: typeof elections;
            dashboard: typeof dashboard;
            parties: typeof parties;
        };
    }
}
