// import type {UserResponseDTO} from "@/features/authentication/types/api.types.ts";
import type {ReactNode} from "react";
import type {UserResponseDTO} from "@/user-management/types/types.ts";

export type Language = {
    code: string;
    name: string;
    flag: string;
};

export interface User {
    username: string;
    email: string;
    firstName: string;
    lastName: string;
    phoneNumber: number;
    roles: Set<string>;
}

export interface SuccessToastProps {
    message: string;
    isVisible: boolean;
    onClose: () => void;
    duration?: number;
}

export interface ToastState {
    key: number;
    message: string;
    duration: number;
}
export interface AuthContextType {
    user: UserResponseDTO | null;
    login: (user: UserResponseDTO) => void;
    logout: () => void;
    isLoading: boolean;
    justLoggedIn: boolean;
    clearJustLoggedIn: () => void;
}

export interface ToastContextType {
    showToast: (message: string, duration?: number) => void;
}

export interface ToastProviderProps {
    children: ReactNode;
}
