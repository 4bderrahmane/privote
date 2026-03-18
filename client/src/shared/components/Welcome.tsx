// import{TypeAnimation} from 'react-type-animation';
// import {useTranslation} from 'react-i18next';
//
// function Welcome() {
//     const {t, i18n} = useTranslation('auth');
//
//     return (
//         <div className="text-xl font-mono">
//             <TypeAnimation
//                 key={i18n.language}
//                 sequence={[
//                     t('welcome.welcome'),
//                     1000,
//                 ]}
//                 speed={40}
//                 repeat={0}
//             />
//         </div>
//     );
// }
// export default Welcome;
import { TypeAnimation } from "react-type-animation";
import { useTranslation } from "react-i18next";
import { keycloak } from "@/auth/keycloak";
import type {KeycloakTokenParsed} from "keycloak-js";

function Welcome() {
    const { t, i18n } = useTranslation("auth");

    // Usually: given_name is on the ID token (idTokenParsed). Fallback to tokenParsed if needed.
    const givenName =
        (keycloak?.idTokenParsed as KeycloakTokenParsed)?.given_name ??
        (keycloak?.tokenParsed as KeycloakTokenParsed)?.given_name ??
        "";

    const text = t("welcome.welcome", { name: givenName || "Guest" });

    return (
        <div>
            <TypeAnimation
                key={`${i18n.language}-${givenName}`} // rerun animation if language or name changes
                sequence={[text, 1000]}
                speed={40}
                repeat={0}
            />
        </div>
    );
}

export default Welcome;
