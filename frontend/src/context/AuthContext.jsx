import { createContext, useContext, useEffect, useState } from 'react';
import * as authApi from '../api/auth';
import { refreshAccessToken, setToken } from '../api/client';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // The page starts with no access token; if the browser still holds a session, this picks it up.
    refreshAccessToken()
      .then((session) => (session ? authApi.me() : null))
      .then(setUser)
      .catch(() => setUser(null))
      .finally(() => setLoading(false));
  }, []);

  async function login(email, password) {
    const response = await authApi.login({ email, password });
    setToken(response.token);
    setUser(response.user);
    return response;
  }

  async function register(payload) {
    const response = await authApi.register(payload);
    setToken(response.token);
    setUser(response.user);
    return response;
  }

  async function logout() {
    try {
      await authApi.logout();
    } finally {
      setToken(null);
      setUser(null);
    }
  }

  return (
    <AuthContext.Provider value={{ user, loading, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return ctx;
}
