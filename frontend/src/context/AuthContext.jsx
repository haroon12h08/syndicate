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
    if (response.mfaRequired) {
      return response;
    }
    setToken(response.token);
    setUser(response.user);
    return response;
  }

  async function completeMfaLogin(mfaToken, code) {
    const response = await authApi.verifyMfaLogin(mfaToken, parseInt(code, 10));
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
    <AuthContext.Provider value={{ user, setUser, loading, login, completeMfaLogin, register, logout }}>
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
