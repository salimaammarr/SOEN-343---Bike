import { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { authService, prcService } from '../services/api';
import { motion } from 'framer-motion';
import { PAGE_VARIANTS } from '../constants/animations';
import { GridBackground, AnimatedBlob, LoadingSpinner, Alert } from '../components';
import GuestHomeLink from '../components/GuestHomeLink';

const Register = () => {
  const navigate = useNavigate();
  const [step, setStep] = useState(1);
  const [plans, setPlans] = useState([]);
  const [selectedPlan, setSelectedPlan] = useState(null);
  const [formData, setFormData] = useState({
    fullName: '',
    address: '',
    email: '',
    username: '',
    password: '',
    paymentInfo: '',
  });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const fetchPlans = async () => {
      try {
        const { data } = await prcService.getPricingPlans();
        setPlans(data ?? []);
      } catch (error) {
        console.error('Failed to load pricing plans', error);
      }
    };
    fetchPlans();
  }, []);

  const handleChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleNextStep = (e) => {
    e.preventDefault();
    if (!formData.fullName || !formData.email || !formData.username || !formData.password) {
      setError('Please fill in all required fields');
      return;
    }
    setError('');
    setStep(2);
  };

  const handleSubmit = async () => {
    setError('');
    setLoading(true);

    try {
      const response = await authService.register(formData);
      
      if (response.status === 201) {
        const message = selectedPlan && selectedPlan.planType !== 'FREE'
          ? `Account created! Please sign in to complete your ${selectedPlan.planName} subscription.`
          : 'Account created successfully. Please sign in.';
          
        navigate('/login', { 
          state: { 
            message,
            intendedPlan: selectedPlan && selectedPlan.planType !== 'FREE' ? selectedPlan : null
          } 
        });
      }
    } catch (err) {
      const errorMessage = err.response?.data?.message || 
                          err.response?.data || 
                          'Registration failed. Please try again.';
      setError(errorMessage);
    } finally {
      setLoading(false);
    }
  };

  const renderStep1 = () => (
    <form onSubmit={handleNextStep} className="space-y-5">
      <div className="space-y-1.5">
        <label className="block text-sm font-semibold text-gray-700 dark:text-gray-300 tracking-tight">Full Name</label>
        <input
          type="text"
          name="fullName"
          value={formData.fullName}
          onChange={handleChange}
          required
          className="input-field"
          placeholder="John Doe"
        />
      </div>

      <div className="space-y-1.5">
        <label className="block text-sm font-semibold text-gray-700 dark:text-gray-300 tracking-tight">Address</label>
        <input
          type="text"
          name="address"
          value={formData.address}
          onChange={handleChange}
          required
          className="input-field"
          placeholder="123 Main St, Montreal"
        />
      </div>

      <div className="space-y-1.5">
        <label className="block text-sm font-semibold text-gray-700 dark:text-gray-300 tracking-tight">Email</label>
        <input
          type="email"
          name="email"
          value={formData.email}
          onChange={handleChange}
          required
          className="input-field"
          placeholder="john@example.com"
        />
      </div>

      <div className="space-y-1.5">
        <label className="block text-sm font-semibold text-gray-700 dark:text-gray-300 tracking-tight">Username</label>
        <input
          type="text"
          name="username"
          value={formData.username}
          onChange={handleChange}
          required
          className="input-field"
          placeholder="johndoe"
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
          minLength={6}
          className="input-field"
          placeholder="Minimum 6 characters"
        />
      </div>

      <div className="space-y-1.5">
        <label className="block text-sm font-semibold text-gray-700 dark:text-gray-300 tracking-tight">Payment Info (Optional)</label>
        <input
          type="text"
          name="paymentInfo"
          value={formData.paymentInfo}
          onChange={handleChange}
          className="input-field"
          placeholder="VISA-1234"
        />
      </div>

      <motion.button
        type="submit"
        whileHover={{ scale: 1.02 }}
        whileTap={{ scale: 0.98 }}
        className="w-full btn-primary mt-8"
      >
        Next: Select Plan
      </motion.button>
    </form>
  );

  const renderStep2 = () => (
    <div className="space-y-6">
      <div className="grid gap-4">
        {plans.map((plan) => (
          <div
            key={plan.planVersionId}
            onClick={() => setSelectedPlan(plan)}
            className={`p-4 rounded-xl border-2 cursor-pointer transition-all ${
              selectedPlan?.planVersionId === plan.planVersionId
                ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20'
                : 'border-gray-200 dark:border-gray-700 hover:border-primary-300'
            }`}
          >
            <div className="flex justify-between items-center">
              <div>
                <h3 className="font-bold text-lg">{plan.planName}</h3>
                <p className="text-sm text-gray-600 dark:text-gray-400">{plan.description}</p>
              </div>
              <div className="text-right">
                <div className="font-bold text-xl">
                  {plan.subscriptionPrice > 0 
                    ? `$${plan.subscriptionPrice}` 
                    : 'Free'}
                </div>
                {plan.subscriptionPrice > 0 && (
                  <div className="text-xs text-gray-500">
                    {plan.planType === 'REGULAR' ? '/month' : '/year'}
                  </div>
                )}
              </div>
            </div>
          </div>
        ))}
      </div>

      <div className="flex gap-4">
        <button
          onClick={() => setStep(1)}
          className="flex-1 px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
        >
          Back
        </button>
        <motion.button
          onClick={handleSubmit}
          disabled={loading || !selectedPlan}
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.98 }}
          className="flex-1 btn-primary disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {loading ? (
            <span className="flex items-center justify-center gap-2">
              <LoadingSpinner />
              Creating...
            </span>
          ) : 'Create Account'}
        </motion.button>
      </div>
    </div>
  );

  return (
    <motion.div
      variants={PAGE_VARIANTS}
      initial="initial"
      animate="animate"
      exit="exit"
      transition={{ duration: 0.4, ease: 'easeInOut' }}
      className="min-h-[calc(100vh-5rem)] flex items-center justify-center px-4 sm:px-6 lg:px-8 py-12 relative overflow-hidden"
    >
      <GuestHomeLink className="absolute left-4 top-4 z-20 sm:left-6 sm:top-6" />
      <GridBackground gridSize="48px" />
      <AnimatedBlob 
        position={{ top: '33%', left: '33%' }} 
        size="450px"
        duration={10}
        movement={{ x: [0, -30, 0], y: [0, 20, 0] }} 
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
              <h2 className="text-4xl md:text-5xl font-extrabold bg-gradient-to-r from-primary-900 via-primary-800 to-primary-900 dark:from-gray-100 dark:via-white dark:to-gray-100 bg-clip-text text-transparent mb-4 tracking-tight">
                {step === 1 ? 'Create account' : 'Select Plan'}
              </h2>
              <p className="text-lg text-gray-600 dark:text-gray-400 font-light tracking-tight">
                {step === 1 ? 'Join thousands of riders making a difference' : 'Choose the plan that fits your ride'}
              </p>
            </motion.div>
          </div>

          <div className="card relative overflow-hidden">
            <div className="absolute inset-0 bg-gradient-to-br from-primary-50/0 via-blue-50/0 to-transparent dark:from-gray-700/0 dark:via-gray-600/0 hover:from-primary-50/30 hover:via-blue-50/20 dark:hover:from-gray-700/20 dark:hover:via-gray-600/10 transition-all duration-700 rounded-2xl pointer-events-none"></div>
            <div className="relative z-10">
              {error && <Alert type="error">{error}</Alert>}

              {step === 1 ? renderStep1() : renderStep2()}

              <p className="mt-6 text-center text-gray-600 dark:text-gray-400">
                Already have an account?{' '}
                <Link to="/login" className="text-primary-900 dark:text-gray-100 font-semibold hover:text-primary-700 dark:hover:text-white transition-colors">
                  Sign in
                </Link>
              </p>
            </div>
          </div>
        </motion.div>
      </div>
    </motion.div>
  );
};

export default Register;