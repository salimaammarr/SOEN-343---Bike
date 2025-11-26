import React, { useState, useEffect } from 'react';
import { loadStripe } from '@stripe/stripe-js';
import { Elements } from '@stripe/react-stripe-js';
import { useLocation, useNavigate } from 'react-router-dom';
import CheckoutForm from '../components/CheckoutForm';
import api from '../services/api';

// Load Stripe outside of a component’s render to avoid recreating the `Stripe` object on every render.
const stripePromise = loadStripe(import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY);

const PaymentPage = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const [clientSecret, setClientSecret] = useState("");
  
  // Get amount from navigation state, default to 0 if not present
  const amount = location.state?.amount || 0;
  const ledgerEntryId = location.state?.ledgerEntryId;
  const planId = location.state?.planId;

  useEffect(() => {
    if (amount <= 0) {
        // Redirect back if no valid amount
        navigate('/pricing');
        return;
    }

    // Create PaymentIntent as soon as the page loads
    api.post("/payments/create-payment-intent", { 
        amount: amount, 
        currency: "cad",
        planId: planId,
        ledgerEntryId: ledgerEntryId
    })
      .then((res) => setClientSecret(res.data.clientSecret))
      .catch((err) => console.error("Error creating payment intent:", err));
  }, [amount, planId, ledgerEntryId, navigate]);

  const appearance = {
    theme: 'stripe',
  };
  const options = {
    clientSecret,
    appearance,
  };

  return (
    <div className="min-h-screen bg-gray-100 flex items-center justify-center">
      {clientSecret && (
        <Elements options={options} stripe={stripePromise}>
          <CheckoutForm amount={amount / 100} />
        </Elements>
      )}
    </div>
  );
};

export default PaymentPage;
