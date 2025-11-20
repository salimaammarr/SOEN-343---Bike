import { useState } from 'react';
import { useNavigate, Link, useLocation } from 'react-router-dom';
import { authService } from '../services/api';
import { useAuth } from '../hooks/useAuth.js';
import { motion } from 'framer-motion';
import { PAGE_VARIANTS } from '../constants/animations';
import { GridBackground, AnimatedBlob, LoadingSpinner, Alert } from '../components';
import GuestHomeLink from '../components/GuestHomeLink';

const Login = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { login } = useAuth();
  const [formData, setFormData] = useState({ username: '', password: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const successMessage = location.state?.message;

  const handleChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const response = await authService.login(formData);
      const { token, username, fullName, role, id, tier, tierChangeNotification, hasDualRole, primaryRole } = response.data;
      login(token, { 
        username, 
        fullName, 
        role, 
        id, 
        tier, 
        tierChangeNotification,
        hasDualRole: hasDualRole || false,
        primaryRole: primaryRole || role
      });
      
      // Show tier change notification if present
      if (tierChangeNotification) {
        // Store notification to show in dashboard
        sessionStorage.setItem('tierNotification', tierChangeNotification);
      }
      
      navigate('/dashboard');
    } catch {
      setError('Invalid username or password');
    } finally {
      setLoading(false);
    }
  };

  return (
    <motion.div
      variants={PAGE_VARIANTS}
      initial="initial"
      animate="animate"
      exit="exit"
      transition={{ duration: 0.4, ease: 'easeInOut' }}
      className="min-h-[calc(100vh-5rem)] flex items-center justify-center px-4 sm:px-6 lg:px-8 relative overflow-hidden"
    >
      <GuestHomeLink className="absolute left-4 top-4 z-20 sm:left-6 sm:top-6" />
      <GridBackground gridSize="48px" />
      <AnimatedBlob 
        position={{ top: '33%', right: '33%' }} 
        size="450px"
        duration={10}
        movement={{ x: [0, 30, 0], y: [0, -20, 0] }} 
      />
      
      <div className="max-w-lg w-full relative z-10">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, delay: 0.2 }}
        >
          <div className="text-center mb-12">
            <motion.div
              initial={{ opacity: 0, scale: 0.9 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ duration: 0.5, delay: 0.3 }}
            >
              <h2 className="text-4xl md:text-5xl font-extrabold bg-gradient-to-r from-primary-900 via-primary-800 to-primary-900 dark:from-gray-100 dark:via-white dark:to-gray-100 bg-clip-text text-transparent mb-4 tracking-tight">Welcome back</h2>
              <p className="text-lg text-gray-600 dark:text-gray-400 font-light tracking-tight">Sign in to continue your journey</p>
            </motion.div>
          </div>

          <div className="card relative overflow-hidden">
            <div className="absolute inset-0 bg-gradient-to-br from-primary-50/0 via-blue-50/0 to-transparent dark:from-gray-700/0 dark:via-gray-600/0 hover:from-primary-50/30 hover:via-blue-50/20 dark:hover:from-gray-700/20 dark:hover:via-gray-600/10 transition-all duration-700 rounded-2xl pointer-events-none"></div>
            <div className="relative z-10">
              {successMessage && <Alert type="success">{successMessage}</Alert>}
              {error && <Alert type="error">{error}</Alert>}

              <form onSubmit={handleSubmit} className="space-y-6">
                <div className="space-y-1.5">
                  <label className="block text-sm font-semibold text-gray-700 dark:text-gray-300 tracking-tight">Username</label>
                  <input
                    type="text"
                    name="username"
                    value={formData.username}
                    onChange={handleChange}
                    required
                    className="input-field"
                    placeholder="Enter your username"
                  />
                </div>

                <div className="space-y-1.5">
                  <label className="block text-sm font-semibold text-gray-700 dark:text-gray-300 tracking-tight">Password</label>
                  <input
                    type="password"
                    name="password"
                    value={formData.password}
                    onChange={handleChange}
                    required
                    className="input-field"
                    placeholder="Enter your password"
                  />
                </div>

                <motion.button
                  type="submit"
                  disabled={loading}
                  whileHover={{ scale: 1.02 }}
                  whileTap={{ scale: 0.98 }}
                  className="w-full btn-primary disabled:opacity-50 disabled:cursor-not-allowed mt-8"
                >
                  {loading ? (
                    <span className="flex items-center justify-center gap-2">
                      <LoadingSpinner />
                      Signing in...
                    </span>
                  ) : 'Sign In'}
                </motion.button>
              </form>

              <p className="mt-6 text-center text-gray-600 dark:text-gray-400">
                Don't have an account?{' '}
                <Link to="/register" className="text-primary-900 dark:text-gray-100 font-semibold hover:text-primary-700 dark:hover:text-white transition-colors">
                  Create account
                </Link>
              </p>

              <div className="mt-5 p-2.5 bg-gray-50 dark:bg-gray-800/50 rounded-md border border-gray-200 dark:border-gray-700">
                <p className="text-xs text-gray-600 dark:text-gray-400 font-medium mb-0.5">Test Account</p>
                <p className="text-xs text-gray-500 dark:text-gray-500 font-mono">operator / operator123</p>
              </div>
            </div>
          </div>
        </motion.div>
      </div>
    </motion.div>
  );
};

export default Login;