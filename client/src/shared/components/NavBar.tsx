import React, { useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { useTranslation } from "react-i18next";
import "../styles/NavBar.css";
import LanguageSwitcher from "./LanguageSwitcher.tsx";
import Welcome from "./Welcome";
import { useSuccessToast } from "../hooks/useSuccessToast";
import { useAuth } from "@/auth/useAuth.ts";
import useOutsideClick from "../hooks/useOutsideClick";
import { MdSettings, MdPerson, MdLogout } from "react-icons/md";
import SettingsDropDown from "@/user-management/components/settings/SettingsDropDown.tsx";

const NavBar: React.FC = () => {
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);
  const location = useLocation();
  const { t } = useTranslation(["auth", "nav"]);
  const auth = useAuth();
  const { showSuccessToast } = useSuccessToast();

  const closeDropdown = () => setIsDropdownOpen(false);
  const toggleDropdown = () => setIsDropdownOpen((v) => !v);
  const dropdownRef = useOutsideClick(closeDropdown);

  const isCurrentPage = (path: string) => location.pathname === path;

  const isAuthed = auth.status === "authenticated";
  const username = isAuthed ? auth.user.name ?? "User" : "Guest";

  const handleLogout = async () => {
    closeDropdown();

    // NOTE: you usually won't see this toast because logout redirects away immediately.
    showSuccessToast(t("success.logoutSuccess"));

    try {
      await auth.logout(); // There is a default uri btw
    } catch (error) {
      console.error("Logout request failed:", error);
    }
  };

  if (auth.status === "checking") return null;

  if (!isAuthed) {
    return (
      <nav className="navbar">
        <div className="navbar-brand">
          <Link to="/">
            <Welcome />
          </Link>
        </div>
        <div className="navbar-actions">
          <LanguageSwitcher />
          <Link to="/login">{t("auth:login")}</Link>
          <Link to="/register">{t("auth:register")}</Link>
        </div>
      </nav>
    );
  }

  return (
    <nav className="navbar">
      <div className="navbar-brand">
        <Link to="/dashboard">
          <Welcome />
        </Link>
      </div>

      <div className="navbar-menu">
        <ul className="navbar-links">
          <li className={isCurrentPage("/dashboard") ? "active" : ""}>
            <Link to="/dashboard">{t("nav:dashboard")}</Link>
          </li>
          <li className={isCurrentPage("/elections") ? "active" : ""}>
            <Link to="/elections">{t("nav:elections")}</Link>
          </li>
          <li className={isCurrentPage("/results") ? "active" : ""}>
            <Link to="/results">{t("nav:results")}</Link>
          </li>
          <li className={isCurrentPage("/my-votes") ? "active" : ""}>
            <Link to="/my-votes">{t("nav:myVotes")}</Link>
          </li>
        </ul>

        <div className="navbar-actions">
          <LanguageSwitcher />

          <div className="navbar-user" ref={dropdownRef}>
            <button className="user-profile" onClick={toggleDropdown}>
              <span className="user-name">{username}</span>
              <span className="dropdown-icon">▼</span>
            </button>

            {isDropdownOpen && (
              <div className="dropdown-menu">
                <Link
                  to="/settings/profile"
                  className="dropdown-item"
                  onClick={closeDropdown}
                >
                  <MdPerson className="dropdown-icon-svg" />{" "}
                  <span>{t("profile", { ns: "nav" })}</span>
                </Link>

                <div className="dropdown-item dropdown-parent settings-parent">
                  <MdSettings className="dropdown-icon-svg" />
                  <span>{t("nav:settings")}</span>
                  <div className="dropdown-submenu">
                    <SettingsDropDown onClose={closeDropdown} />
                  </div>
                </div>

                <div className="dropdown-divider"></div>

                <button
                  className="dropdown-item logout-button"
                  onClick={handleLogout}
                >
                  <MdLogout className="dropdown-icon-svg" />{" "}
                  <span>{t("auth:logout")}</span>
                </button>
              </div>
            )}
          </div>
        </div>
      </div>
    </nav>
  );
};

export default NavBar;
