import { useState, useEffect } from 'react';
import { useAuth } from '../hooks/useAuth';
import { motion } from 'framer-motion';
import { PAGE_VARIANTS } from '../constants/animations';
import { GridBackground, AnimatedBlob, LoadingSpinner, Alert } from '../components';
import { api } from '../services/api';

const AccountInformation = () => {
  const { user, toggleRole } = useAuth();
  const [accountData, setAccountData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [toggling, setToggling] = useState(false);
  const [notification, setNotification] = useState(null);

  useEffect(() => {
    // Check for tier notification from session storage
    const tierNotification = sessionStorage.getItem('tierNotification');
    if (tierNotification) {
      setNotification(tierNotification);
      sessionStorage.removeItem('tierNotification');
    }
  }, []);

  useEffect(() => {
    const fetchAccountData = async () => {
      try {
        setLoading(true);
        setError('');
        const response = await api.get('/auth/account');
        setAccountData(response.data);
      } catch (err) {
        console.error('Failed to fetch account data:', err);
        setError('Failed to load account information. Please refresh the page.');
      } finally {
        setLoading(false);
      }
    };

    if (user) {
      fetchAccountData();
    }
  }, [user]);

  const handleRoleToggle = async (newRole) => {
    try {
      setToggling(true);
      setError('');
      await toggleRole(newRole);
      // Refresh account data after role toggle
      const response = await api.get('/auth/account');
      setAccountData(response.data);
      
      // Show success message
      setNotification(`Successfully switched to ${newRole} mode`);
      setTimeout(() => setNotification(null), 5000);
    } catch (err) {
      console.error('Failed to toggle role:', err);
      setError('Failed to switch role. Please try again.');
    } finally {
      setToggling(false);
    }
  };

  const getTierColor = (tier) => {
    switch (tier) {
      case 'GOLD':
        return 'from-yellow-400 via-yellow-500 to-yellow-600';
      case 'SILVER':
        return 'from-gray-300 via-gray-400 to-gray-500';
      case 'BRONZE':
        return 'from-orange-400 via-orange-500 to-orange-600';
      case 'ENTRY':
      default:
        return 'from-gray-200 via-gray-300 to-gray-400';
    }
  };

  const getTierBadge = (tier) => {
    const colors = getTierColor(tier);
    return (
      <motion.div
        initial={{ scale: 0 }}
        animate={{ scale: 1 }}
        transition={{ type: 'spring', delay: 0.2 }}
        className={`inline-flex items-center gap-2 px-4 py-2 rounded-full bg-gradient-to-r ${colors} text-white shadow-lg`}
      >
        <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
          <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
        </svg>
        <span className="font-bold text-sm">{tier}</span>
      </motion.div>
    );
  };

  const formatMoney = (amount) => {
    if (!amount) return '$0.00';
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(amount);
  };

  if (loading) {
    return (
      <motion.div
        variants={PAGE_VARIANTS}
        initial="initial"
        animate="animate"
        exit="exit"
        className="min-h-[calc(100vh-5rem)] flex items-center justify-center"
      >
        <LoadingSpinner />
      </motion.div>
    );
  }

  const account = accountData || user;
  const hasDualRole = account?.hasDualRole || (account?.primaryRole === 'OPERATOR');
  const currentRole = account?.activeRole || account?.role || 'RIDER';

  return (
    <motion.div
      variants={PAGE_VARIANTS}
      initial="initial"
      animate="animate"
      exit="exit"
      transition={{ duration: 0.4, ease: 'easeInOut' }}
      className="min-h-[calc(100vh-5rem)] px-4 sm:px-6 lg:px-8 py-16 relative overflow-hidden"
    >
      <GridBackground />
      
      <AnimatedBlob 
        position={{ top: '10%', right: '20%' }}
        size="400px"
        duration={12}
        movement={{ x: [0, 40, 0], y: [0, -30, 0] }}
      />
      
      <div className="max-w-4xl mx-auto relative z-10">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6 }}
          className="mb-8"
        >
          <h1 className="text-4xl md:text-5xl font-black bg-gradient-to-r from-primary-900 via-primary-700 to-primary-900 dark:from-gray-50 dark:via-gray-200 dark:to-gray-50 bg-clip-text text-transparent tracking-tight mb-3">
            Account Information
          </h1>
          <p className="text-lg text-gray-600 dark:text-gray-400 font-light">
            Manage your account settings and view your loyalty status
          </p>
        </motion.div>

        {notification && (
          <motion.div
            initial={{ opacity: 0, y: -10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -10 }}
            className="mb-6"
          >
            <Alert type="success">{notification}</Alert>
          </motion.div>
        )}

        {error && (
          <div className="mb-6">
            <Alert type="error">{error}</Alert>
          </div>
        )}

        <div className="grid md:grid-cols-2 gap-6">
          {/* Account Details Card */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.1 }}
            className="card"
          >
            <h2 className="text-2xl font-bold mb-6 text-primary-900 dark:text-gray-50">Account Details</h2>
            <div className="space-y-4">
              <div>
                <label className="text-sm font-semibold text-gray-600 dark:text-gray-400">Full Name</label>
                <p className="text-lg font-medium text-primary-900 dark:text-gray-100">{account?.fullName}</p>
              </div>
              <div>
                <label className="text-sm font-semibold text-gray-600 dark:text-gray-400">Username</label>
                <p className="text-lg font-medium text-primary-900 dark:text-gray-100">{account?.username}</p>
              </div>
              <div>
                <label className="text-sm font-semibold text-gray-600 dark:text-gray-400">Email</label>
                <p className="text-lg font-medium text-primary-900 dark:text-gray-100">{account?.email || 'N/A'}</p>
              </div>
              <div>
                <label className="text-sm font-semibold text-gray-600 dark:text-gray-400">Current Role</label>
                <p className="text-lg font-medium text-primary-900 dark:text-gray-100">
                  <span className="inline-flex items-center px-3 py-1 rounded-full bg-primary-100 dark:bg-primary-900 text-primary-900 dark:text-primary-100 font-semibold">
                    {currentRole}
                  </span>
                </p>
              </div>
            </div>
          </motion.div>

          {/* Loyalty & Rewards Card */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.2 }}
            className="card"
          >
            <h2 className="text-2xl font-bold mb-6 text-primary-900 dark:text-gray-50">Loyalty & Rewards</h2>
            <div className="space-y-6">
              <div>
                <label className="text-sm font-semibold text-gray-600 dark:text-gray-400 mb-2 block">Membership Tier</label>
                {getTierBadge(account?.tier || 'ENTRY')}
                <p className="text-xs text-gray-500 dark:text-gray-400 mt-2">
                  {account?.tier === 'GOLD' && '15% discount + 5min reservation extension'}
                  {account?.tier === 'SILVER' && '10% discount + 2min reservation extension'}
                  {account?.tier === 'BRONZE' && '5% discount on trips'}
                  {account?.tier === 'ENTRY' && 'No perks yet - start riding to unlock rewards!'}
                </p>
              </div>
              
              <div className="pt-4 border-t border-gray-200 dark:border-gray-700">
                <label className="text-sm font-semibold text-gray-600 dark:text-gray-400 mb-2 block">Flex Dollars</label>
                <div className="flex items-baseline gap-2">
                  <span className="text-3xl font-bold text-green-600 dark:text-green-400">
                    {formatMoney(account?.flexDollars || 0)}
                  </span>
                </div>
                <p className="text-xs text-gray-500 dark:text-gray-400 mt-2">
                  Earned by returning bikes to stations below 25% capacity. Automatically applied to trips.
                </p>
              </div>

              {account?.pendingBalance && account.pendingBalance > 0 && (
                <div className="pt-4 border-t border-gray-200 dark:border-gray-700">
                  <label className="text-sm font-semibold text-gray-600 dark:text-gray-400 mb-2 block">Pending Balance</label>
                  <p className="text-lg font-medium text-primary-900 dark:text-gray-100">
                    {formatMoney(account.pendingBalance)}
                  </p>
                </div>
              )}
            </div>
          </motion.div>
        </div>

        {/* Role Toggle Card (for dual-role users) */}
        {hasDualRole && (
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.3 }}
            className="card mt-6"
          >
            <h2 className="text-2xl font-bold mb-6 text-primary-900 dark:text-gray-50">Role Management</h2>
            <p className="text-gray-600 dark:text-gray-400 mb-6">
              As an operator, you can switch between OPERATOR and RIDER modes to access different features.
            </p>
            <div className="flex gap-4">
              <motion.button
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
                onClick={() => handleRoleToggle('OPERATOR')}
                disabled={toggling || currentRole === 'OPERATOR'}
                className={`flex-1 px-6 py-3 rounded-lg font-semibold transition-all ${
                  currentRole === 'OPERATOR'
                    ? 'bg-primary-900 text-white dark:bg-primary-700 dark:text-gray-100'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200 dark:bg-gray-700 dark:text-gray-300 dark:hover:bg-gray-600'
                } disabled:opacity-50 disabled:cursor-not-allowed`}
              >
                {toggling && currentRole !== 'OPERATOR' ? (
                  <span className="flex items-center justify-center gap-2">
                    <LoadingSpinner />
                    Switching...
                  </span>
                ) : (
                  <>
                    {currentRole === 'OPERATOR' && '✓ '}
                    Operator Mode
                  </>
                )}
              </motion.button>
              <motion.button
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
                onClick={() => handleRoleToggle('RIDER')}
                disabled={toggling || currentRole === 'RIDER'}
                className={`flex-1 px-6 py-3 rounded-lg font-semibold transition-all ${
                  currentRole === 'RIDER'
                    ? 'bg-primary-900 text-white dark:bg-primary-700 dark:text-gray-100'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200 dark:bg-gray-700 dark:text-gray-300 dark:hover:bg-gray-600'
                } disabled:opacity-50 disabled:cursor-not-allowed`}
              >
                {toggling && currentRole !== 'RIDER' ? (
                  <span className="flex items-center justify-center gap-2">
                    <LoadingSpinner />
                    Switching...
                  </span>
                ) : (
                  <>
                    {currentRole === 'RIDER' && '✓ '}
                    Rider Mode
                  </>
                )}
              </motion.button>
            </div>
          </motion.div>
        )}
      </div>
    </motion.div>
  );
};

export default AccountInformation;

