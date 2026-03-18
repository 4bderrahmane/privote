export interface UserUpdateDTO {
    firstName?: string;
    lastName?: string;
    email?: string;
    phoneNumber?: string;
    address?: string;
    region?: string;
    birthPlace?: string;
    birthDate?: string;
}

export interface SettingsProps {
    section?: 'profile' | 'delete';
}

export interface User {
    email: string;
    password: string;
    name: string;
}

export interface AuthContextType {
    isAuthenticated: boolean;
    token: string | undefined;
    userProfile: Keycloak.KeycloakProfile | undefined;
    login: () => void;
    logout: () => void;
}

export interface UserLoginDTO {
    email: string;
    password: string;
}

export type Role = 'ADMIN' | 'CANDIDATE' | 'INTERVIEWER' | 'HR_MANAGER';

export interface UserRegistrationDTO {
    email: string;
    password: string;
    username: string;
    firstName: string;
    lastName: string;
    phoneNumber: string;
}

export interface UserResponseDTO {
    keycloakId: string;
    username: string;
    firstName: string;
    lastName: string;
    cin: string;
    email: string;
    phoneNumber: string | null;
    address: string | null;
    region: string | null;
    birthPlace: string | null;
    birthDate: string | null;
    emailVerified: boolean;
}

export interface ApiResponse<T> {
    success: boolean;
    data: T;
    message?: string;
}

export interface ApiError {
    success: false;
    error: string;
    details?: string[];
    statusCode: number;
}

export interface LoginComponentProps {
    onLoginSuccess: (credentials: UserLoginDTO) => void;
}

export interface RegistrationComponentProps {
    onRegistrationSuccess: (credentials: UserRegistrationDTO) => void;
}

export interface ApiErrorResponse {
    message: string;
    errorCode: string;
    details?: string[];
}

export interface ValidationErrorResponse extends ApiErrorResponse {
    field: string;
}

export interface AuthResponse {
    user: UserResponseDTO;
    message?: string;
}

export interface BackendErrorResponse {
    timestamp: string;
    status: number;
    errorCode: string;
    data?: Record<string, unknown>;
}

export interface EnhancedError extends Error {
    errorCode?: AuthErrorCode;
    backendError?: BackendErrorResponse;
}

export type AuthErrorCode =
    | 'UNEXPECTED_ERROR'
    | 'INVALID_REQUEST_BODY'
    | 'RESOURCE_NOT_FOUND'
    | 'INVALID_TOKEN'
    | 'ACCESS_DENIED'
    | 'USERNAME_ALREADY_EXISTS'
    | 'EMAIL_ALREADY_IN_USE'
    | 'EMAIL_ALREADY_EXISTS'
    | 'PASSWORD_TOO_WEAK'
    | 'INVALID_EMAIL_FORMAT'
    | 'INVALID_USERNAME_FORMAT'
    | 'INVALID_CREDENTIALS'
    | 'ACCOUNT_DISABLED'
    | 'ACCOUNT_LOCKED'
    | 'ACCOUNT_NOT_VERIFIED'
    | 'PASSWORD_CHANGE_FAILED'
    | 'PASSWORD_RESET_TOKEN_EXPIRED'
    | 'INVALID_PASSWORD_RESET_TOKEN'
    | 'NEW_PASSWORD_SAME_AS_OLD'
    | 'AUTHENTICATION_REQUIRED'
    | 'AUTHENTICATION_FAILED'
    | 'USER_NOT_FOUND'
    | 'USER_ALREADY_EXISTS'
    | 'VALIDATION_FAILED'
    | 'INTERNAL_SERVER_ERROR';
