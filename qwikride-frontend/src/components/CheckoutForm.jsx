import React, { useState } from 'react';
import { useStripe, useElements, PaymentElement } from '@stripe/react-stripe-js';
import { useNavigate } from 'react-router-dom';

const CheckoutForm = ({ amount }) => {
  const stripe = useStripe();
  const elements = useElements();
  const navigate = useNavigate();
  const [errorMessage, setErrorMessage] = useState(null);
  const [isProcessing, setIsProcessing] = useState(false);

  const handleSubmit = async (event) => {
    event.preventDefault();

    if (!stripe || !elements) {
      return;
    }

    setIsProcessing(true);

    const { error } = await stripe.confirmPayment({
      elements,
      confirmParams: {
        // Make sure to change this to your payment completion page
        return_url: `${window.location.origin}/payment-success`,
      },
    });

    if (error) {
      setErrorMessage(error.message);
    }

    setIsProcessing(false);
  };

  return (
    <form onSubmit={handleSubmit} className="w-full max-w-md mx-auto p-6 bg-white rounded-lg shadow-md">
      <h2 className="text-2xl font-bold mb-4 text-gray-800">Pay ${amount}</h2>
      <div className="mb-4">
        <PaymentElement />
      </div>
      {errorMessage && <div className="text-red-500 mb-4">{errorMessage}</div>}
      <div className="flex gap-4">
        <button
          type="button"
          onClick={() => navigate(-1)}
          disabled={isProcessing}
          className="w-full bg-gray-200 text-gray-800 py-2 px-4 rounded hover:bg-gray-300 disabled:opacity-50 transition duration-200"
        >
          Cancel
        </button>
        <button
          disabled={!stripe || isProcessing}
          className="w-full bg-blue-600 text-white py-2 px-4 rounded hover:bg-blue-700 disabled:opacity-50 transition duration-200"
        >
          {isProcessing ? "Processing..." : "Pay Now"}
        </button>
      </div>
    </form>
  );
};

export default CheckoutForm;
