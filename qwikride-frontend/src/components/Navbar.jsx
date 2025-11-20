import { Link } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth.js';
import { useTheme } from '../hooks/useTheme.js';
import { motion } from 'framer-motion';
import { useState, useEffect } from 'react';

const Navbar = () => {
  const { user, logout } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const [isScrolled, setIsScrolled] = useState(false);

  useEffect(() => {
    const handleScroll = () => {
      setIsScrolled(window.scrollY > 20);
    };
    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  return (
    <motion.nav 
      initial={{ y: -100 }}
      animate={{ y: 0 }}
      transition={{ duration: 0.6 }}
      className={`fixed top-0 left-0 right-0 backdrop-blur-2xl z-50 transition-all duration-500 border-b ${
        isScrolled 
          ? 'bg-white/95 dark:bg-gray-900/95 border-gray-200/80 dark:border-gray-800/80 shadow-lg' 
          : 'bg-white/60 dark:bg-gray-900/60 border-gray-200/30 dark:border-gray-800/30'
      }`}
    >
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between h-20 items-center">
          <Link to="/" className="relative group flex items-center gap-3">
            <motion.div
              whileHover={{ rotate: 360 }}
              transition={{ duration: 0.6 }}
              className="w-10 h-10 bg-gradient-to-br from-primary-900 to-primary-800 dark:from-gray-100 dark:to-white rounded-lg flex items-center justify-center shadow-md"
            >
              <svg className="w-6 h-6 text-white dark:text-primary-900" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
              </svg>
            </motion.div>
            <motion.span 
              className="text-2xl font-bold bg-gradient-to-r from-primary-900 to-primary-800 dark:from-gray-100 dark:to-white bg-clip-text text-transparent"
              whileHover={{ scale: 1.05 }}
              transition={{ type: 'spring', stiffness: 400 }}
            >
              QwikRide
            </motion.span>
          </Link>

          <div className="hidden md:flex items-center gap-6 text-sm font-medium text-gray-700 dark:text-gray-300">
            <Link
              to="/pricing"
              className="transition-colors hover:text-primary-900 dark:hover:text-white"
            >
              Pricing
            </Link>
            {user && (
              <>
                <Link
                  to="/dashboard"
                  className="transition-colors hover:text-primary-900 dark:hover:text-white"
                >
                  Dashboard
                </Link>
                <Link
                  to="/bikes"
                  className="transition-colors hover:text-primary-900 dark:hover:text-white"
                >
                  Bikes
                </Link>
                <Link
                  to="/history"
                  className="transition-colors hover:text-primary-900 dark:hover:text-white"
                >
                  History
                </Link>
                <Link
                  to="/account"
                  className="transition-colors hover:text-primary-900 dark:hover:text-white"
                >
                  Account
                </Link>
              </>
            )}
          </div>
          
          <div className="flex items-center space-x-4">
            <motion.button
              onClick={toggleTheme}
              whileHover={{ scale: 1.1, rotate: 180 }}
              whileTap={{ scale: 0.9 }}
              transition={{ duration: 0.3 }}
              className="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors border border-transparent hover:border-gray-200 dark:hover:border-gray-700"
            >
              {theme === 'light' ? (
                <svg className="w-5 h-5 text-gray-700" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z" />
                </svg>
              ) : (
                <svg className="w-5 h-5 text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z" />
                </svg>
              )}
            </motion.button>

            {user ? (
              <>
                <motion.div
                  initial={{ opacity: 0, x: 20 }}
                  animate={{ opacity: 1, x: 0 }}
                  className="flex items-center gap-3"
                >
                  <div className="hidden md:block text-right">
                    <div className="text-sm font-medium text-gray-900 dark:text-white">{user.fullName}</div>
                    <div className="text-xs text-gray-500 dark:text-gray-400">{user.role}</div>
                  </div>
                  
                  <Link to="/account">
                    <motion.div 
                      className="relative"
                      whileHover={{ scale: 1.1 }}
                      transition={{ type: 'spring', stiffness: 400 }}
                    >
                      <div className="w-9 h-9 bg-gradient-to-br from-gray-100 to-gray-50 dark:from-gray-800 dark:to-gray-700 rounded-full flex items-center justify-center border border-gray-200 dark:border-gray-700 shadow-sm cursor-pointer">
                        <span className="text-primary-900 dark:text-gray-100 font-bold text-sm">{user.fullName[0]}</span>
                      </div>
                    <motion.div 
                      animate={{ scale: [1, 1.2, 1] }}
                      transition={{ duration: 2, repeat: Infinity }}
                      className="absolute -bottom-0.5 -right-0.5 w-2.5 h-2.5 bg-green-500 rounded-full border-2 border-white dark:border-gray-900 shadow-sm"
                    />
                    </motion.div>
                  </Link>
                  
                  <motion.button 
                    onClick={logout} 
                    whileHover={{ scale: 1.05 }}
                    whileTap={{ scale: 0.95 }}
                    className="px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 hover:text-gray-900 dark:hover:text-white transition-colors rounded-md hover:bg-gray-100 dark:hover:bg-gray-800"
                  >
                    Logout
                  </motion.button>
                </motion.div>
              </>
            ) : (
              <>
                <Link 
                  to="/login" 
                  className="text-gray-700 dark:text-gray-300 hover:text-primary-900 dark:hover:text-white font-medium transition-colors"
                >
                  Login
                </Link>
                <motion.div 
                  whileHover={{ scale: 1.05 }} 
                  whileTap={{ scale: 0.95 }}
                >
                  <Link to="/register" className="btn-primary">
                    Sign Up
                  </Link>
                </motion.div>
              </>
            )}
          </div>
        </div>
      </div>
    </motion.nav>
  );
};

export default Navbar;