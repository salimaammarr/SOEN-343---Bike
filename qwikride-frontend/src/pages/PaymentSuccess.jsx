import React, { useEffect, useState, useRef } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import api from '../services/api';
import { useAuth } from '../hooks/useAuth';

const PaymentSuccess = () => {
  const navigate = useNavigate();
  const { updateUser } = useAuth();
  const [searchParams] = useSearchParams();
  const paymentIntentId = searchParams.get('payment_intent');
  const redirectStatus = searchParams.get('redirect_status');
  const [statusMessage, setStatusMessage] = useState('Verifying payment...');
  const processedRef = useRef(false);

  useEffect(() => {
    if (redirectStatus === 'succeeded' && paymentIntentId && !processedRef.current) {
      processedRef.current = true;
      // Call backend to confirm payment and update plan
      api.post('/payments/confirm-plan-update', { paymentIntentId })
        .then(async (res) => {
            const { planUpdated } = res.data;
            
            if (planUpdated) {
                setStatusMessage('Payment confirmed! Updating your plan...');
                // Refresh user data to reflect new plan
                await updateUser(); 
                setTimeout(() => {
                    navigate('/pricing');
                }, 2000);
            } else {
                setStatusMessage('Payment confirmed! Thank you for your ride.');
                setTimeout(() => {
                    navigate('/');
                }, 2000);
            }
        })
        .catch(err => {
            console.error("Failed to confirm payment", err);
            setStatusMessage('Payment successful, but failed to process confirmation. Please contact support.');
        });
    }
  }, [redirectStatus, paymentIntentId, navigate, updateUser]);

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-100">
      <div className="bg-white p-8 rounded-lg shadow-md text-center">
        {redirectStatus === 'succeeded' ? (
          <>
            <div className="text-green-500 text-5xl mb-4">✓</div>
            <h1 className="text-2xl font-bold mb-2">Payment Successful!</h1>
            <p className="text-gray-600">{statusMessage}</p>
          </>
        ) : (
          <>
            <div className="text-red-500 text-5xl mb-4">✗</div>
            <h1 className="text-2xl font-bold mb-2">Payment Failed</h1>
            <p className="text-gray-600">Something went wrong.</p>
            <button 
                onClick={() => navigate('/pricing')}
                className="mt-4 bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700"
            >
                Return to Pricing
            </button>
          </>
        )}
      </div>
    </div>
  );
};

export default PaymentSuccess;
