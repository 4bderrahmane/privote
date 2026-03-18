import React from "react";
import { NavLink } from "react-router-dom";
import { useTranslation } from "react-i18next";
import "../../styles/settings/SettingsDropDown.css";

interface SettingsDropDownProps {
    onClose?: () => void;
}

const SettingsDropdown: React.FC<SettingsDropDownProps> = ({ onClose }) => {
    const { t } = useTranslation("settings");

    const handleClick = () => {
        if (onClose) onClose();
    };

    return (
        <div className="settings-nav-container">
            <div className="settings-nav">
                <NavLink to="/settings/profile" className="settings-nav-link" onClick={handleClick}>
                    {t("editProfile")}
                </NavLink>
                <NavLink to="/settings/delete" className="settings-nav-link settings-nav-link-delete" onClick={handleClick}>
                    {t("deleteAccount")}
                </NavLink>
            </div>
        </div>
    );
};

export default SettingsDropdown;
