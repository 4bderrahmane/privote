import { Outlet } from "react-router-dom";
import Navbar from "../components/NavBar.tsx";
import "../styles/Layout.css";

export default function CitizenLayout() {
    return (
        <div className="dashboard-container">
            <div className="main-content">
                <Navbar />
                <Outlet />
            </div>
        </div>
    );
}

