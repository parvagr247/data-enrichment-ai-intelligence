'use client';

import React, { createContext, useContext, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { authService } from '@/services/authService';
import { LoginRequest, RegisterRequest, UserDto } from '@/types/auth';

interface AuthContextType {
  user: UserDto | null;
  token: string | null;
  loading: boolean;
  login: (credentials: LoginRequest) => Promise<void>;
  register: (payload: RegisterRequest) => Promise<void>;
  logout: () => void;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<UserDto | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const router = useRouter();

  useEffect(() => {
    const savedToken = authService.getToken();
    const savedUser = authService.getUser();

    if (savedToken) {
      setToken(savedToken);
      if (savedUser) {
        setUser(savedUser);
      }
      // Optionally verify with /api/v1/auth/me
      authService.getMe()
        .then((freshUser) => setUser(freshUser))
        .catch(() => {
          // Token expired or invalid
          authService.clearSession();
          setUser(null);
          setToken(null);
        })
        .finally(() => setLoading(false));
    } else {
      setLoading(false);
    }
  }, []);

  const login = async (credentials: LoginRequest) => {
    setLoading(true);
    try {
      const res = await authService.login(credentials);
      setToken(res.token);
      setUser(res.user);
      router.push('/');
    } finally {
      setLoading(false);
    }
  };

  const register = async (payload: RegisterRequest) => {
    setLoading(true);
    try {
      const res = await authService.register(payload);
      setToken(res.token);
      setUser(res.user);
      router.push('/');
    } finally {
      setLoading(false);
    }
  };

  const logout = () => {
    authService.clearSession();
    setUser(null);
    setToken(null);
    router.push('/login');
  };

  const refreshUser = async () => {
    try {
      const freshUser = await authService.getMe();
      setUser(freshUser);
    } catch {
      logout();
    }
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        loading,
        login,
        register,
        logout,
        refreshUser,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
