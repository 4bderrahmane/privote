import React, { useState } from "react";
import { Link, NavLink } from "react-router-dom";
import { useTranslation } from "react-i18next";
import "../styles/NavBar.css";
import LanguageSwitcher from "./LanguageSwitcher.tsx";
import { useSuccessToast } from "../hooks/useSuccessToast";
import { useAuth } from "@/auth/useAuth.ts";
import { clearActiveRole, getActiveRole, getAvailableRoles, getRoleBasePath } from "@/auth/roles.ts";
import useOutsideClick from "../hooks/useOutsideClick";
import { MdDeleteOutline, MdHowToVote, MdOutlinePerson, MdPerson, MdLogout } from "react-icons/md";

const NavBar: React.FC = () => {
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);
  const { t, i18n } = useTranslation(["auth", "nav", "settings"]);
  const auth = useAuth();
  const { showSuccessToast } = useSuccessToast();

  const closeDropdown = () => setIsDropdownOpen(false);
  const toggleDropdown = () => setIsDropdownOpen((v) => !v);
  const dropdownRef = useOutsideClick(closeDropdown);

  const isAuthed = auth.status === "authenticated";
  const username = isAuthed ? auth.user.name ?? "User" : "Guest";
  const availableRoles = isAuthed ? getAvailableRoles(auth.user) : [];
  const activeRole = isAuthed ? getActiveRole(auth.user) : null;
  const resolvedRole = activeRole ?? availableRoles[0] ?? null;
  const basePath = resolvedRole ? getRoleBasePath(resolvedRole) : "";
  const homePath = resolvedRole ? `${basePath}/dashboard` : isAuthed ? "/choose-role" : "/";
  const navItems =
    resolvedRole === "admin"
      ? [
          { to: `${basePath}/dashboard`, label: t("nav:dashboard") },
          { to: `${basePath}/elections`, label: t("nav:elections") },
          {
            to: `${basePath}/parties`,
            label: t("parties", {
              ns: "nav",
              defaultValue: i18n.resolvedLanguage === "fr" ? "Partis" : "Parties",
            }),
          },
          { to: `${basePath}/results`, label: t("nav:results") },
        ]
      : [
          { to: `${basePath}/dashboard`, label: t("nav:dashboard") },
          { to: `${basePath}/elections`, label: t("nav:elections") },
          { to: `${basePath}/my-votes`, label: t("nav:myVotes") },
          { to: `${basePath}/results`, label: t("nav:results") },
        ];

  const handleLogout = async () => {
    closeDropdown();

    // NOTE: you usually won't see this toast because logout redirects away immediately.
    showSuccessToast(t("success.logoutSuccess"));

    try {
      if (auth.status === "authenticated") {
        clearActiveRole(auth.user);
      }
      await auth.logout(); // There is a default uri btw
    } catch (error) {
      console.error("Logout request failed:", error);
    }
  };

  if (auth.status === "checking") return null;

  if (!isAuthed) {
    return (
      <nav className="navbar navbar-guest">
        <div className="navbar-shell navbar-shell-guest">
          <Link to="/" className="navbar-brand-link">
            <span className="navbar-brand-mark" aria-hidden="true">
              <MdHowToVote />
            </span>
            <span className="navbar-brand-text">Privote</span>
          </Link>

          <div className="navbar-actions navbar-actions-guest">
            <LanguageSwitcher />
            <Link to="/login" className="auth-link auth-link-secondary">
              {t("auth:login")}
            </Link>
            <Link to="/register" className="auth-link auth-link-primary">
              {t("auth:register")}
            </Link>
          </div>
        </div>
      </nav>
    );
  }

  return (
    <nav className="navbar navbar-auth">
      <div className="navbar-shell">
        <Link to={homePath} className="navbar-brand-link">
          <span className="navbar-brand-mark" aria-hidden="true">
            <MdHowToVote />
          </span>
          <span className="navbar-brand-text">Privote</span>
        </Link>

        <div className="navbar-menu">
          <ul className="navbar-links">
            {navItems.map((item) => (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  end
                  className={({ isActive }) => (isActive ? "navbar-link active" : "navbar-link")}
                >
                  {item.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </div>

        <div className="navbar-actions">
          <LanguageSwitcher />

          <div className="navbar-user" ref={dropdownRef}>
            <button
              type="button"
              className="user-profile"
              onClick={toggleDropdown}
              aria-expanded={isDropdownOpen}
              aria-haspopup="menu"
            >
              <MdOutlinePerson className="user-profile-icon" />
              <span className="user-name">{username}</span>
              <span className={isDropdownOpen ? "dropdown-icon open" : "dropdown-icon"}>▾</span>
            </button>

            {isDropdownOpen && (
              <div className="dropdown-menu" role="menu">
                <Link to="/settings/profile" className="dropdown-item" onClick={closeDropdown}>
                  <MdPerson className="dropdown-icon-svg" />
                  <span>{t("profile", { ns: "nav" })}</span>
                </Link>

                <Link to="/settings/delete" className="dropdown-item dropdown-item-danger" onClick={closeDropdown}>
                  <MdDeleteOutline className="dropdown-icon-svg" />
                  <span>{t("deleteAccount", { ns: "settings" })}</span>
                </Link>

                <div className="dropdown-divider"></div>

                <button className="dropdown-item logout-button" onClick={handleLogout}>
                  <MdLogout className="dropdown-icon-svg" />
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
