import { apiClient } from '@/lib/apiClient';
import { ENV } from '@/config/env';
import { AuthResponse, LoginRequest, RegisterRequest, UserDto } from '@/types/auth';

const TOKEN_KEY = 'enrichment_auth_token';
const USER_KEY = 'enrichment_auth_user';

export const authService = {
  async register(data: RegisterRequest): Promise<AuthResponse> {
    const res = await apiClient<AuthResponse>(`${ENV.AUTH_SERVICE_URL}/api/v1/auth/register`, {
      method: 'POST',
      body: data,
    });
    if (res.token && res.user) {
      this.saveSession(res.token, res.user);
    }
    return res;
  },

  async login(data: LoginRequest): Promise<AuthResponse> {
    const res = await apiClient<AuthResponse>(`${ENV.AUTH_SERVICE_URL}/api/v1/auth/login`, {
      method: 'POST',
      body: data,
    });
    if (res.token && res.user) {
      this.saveSession(res.token, res.user);
    }
    return res;
  },

  async getMe(): Promise<UserDto> {
    const res = await apiClient<UserDto>(`${ENV.AUTH_SERVICE_URL}/api/v1/auth/me`, {
      method: 'GET',
    });
    if (res && typeof window !== 'undefined') {
      localStorage.setItem(USER_KEY, JSON.stringify(res));
    }
    return res;
  },

  saveSession(token: string, user: UserDto): void {
    if (typeof window !== 'undefined') {
      localStorage.setItem(TOKEN_KEY, token);
      localStorage.setItem(USER_KEY, JSON.stringify(user));
    }
  },

  clearSession(): void {
    if (typeof window !== 'undefined') {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
    }
  },

  getToken(): string | null {
    if (typeof window !== 'undefined') {
      return localStorage.getItem(TOKEN_KEY);
    }
    return null;
  },

  getUser(): UserDto | null {
    if (typeof window !== 'undefined') {
      const raw = localStorage.getItem(USER_KEY);
      if (raw) {
        try {
          return JSON.parse(raw) as UserDto;
        } catch {
          return null;
        }
      }
    }
    return null;
  },

  isAuthenticated(): boolean {
    return !!this.getToken();
  },
};
