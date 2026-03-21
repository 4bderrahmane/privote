import { createBrowserRouter, Navigate } from "react-router-dom";

import RouteErrorElement from "./RouteErrorElement";

import PublicLayout from "../shared/layouts/PublicLayout";
import DashboardLayout from "../shared/layouts/DashboardLayout";
import AdminLayout from "../shared/layouts/AdminLayout";
import CitizenLayout from "../shared/layouts/CitizenLayout";

import PublicOnly from "../auth/guards/PublicOnly.tsx";
import RequireAuth from "../auth/guards/RequireAuth.tsx";
import EnsureActiveRole from "../auth/guards/EnsureActiveRole.tsx";
import RequireRole from "../auth/guards/RequireRole.tsx";

import WelcomePage from "@/auth/components/WelcomePage";
import LoginRedirect from "@/auth/components/LoginRedirect";
import RegisterRedirect from "@/auth/components/RegisterRedirect";

import LogoutPage from "@/auth/components/LogoutPage";
import AdminDashboard from "../shared/components/AdminDashboard";
import CitizenDashboard from "../shared/components/CitizenDashboard";
import CreateElection from "../shared/components/CreateElection";
import ChooseRole from "../shared/components/ChooseRole";
import DashboardRedirect from "../shared/components/DashboardRedirect";
import NotFoundPage from "../shared/components/NotFoundPage";
import Elections from "../shared/components/Elections";
import Parties from "../shared/components/Parties";
import Results from "../shared/components/Results";
import MyVotes from "../shared/components/MyVotes";
import RoleRouteRedirect from "../shared/components/RoleRouteRedirect";
import Settings from "@/user-management/components/settings/Settings.tsx";

const router = createBrowserRouter([
    {
        errorElement: <RouteErrorElement />,
        children: [
            {
                element: <PublicLayout />,
                children: [
                    {
                        element: <PublicOnly />,
                        children: [
                            { path: "/", element: <WelcomePage /> },
                            { path: "/login", element: <LoginRedirect /> },
                            { path: "/register", element: <RegisterRedirect /> },
                        ],
                    },
                    { path: "*", element: <NotFoundPage /> },
                ],
            },

            {
                path: "/dashboard",
                element: <RequireAuth />,
                children: [{ index: true, element: <DashboardRedirect /> }],
            },

            {
                path: "/choose-role",
                element: <RequireAuth />,
                children: [{ index: true, element: <ChooseRole /> }],
            },

            {
                path: "/elections",
                element: <RequireAuth />,
                children: [{ index: true, element: <RoleRouteRedirect target="elections" /> }],
            },

            {
                path: "/results",
                element: <RequireAuth />,
                children: [{ index: true, element: <RoleRouteRedirect target="results" /> }],
            },

            {
                path: "/my-votes",
                element: <RequireAuth />,
                children: [{ index: true, element: <RoleRouteRedirect target="my-votes" /> }],
            },

            {
                path: "/admin",
                element: <RequireAuth />,
                children: [
                    {
                        element: <EnsureActiveRole />,
                        children: [
                            {
                                element: <RequireRole allow={["admin"]} />,
                                children: [
                                    {
                                        element: <AdminLayout />,
                                        children: [
                                            { index: true, element: <Navigate to="dashboard" replace /> },
                                            { path: "dashboard", element: <AdminDashboard /> },
                                            { path: "elections", element: <Elections /> },
                                            { path: "elections/create", element: <CreateElection /> },
                                            { path: "parties", element: <Parties /> },
                                            { path: "results", element: <Results /> },
                                            { path: "*", element: <NotFoundPage /> },
                                        ],
                                    },
                                ],
                            },
                        ],
                    },
                ],
            },

            {
                path: "/citizen",
                element: <RequireAuth />,
                children: [
                    {
                        element: <EnsureActiveRole />,
                        children: [
                            {
                                element: <RequireRole allow={["citizen"]} />,
                                children: [
                                    {
                                        element: <CitizenLayout />,
                                        children: [
                                            { index: true, element: <Navigate to="dashboard" replace /> },
                                            { path: "dashboard", element: <CitizenDashboard /> },
                                            { path: "elections", element: <Elections /> },
                                            { path: "results", element: <Results /> },
                                            { path: "my-votes", element: <MyVotes /> },
                                            { path: "*", element: <NotFoundPage /> },
                                        ],
                                    },
                                ],
                            },
                        ],
                    },
                ],
            },

            {
                path: "/settings",
                element: <RequireAuth />,
                children: [
                    {
                        element: <EnsureActiveRole />,
                        children: [
                            {
                                element: <DashboardLayout />,
                                children: [
                                    { index: true, element: <Settings section="profile" /> },
                                    { path: "profile", element: <Settings section="profile" /> },
                                    { path: "delete", element: <Settings section="delete" /> },
                                    { path: "*", element: <NotFoundPage /> },
                                ],
                            },
                        ],
                    },
                ],
            },

            { path: "/logout", element: <LogoutPage /> },
        ],
    },
]);

export default router;
