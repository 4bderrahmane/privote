import { createBrowserRouter } from "react-router-dom";

import RouteErrorElement from "./RouteErrorElement";

import PublicLayout from "../shared/layouts/PublicLayout";
import DashboardLayout from "../shared/layouts/DashboardLayout";

import PublicOnly from "../auth/guards/PublicOnly.tsx";
import RequireAuth from "../auth/guards/RequireAuth.tsx";

import WelcomePage from "@/auth/components/WelcomePage";
import LoginRedirect from "@/auth/components/LoginRedirect";
import RegisterRedirect from "@/auth/components/RegisterRedirect";

import LogoutPage from "@/auth/components/LogoutPage";
import Dashboard from "../shared/components/Dashboard"
import NotFoundPage from "../shared/components/NotFoundPage";
import Elections from "../shared/components/Elections";
import Results from "../shared/components/Results";
import MyVotes from "../shared/components/MyVotes";
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
                children: [
                    {
                        element: <DashboardLayout />,
                        children: [
                            { index: true, element: <Dashboard /> },
                            // { path: "profile", element: <ProfilePage /> },
                            { path: "*", element: <NotFoundPage /> }
                        ],
                    },
                ],
            },

            {
                path: "/elections",
                element: <RequireAuth />,
                children: [
                    {
                        element: <DashboardLayout />,
                        children: [
                            { index: true, element: <Elections /> },
                            { path: "*", element: <NotFoundPage /> },
                        ],
                    },
                ],
            },

            {
                path: "/results",
                element: <RequireAuth />,
                children: [
                    {
                        element: <DashboardLayout />,
                        children: [
                            { index: true, element: <Results /> },
                            { path: "*", element: <NotFoundPage /> },
                        ],
                    },
                ],
            },

            {
                path: "/my-votes",
                element: <RequireAuth />,
                children: [
                    {
                        element: <DashboardLayout />,
                        children: [
                            { index: true, element: <MyVotes /> },
                            { path: "*", element: <NotFoundPage /> },
                        ],
                    },
                ],
            },

            {
                path: "/settings",
                element: <RequireAuth />,
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

            { path: "/logout", element: <LogoutPage /> },
        ],
    },
]);

export default router;
