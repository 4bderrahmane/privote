import { TypeAnimation } from "react-type-animation";
import { useTranslation } from "react-i18next";
import { keycloak } from "@/auth/keycloak";
import type {KeycloakTokenParsed} from "keycloak-js";

function Welcome() {
    const { t, i18n } = useTranslation("auth");

    const givenName =
        (keycloak?.idTokenParsed as KeycloakTokenParsed)?.given_name ??
        (keycloak?.tokenParsed as KeycloakTokenParsed)?.given_name ??
        "";

    const text = t("welcome.welcome", { name: givenName || "Guest" });

    return (
        <div>
            <TypeAnimation
                key={`${i18n.language}-${givenName}`}
                sequence={[text, 1000]}
                speed={40}
                repeat={0}
            />
        </div>
    );
}

export default Welcome;