'use client';

import React, { createContext, useContext, useState, useEffect } from 'react';
import { ApiClient, AuthResponse, UserResponse } from './api';

interface AuthContextType {
  user: UserResponse | null;
  token: string | null;
  login: (email: string, password: string) => Promise<void>;
  signup: (username: string, email: string, password: string, firstName?: string, lastName?: string) => Promise<void>;
  logout: () => void;
  loading: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // Check for stored token on mount
    const storedToken = localStorage.getItem('token');
    if (storedToken) {
      setToken(storedToken);
      loadUser();
    } else {
      setLoading(false);
    }
  }, []);

  const loadUser = async () => {
    try {
      console.log('Loading user...');
      const userData = await ApiClient.getCurrentUser();
      console.log('User loaded:', userData);
      setUser(userData);
      return userData;
    } catch (error) {
      console.error('Failed to load user:', error);
      localStorage.removeItem('token');
      document.cookie = 'token=; path=/; expires=Thu, 01 Jan 1970 00:00:01 GMT';
      setToken(null);
      return null;
    } finally {
      setLoading(false);
    }
  };

  const login = async (email: string, password: string) => {
    setLoading(true);
    try {
      console.log('Logging in...');
      const response: AuthResponse = await ApiClient.login({ email, password });
      console.log('Login successful, token:', response.token);
      localStorage.setItem('token', response.token);
      // Set cookie for middleware
      document.cookie = `token=${response.token}; path=/; max-age=86400; SameSite=Strict`;
      setToken(response.token);
      const user = await loadUser();
      if (!user) {
        throw new Error('Failed to load user profile');
      }
    } catch (error) {
      console.error('Login failed:', error);
      setLoading(false);
      throw error;
    }
  };

  const signup = async (username: string, email: string, password: string, firstName?: string, lastName?: string) => {
    try {
      const response: AuthResponse = await ApiClient.signup({
        username,
        email,
        password,
        firstName,
        lastName,
      });
      localStorage.setItem('token', response.token);
      // Set cookie for middleware
      document.cookie = `token=${response.token}; path=/; max-age=86400; SameSite=Strict`;
      setToken(response.token);
      await loadUser();
    } catch (error) {
      console.error('Signup failed:', error);
      throw error;
    }
  };

  const logout = () => {
    localStorage.removeItem('token');
    // Clear cookie
    document.cookie = 'token=; path=/; expires=Thu, 01 Jan 1970 00:00:01 GMT';
    setToken(null);
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, token, login, signup, logout, loading }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
