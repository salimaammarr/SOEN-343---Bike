import { useCallback, useEffect, useState } from 'react';
import { AuthContext } from './AuthContext';
import { authService } from '../services/api';

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const token = localStorage.getItem('token');
    const userData = localStorage.getItem('user');
    if (token && userData) {
      setUser(JSON.parse(userData));
    }
    setLoading(false);
  }, []);

  const login = (token, userData) => {
    localStorage.setItem('token', token);
    localStorage.setItem('user', JSON.stringify(userData));
    setUser(userData);
  };

  const logout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    setUser(null);
  };

  const updateUser = useCallback((updater) => {
    setUser((previous) => {
      const next = typeof updater === 'function' ? updater(previous) : updater;
      if (next) {
        localStorage.setItem('user', JSON.stringify(next));
      } else {
        localStorage.removeItem('user');
      }
      return next;
    });
  }, []);

  const toggleRole = useCallback(async (newRole) => {
    try {
      const response = await authService.toggleRole(newRole);
      const { token, username, fullName, role, id, tier, tierChangeNotification, hasDualRole, primaryRole } = response.data;
      
      const updatedUser = {
        username,
        fullName,
        role,
        id,
        tier,
        tierChangeNotification,
        hasDualRole: hasDualRole || false,
        primaryRole: primaryRole || role
      };
      
      localStorage.setItem('token', token);
      localStorage.setItem('user', JSON.stringify(updatedUser));
      setUser(updatedUser);
      
      if (tierChangeNotification) {
        sessionStorage.setItem('tierNotification', tierChangeNotification);
      }
      
      return updatedUser;
    } catch (error) {
      console.error('Failed to toggle role:', error);
      throw error;
    }
  }, []);

  return (
    <AuthContext.Provider value={{ user, login, logout, updateUser, toggleRole, loading }}>
      {children}
    </AuthContext.Provider>
  );
};