import { useCallback, useEffect, useMemo, useState } from 'react';
import { motion } from 'framer-motion';
import { useAuth } from '../hooks/useAuth';
import { prcService, pricingAdminService } from '../services/api';
import GuestHomeLink from '../components/GuestHomeLink';

const currencyFormatter = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  maximumFractionDigits: 2,
});

const formatMoney = (value) => {
  if (value === null || value === undefined) {
    return currencyFormatter.format(0);
  }
  const numeric = Number(value);
  if (Number.isNaN(numeric)) {
    return currencyFormatter.format(0);
  }
  return currencyFormatter.format(numeric);
};

const formatDate = (value) => {
  if (!value) {
    return 'Ongoing';
  }
  return new Date(value).toLocaleDateString(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });
};

const formatDateTime = (value) => {
  if (!value) {
    return '—';
  }
  return new Date(value).toLocaleString();
};

const formatDistance = (kilometres) => {
  if (kilometres === null || kilometres === undefined) {
    return '—';
  }
  const numeric = Number(kilometres);
  if (Number.isNaN(numeric)) {
    return '—';
  }
  return `${numeric.toFixed(1)} km`;
};

const formatPaymentStatus = (status) => {
  switch (status) {
    case 'PENDING':
      return 'Pending';
    case 'PAID':
      return 'Paid';
    case 'FAILED':
      return 'Failed';
    case 'ADJUSTED':
      return 'Adjusted';
    default:
      return status ?? 'Unknown';
  }
};

const formatDisputeStatus = (status) => {
  switch (status) {
    case 'OPEN':
      return 'Open';
    case 'UNDER_REVIEW':
      return 'Under Review';
    case 'RESOLVED':
      return 'Resolved';
    case 'REJECTED':
      return 'Rejected';
    default:
      return status ?? 'Unknown';
  }
};

const computePendingBalance = (entries) => {
  if (!entries || entries.length === 0) {
    return 0;
  }
  const total = entries
    .filter((entry) => entry.paymentStatus === 'PENDING')
    .reduce((acc, entry) => acc + Number(entry.total ?? 0), 0);
  return Number(total.toFixed(2));
};

const normalizeDateTimeInput = (value) => {
  if (!value) {
    return null;
  }
  if (value.length === 16) {
    return `${value}:00`;
  }
  return value;
};

const toDateTimeLocalInput = (value) => {
  if (!value) {
    return '';
  }
  return value.slice(0, 16);
};

const createEmptyPlanForm = () => ({
  planName: '',
  baseFee: '',
  perMinuteRate: '',
  ebikeSurcharge: '',
  membershipTier: 'NONE',
  cityId: '',
  effectiveFrom: '',
  effectiveTo: '',
  description: '',
  publish: true,
});

const membershipOptions = ['NONE', 'STANDARD', 'PREMIUM'];

const Pricing = () => {
  const { user, updateUser } = useAuth();
  const isRider = user?.role === 'RIDER';
  const isOperator = user?.role === 'OPERATOR';
  const isAuthenticatedRider = useMemo(() => {
    if (!user) {
      return false;
    }
    return isRider || isOperator;
  }, [isOperator, isRider, user]);

  const [plans, setPlans] = useState([]);
  const [plansLoading, setPlansLoading] = useState(true);
  const [plansError, setPlansError] = useState(null);

  const [history, setHistory] = useState([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState(null);

  const [selectedEntryId, setSelectedEntryId] = useState(null);
  const [summary, setSummary] = useState(null);
  const [summaryLoading, setSummaryLoading] = useState(false);
  const [summaryError, setSummaryError] = useState(null);
  const [pendingBalance, setPendingBalance] = useState(null);

  const [billingActionMessage, setBillingActionMessage] = useState(null);
  const [billingActionError, setBillingActionError] = useState(null);
  const [settlementLoading, setSettlementLoading] = useState(false);
  const [receiptLoading, setReceiptLoading] = useState(false);

  const [disputes, setDisputes] = useState([]);
  const [disputesLoading, setDisputesLoading] = useState(false);
  const [disputesError, setDisputesError] = useState(null);
  const [disputeFormOpen, setDisputeFormOpen] = useState(false);
  const [disputeReason, setDisputeReason] = useState('');
  const [disputeEvidenceUrl, setDisputeEvidenceUrl] = useState('');
  const [submitDisputeLoading, setSubmitDisputeLoading] = useState(false);
  const [submitDisputeError, setSubmitDisputeError] = useState(null);
  const [submitDisputeSuccess, setSubmitDisputeSuccess] = useState(null);

  const [openDisputes, setOpenDisputes] = useState([]);
  const [openDisputesLoading, setOpenDisputesLoading] = useState(false);
  const [openDisputesError, setOpenDisputesError] = useState(null);
  const [resolutionDrafts, setResolutionDrafts] = useState({});
  const [resolveLoadingId, setResolveLoadingId] = useState(null);
  const [operatorActionMessage, setOperatorActionMessage] = useState(null);
  const [operatorActionError, setOperatorActionError] = useState(null);
  const [exportingLedger, setExportingLedger] = useState(false);

  const [adminPlans, setAdminPlans] = useState([]);
  const [adminPlansLoading, setAdminPlansLoading] = useState(false);
  const [adminPlansError, setAdminPlansError] = useState(null);
  const [planForm, setPlanForm] = useState(() => createEmptyPlanForm());
  const [editingPlanId, setEditingPlanId] = useState(null);
  const [planSubmitLoading, setPlanSubmitLoading] = useState(false);
  const [planSubmitError, setPlanSubmitError] = useState(null);
  const [planSubmitSuccess, setPlanSubmitSuccess] = useState(null);

  const fetchPlans = useCallback(async () => {
    setPlansLoading(true);
    setPlansError(null);
    try {
      const { data } = await prcService.getPricingPlans();
      setPlans(data ?? []);
    } catch (error) {
      console.error('Failed to load pricing plans', error);
      setPlansError('Unable to load pricing plans at this time.');
    } finally {
      setPlansLoading(false);
    }
  }, []);

  const fetchBillingHistory = useCallback(async () => {
    // Don't make API calls if user is not authenticated or token is missing
    if (!isAuthenticatedRider || !user?.id || !localStorage.getItem('token')) {
      setHistory([]);
      setHistoryError(null);
      setHistoryLoading(false);
      setSelectedEntryId(null);
      setPendingBalance(null);
      return;
    }

    setHistoryLoading(true);
    setHistoryError(null);
    try {
      const { data } = await prcService.getBillingHistory(user.id);
      const entries = Array.isArray(data) ? data : [];
      setHistory(entries);

      const balance = computePendingBalance(entries);
      setPendingBalance(balance);
      if (updateUser) {
        updateUser((current) => {
          if (!current || current.id !== user.id) {
            return current;
          }
          const currentBalance = Number(current.pendingBalance ?? 0);
          if (Math.abs(currentBalance - balance) < 0.005) {
            return current;
          }
          return { ...current, pendingBalance: balance };
        });
      }

      setSelectedEntryId((current) => {
        if (entries.length === 0) {
          return null;
        }
        if (current && entries.some((entry) => entry.ledgerEntryId === current)) {
          return current;
        }
        return entries[0].ledgerEntryId;
      });
    } catch (error) {
      console.error('Failed to load billing history', error);
      setHistoryError('Unable to load billing history.');
      setHistory([]);
      setSelectedEntryId(null);
      setPendingBalance(null);
    } finally {
      setHistoryLoading(false);
    }
  }, [isAuthenticatedRider, updateUser, user?.id]);

  const fetchDisputes = useCallback(async () => {
    // Don't make API calls if user is not authenticated or token is missing
    if (!isRider || !user?.id || !localStorage.getItem('token')) {
      setDisputes([]);
      setDisputesError(null);
      setDisputesLoading(false);
      return;
    }

    setDisputesLoading(true);
    setDisputesError(null);
    try {
      const { data } = await prcService.listDisputes();
      setDisputes(Array.isArray(data) ? data : []);
    } catch (error) {
      console.error('Failed to load disputes', error);
      setDisputesError('Unable to load disputes at this time.');
    } finally {
      setDisputesLoading(false);
    }
  }, [isRider, user?.id]);

  const fetchOpenDisputes = useCallback(async () => {
    // Don't make API calls if user is not authenticated or token is missing
    if (!isOperator || !user?.id || !localStorage.getItem('token')) {
      setOpenDisputes([]);
      setOpenDisputesError(null);
      setOpenDisputesLoading(false);
      return;
    }

    setOpenDisputesLoading(true);
    setOpenDisputesError(null);
    try {
      const { data } = await prcService.listOpenDisputes();
      setOpenDisputes(Array.isArray(data) ? data : []);
    } catch (error) {
      console.error('Failed to load open disputes', error);
      setOpenDisputesError('Unable to load open disputes.');
    } finally {
      setOpenDisputesLoading(false);
    }
  }, [isOperator, user?.id]);

  const fetchAdminPlans = useCallback(async () => {
    // Don't make API calls if user is not authenticated or token is missing
    if (!isOperator || !user?.id || !localStorage.getItem('token')) {
      setAdminPlans([]);
      setAdminPlansError(null);
      setAdminPlansLoading(false);
      return;
    }

    setAdminPlansLoading(true);
    setAdminPlansError(null);
    try {
      const { data } = await pricingAdminService.listPlans();
      setAdminPlans(Array.isArray(data) ? data : []);
    } catch (error) {
      console.error('Failed to load plan catalogue', error);
      setAdminPlansError('Unable to load plan catalogue.');
    } finally {
      setAdminPlansLoading(false);
    }
  }, [isOperator, user?.id]);

  useEffect(() => {
    fetchPlans();
  }, [fetchPlans]);

  useEffect(() => {
    fetchBillingHistory();
  }, [fetchBillingHistory]);

  useEffect(() => {
    fetchDisputes();
  }, [fetchDisputes]);

  useEffect(() => {
    fetchOpenDisputes();
  }, [fetchOpenDisputes]);

  useEffect(() => {
    fetchAdminPlans();
  }, [fetchAdminPlans]);

  useEffect(() => {
    if (!selectedEntryId) {
      setSummary(null);
      return;
    }

    let isCancelled = false;

    const fetchSummary = async () => {
      setSummaryLoading(true);
      setSummaryError(null);
      try {
        const { data } = await prcService.getTripSummary(selectedEntryId);
        if (!isCancelled) {
          setSummary(data);
        }
      } catch (error) {
        console.error('Failed to load trip summary', error);
        if (!isCancelled) {
          setSummaryError('Unable to load trip summary.');
          setSummary(null);
        }
      } finally {
        if (!isCancelled) {
          setSummaryLoading(false);
        }
      }
    };

    fetchSummary();

    return () => {
      isCancelled = true;
    };
  }, [selectedEntryId]);

  useEffect(() => {
    setBillingActionMessage(null);
    setBillingActionError(null);
    setDisputeFormOpen(false);
    setDisputeReason('');
    setDisputeEvidenceUrl('');
    setSubmitDisputeError(null);
    setSubmitDisputeSuccess(null);
  }, [selectedEntryId]);

  const activeEntry = useMemo(() => {
    if (!selectedEntryId) {
      return null;
    }
    return history.find((entry) => entry.ledgerEntryId === selectedEntryId) ?? null;
  }, [history, selectedEntryId]);

  const canSettlePayment = activeEntry?.paymentStatus === 'PENDING';
  const canDownloadReceipt = Boolean(
    activeEntry && ['PAID', 'ADJUSTED'].includes(activeEntry.paymentStatus)
  );

  const handleSettlePayment = async () => {
    if (!activeEntry) {
      return;
    }
    setSettlementLoading(true);
    setBillingActionError(null);
    setBillingActionMessage(null);
    try {
      const { data } = await prcService.settlePayment(activeEntry.ledgerEntryId, {
        paymentMethodToken: 'saved-default',
      });
      if (data?.success) {
        setBillingActionMessage('Payment processed successfully.');
      } else {
        setBillingActionError(
          data?.failureReason ? `Payment failed: ${data.failureReason}` : 'Payment could not be processed.'
        );
      }
      await fetchBillingHistory();
    } catch (error) {
      console.error('Failed to settle payment', error);
      setBillingActionError('Payment failed. Please try again or contact support.');
    } finally {
      setSettlementLoading(false);
    }
  };

  const handleDownloadReceipt = async () => {
    if (!activeEntry || !canDownloadReceipt) {
      return;
    }
    setReceiptLoading(true);
    setBillingActionError(null);
    setBillingActionMessage(null);
    try {
      const { data } = await prcService.downloadReceipt(activeEntry.ledgerEntryId);
      const blob = new Blob([data], { type: 'application/pdf' });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `receipt-${activeEntry.ledgerEntryId}.pdf`);
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
      setBillingActionMessage('Receipt downloaded.');
    } catch (error) {
      console.error('Failed to download receipt', error);
      setBillingActionError('Unable to download receipt right now.');
    } finally {
      setReceiptLoading(false);
    }
  };

  const handleSubmitDispute = async (event) => {
    event.preventDefault();
    if (!activeEntry || !isRider) {
      return;
    }
    const reason = disputeReason.trim();
    const evidence = disputeEvidenceUrl.trim();
    if (!reason) {
      setSubmitDisputeError('Please describe the issue you want to dispute.');
      return;
    }
    setSubmitDisputeLoading(true);
    setSubmitDisputeError(null);
    setSubmitDisputeSuccess(null);
    try {
      await prcService.submitDispute({
        ledgerEntryId: activeEntry.ledgerEntryId,
        reason,
        evidenceUrl: evidence || null,
      });
      setSubmitDisputeSuccess('Dispute submitted. Our team will review it shortly.');
      setDisputeFormOpen(false);
      setDisputeReason('');
      setDisputeEvidenceUrl('');
      await fetchDisputes();
    } catch (error) {
      console.error('Failed to submit dispute', error);
      setSubmitDisputeError('Unable to submit dispute. Please try again later.');
    } finally {
      setSubmitDisputeLoading(false);
    }
  };

  const handleResolutionDraftChange = (ticketId, field, value) => {
    setResolutionDrafts((drafts) => ({
      ...drafts,
      [ticketId]: {
        ...(drafts[ticketId] ?? {}),
        [field]: value,
      },
    }));
  };

  const handleResolveDispute = async (ticketId, approved) => {
    const draft = resolutionDrafts[ticketId] ?? {};
    if (approved) {
      const amountValue = draft.adjustmentAmount ? Number(draft.adjustmentAmount) : 0;
      if (!draft.adjustmentAmount || Number.isNaN(amountValue) || amountValue <= 0) {
        setOperatorActionError('Enter a positive adjustment amount before approving.');
        return;
      }
    }
    setOperatorActionError(null);
    setOperatorActionMessage(null);
    setResolveLoadingId(ticketId);
    try {
      await prcService.resolveDispute(ticketId, {
        approved,
        adjustmentAmount: approved ? draft.adjustmentAmount : null,
        resolutionNote: draft.resolutionNote || null,
      });
      setOperatorActionMessage(`Dispute #${ticketId} ${approved ? 'approved' : 'rejected'}.`);
      setResolutionDrafts((drafts) => {
        const next = { ...drafts };
        delete next[ticketId];
        return next;
      });
      await fetchOpenDisputes();
      await fetchBillingHistory();
      if (isRider) {
        await fetchDisputes();
      }
    } catch (error) {
      console.error('Failed to resolve dispute', error);
      setOperatorActionError('Unable to update dispute status.');
    } finally {
      setResolveLoadingId(null);
    }
  };

  const handleExportLedger = async () => {
    setExportingLedger(true);
    setOperatorActionError(null);
    setOperatorActionMessage(null);
    try {
      const { data } = await prcService.exportLedger();
      const blob = new Blob([data], { type: 'text/csv' });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', 'ledger-export.csv');
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
      setOperatorActionMessage('Ledger export downloaded.');
    } catch (error) {
      console.error('Failed to export ledger', error);
      setOperatorActionError('Unable to download ledger export.');
    } finally {
      setExportingLedger(false);
    }
  };

  const handlePlanFormChange = (field, value) => {
    setPlanForm((current) => ({ ...current, [field]: value }));
  };

  const handlePlanFormSubmit = async (event) => {
    event.preventDefault();
    setPlanSubmitError(null);
    setPlanSubmitSuccess(null);

    if (!planForm.planName.trim()) {
      setPlanSubmitError('Plan name is required.');
      return;
    }
    if (planForm.baseFee === '' || Number.isNaN(Number(planForm.baseFee))) {
      setPlanSubmitError('Base fee must be a numeric value.');
      return;
    }
    if (planForm.perMinuteRate === '' || Number.isNaN(Number(planForm.perMinuteRate))) {
      setPlanSubmitError('Per-minute rate must be a numeric value.');
      return;
    }

    setPlanSubmitLoading(true);
    try {
      const payload = {
        planName: planForm.planName.trim(),
        baseFee: planForm.baseFee,
        perMinuteRate: planForm.perMinuteRate,
        ebikeSurcharge: planForm.ebikeSurcharge === '' ? null : planForm.ebikeSurcharge,
        membershipTier: planForm.membershipTier,
        cityId: planForm.cityId ? planForm.cityId.trim() : null,
        effectiveFrom: normalizeDateTimeInput(planForm.effectiveFrom),
        effectiveTo: normalizeDateTimeInput(planForm.effectiveTo),
        description: planForm.description ? planForm.description.trim() : null,
        publish: Boolean(planForm.publish),
      };

      if (editingPlanId) {
        await pricingAdminService.updatePlan(editingPlanId, payload);
        setPlanSubmitSuccess('Plan updated.');
      } else {
        await pricingAdminService.createPlan(payload);
        setPlanSubmitSuccess('Plan created.');
      }

      setEditingPlanId(null);
      setPlanForm(createEmptyPlanForm());
      await Promise.all([fetchAdminPlans(), fetchPlans()]);
    } catch (error) {
      console.error('Failed to save pricing plan', error);
      setPlanSubmitError('Unable to save pricing plan.');
    } finally {
      setPlanSubmitLoading(false);
    }
  };

  const handleEditPlan = (plan) => {
    setPlanSubmitError(null);
    setPlanSubmitSuccess(null);
    setEditingPlanId(plan.planVersionId);
    setPlanForm({
      ...createEmptyPlanForm(),
      planName: plan.planName ?? '',
      baseFee: plan.baseFee !== null && plan.baseFee !== undefined ? String(plan.baseFee) : '',
      perMinuteRate:
        plan.perMinuteRate !== null && plan.perMinuteRate !== undefined ? String(plan.perMinuteRate) : '',
      ebikeSurcharge:
        plan.ebikeSurcharge !== null && plan.ebikeSurcharge !== undefined ? String(plan.ebikeSurcharge) : '',
      effectiveFrom: toDateTimeLocalInput(plan.effectiveFrom),
      effectiveTo: toDateTimeLocalInput(plan.effectiveTo),
      description: plan.description ?? '',
    });
  };

  const handleCancelPlanEdit = () => {
    setEditingPlanId(null);
    setPlanForm(createEmptyPlanForm());
    setPlanSubmitError(null);
    setPlanSubmitSuccess(null);
  };

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      className="relative min-h-screen px-4 sm:px-6 lg:px-8 py-8"
    >
      <GuestHomeLink className="absolute left-4 top-4 z-20 sm:left-6 sm:top-6" />
      <div className="max-w-7xl mx-auto space-y-12">
        <header className="space-y-3">
          <h1 className="text-4xl font-bold text-gray-900 dark:text-white">Pricing & Billing</h1>
          <p className="text-gray-600 dark:text-gray-400 max-w-3xl">
            Explore current pricing plans, compare example trip costs, and (when signed in) review your recent billing
            activity.
          </p>
        </header>

        <section>
          <div className="flex items-center justify-between mb-6">
            <h2 className="text-2xl font-semibold text-gray-900 dark:text-white">Current Pricing Plans</h2>
            {plansLoading && (
              <span className="text-sm text-gray-500 dark:text-gray-400">Loading plans…</span>
            )}
          </div>
          {isAuthenticatedRider && pendingBalance !== null && (
            <div className="mb-6 rounded-xl border border-primary-200 bg-primary-50 px-4 py-3 text-sm text-primary-900 dark:border-primary-700 dark:bg-primary-900/30 dark:text-primary-100">
              Pending balance: {formatMoney(pendingBalance)}
            </div>
          )}

          {plansError && (
            <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-800 dark:bg-red-900/30 dark:text-red-200">
              {plansError}
            </div>
          )}

          {!plansLoading && plans.length === 0 && !plansError && (
            <div className="rounded-lg border border-gray-200 bg-white px-4 py-6 text-center text-gray-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300">
              No published pricing plans are available yet.
            </div>
          )}

          <div className="grid grid-cols-1 gap-6 md:grid-cols-2 xl:grid-cols-3">
            {plans.map((plan) => (
              <motion.div
                key={plan.planVersionId}
                whileHover={{ scale: 1.01 }}
                transition={{ type: 'spring', stiffness: 260, damping: 20 }}
                className="flex h-full flex-col rounded-2xl border border-gray-200 bg-white p-6 shadow-lg shadow-gray-200/30 dark:border-gray-700 dark:bg-gray-800 dark:shadow-none"
              >
                <div className="flex flex-col gap-3">
                  <div className="flex items-start justify-between gap-4">
                    <div>
                      <h3 className="text-2xl font-semibold text-gray-900 dark:text-white">{plan.planName}</h3>
                      {plan.description && (
                        <p className="mt-1 text-sm text-gray-600 dark:text-gray-400">{plan.description}</p>
                      )}
                    </div>
                    <div className="rounded-lg bg-gray-100 px-3 py-1 text-xs font-medium uppercase tracking-wide text-gray-600 dark:bg-gray-700 dark:text-gray-300">
                      Effective {formatDate(plan.effectiveFrom)}
                    </div>
                  </div>

                  <div className="grid grid-cols-1 gap-4 rounded-xl bg-gray-50 p-4 dark:bg-gray-900/40 sm:grid-cols-3">
                    <div>
                      <span className="text-xs uppercase tracking-wide text-gray-500 dark:text-gray-400">Base Fee</span>
                      <p className="text-lg font-semibold text-gray-900 dark:text-white">{formatMoney(plan.baseFee)}</p>
                    </div>
                    <div>
                      <span className="text-xs uppercase tracking-wide text-gray-500 dark:text-gray-400">Per Minute</span>
                      <p className="text-lg font-semibold text-gray-900 dark:text-white">{formatMoney(plan.perMinuteRate)}</p>
                    </div>
                    <div>
                      <span className="text-xs uppercase tracking-wide text-gray-500 dark:text-gray-400">E-Bike Surcharge</span>
                      <p className="text-lg font-semibold text-gray-900 dark:text-white">{formatMoney(plan.ebikeSurcharge)}</p>
                    </div>
                  </div>

                  <div className="rounded-xl border border-dashed border-gray-200 p-4 dark:border-gray-700">
                    <h4 className="text-sm font-semibold text-gray-900 dark:text-white">Example Trips</h4>
                    <div className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
                      {(plan.exampleCosts ?? []).map((example) => (
                        <div
                          key={`${example.durationMinutes}-${example.ebike}`}
                          className="rounded-lg bg-gray-100 px-3 py-2 text-sm text-gray-700 dark:bg-gray-900/60 dark:text-gray-300"
                        >
                          <div className="flex items-center justify-between">
                            <span>{example.durationMinutes} min</span>
                            {example.ebike ? (
                              <span className="rounded bg-purple-100 px-2 py-0.5 text-xs font-semibold text-purple-700 dark:bg-purple-900 dark:text-purple-200">
                                E-Bike
                              </span>
                            ) : (
                              <span className="rounded bg-blue-100 px-2 py-0.5 text-xs font-semibold text-blue-700 dark:bg-blue-900 dark:text-blue-200">
                                Standard
                              </span>
                            )}
                          </div>
                          <p className="mt-2 text-lg font-semibold text-gray-900 dark:text-white">
                            {formatMoney(example.estimatedTotal)}
                          </p>
                        </div>
                      ))}
                    </div>
                  </div>

                  <div className="text-xs text-gray-500 dark:text-gray-400">
                    {plan.effectiveTo ? (
                      <>Plan scheduled to end on {formatDate(plan.effectiveTo)}.</>
                    ) : (
                      <>Plan is currently active.</>
                    )}
                  </div>
                </div>
              </motion.div>
            ))}
          </div>
        </section>

        {isAuthenticatedRider && (
          <section className="space-y-6">
            <div className="flex items-center justify-between">
              <h2 className="text-2xl font-semibold text-gray-900 dark:text-white">My Billing History</h2>
              {historyLoading && (
                <span className="text-sm text-gray-500 dark:text-gray-400">Refreshing…</span>
              )}
            </div>

            {historyError && (
              <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-800 dark:bg-red-900/30 dark:text-red-200">
                {historyError}
              </div>
            )}

            {!historyLoading && history.length === 0 && !historyError && (
              <div className="rounded-lg border border-gray-200 bg-white px-4 py-6 text-center text-gray-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300">
                You do not have any billing entries yet.
              </div>
            )}

            {history.length > 0 && (
              <div className="grid gap-6 lg:grid-cols-2">
                <div className="space-y-3">
                  {history.map((entry) => {
                    const isActive = entry.ledgerEntryId === selectedEntryId;
                    return (
                      <button
                        key={entry.ledgerEntryId}
                        type="button"
                        onClick={() => setSelectedEntryId(entry.ledgerEntryId)}
                        className={`w-full rounded-2xl border px-4 py-4 text-left transition-transform ${
                          isActive
                            ? 'border-primary-500 bg-primary-50 shadow-lg shadow-primary-200/50 dark:border-primary-400 dark:bg-primary-900/20'
                            : 'border-gray-200 bg-white hover:-translate-y-0.5 hover:border-primary-200 hover:shadow-md dark:border-gray-700 dark:bg-gray-800'
                        }`}
                      >
                        <div className="flex items-center justify-between gap-4">
                          <div>
                            <p className="text-lg font-semibold text-gray-900 dark:text-white">{entry.planName}</p>
                            <p className="text-sm text-gray-500 dark:text-gray-400">{entry.summary}</p>
                          </div>
                          <div className="text-right">
                            <p className="text-lg font-semibold text-gray-900 dark:text-white">{formatMoney(entry.total)}</p>
                            <p className="text-xs text-gray-500 dark:text-gray-400">{formatDateTime(entry.startTime)}</p>
                            {entry.paymentReference && (
                              <p className="text-xs text-gray-400 dark:text-gray-500">Ref: {entry.paymentReference}</p>
                            )}
                          </div>
                        </div>
                        <div className="mt-3 flex flex-wrap items-center gap-3 text-xs text-gray-500 dark:text-gray-400">
                          <span>{entry.durationMinutes} minutes</span>
                          <span>•</span>
                          <span>{formatDistance(entry.distanceKm)}</span>
                          <span>•</span>
                          <span>Status: {entry.paymentStatus}</span>
                          {entry.adjustmentOfEntryId && (
                            <>
                              <span>•</span>
                              <span>Adjustment of #{entry.adjustmentOfEntryId}</span>
                            </>
                          )}
                        </div>
                      </button>
                    );
                  })}
                </div>

                <div className="rounded-2xl border border-gray-200 bg-white p-6 shadow-lg dark:border-gray-700 dark:bg-gray-800">
                  {summaryLoading && (
                    <div className="text-sm text-gray-500 dark:text-gray-400">Loading trip summary…</div>
                  )}

                  {summaryError && (
                    <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-800 dark:bg-red-900/30 dark:text-red-200">
                      {summaryError}
                    </div>
                  )}

                  {!summaryLoading && summary && !summaryError && (
                    <div className="space-y-5">
                      <div className="flex items-center justify-between">
                        <div>
                          <p className="text-sm text-gray-500 dark:text-gray-400">Plan</p>
                          <p className="text-xl font-semibold text-gray-900 dark:text-white">{summary.planName}</p>
                        </div>
                        <div className="text-right">
                          <p className="text-sm text-gray-500 dark:text-gray-400">Total</p>
                          <p className="text-2xl font-bold text-gray-900 dark:text-white">{formatMoney(summary.total)}</p>
                        </div>
                      </div>

                      <div className="grid gap-3 rounded-xl bg-gray-50 p-4 dark:bg-gray-900/40">
                        <div className="flex justify-between text-sm text-gray-600 dark:text-gray-300">
                          <span>Start</span>
                          <span>{formatDateTime(summary.startTime)}</span>
                        </div>
                        <div className="flex justify-between text-sm text-gray-600 dark:text-gray-300">
                          <span>End</span>
                          <span>{formatDateTime(summary.endTime)}</span>
                        </div>
                        <div className="flex justify-between text-sm text-gray-600 dark:text-gray-300">
                          <span>Duration</span>
                          <span>{summary.durationMinutes} minutes</span>
                        </div>
                        <div className="flex justify-between text-sm text-gray-600 dark:text-gray-300">
                          <span>Distance</span>
                          <span>{formatDistance(summary.distanceKm)}</span>
                        </div>
                      </div>

                      <div>
                        <h3 className="text-sm font-semibold text-gray-900 dark:text-white">Charge Breakdown</h3>
                        <div className="mt-3 space-y-2">
                          {(summary.charges ?? []).map((charge) => (
                            <div
                              key={`${charge.code}-${charge.amount}`}
                              className="flex items-start justify-between rounded-lg border border-dashed border-gray-200 px-3 py-2 text-sm dark:border-gray-700"
                            >
                              <div>
                                <p className="font-medium text-gray-900 dark:text-white">{charge.code}</p>
                                {charge.meta && Object.keys(charge.meta).length > 0 && (
                                  <div className="mt-1 flex flex-wrap gap-2 text-xs text-gray-500 dark:text-gray-400">
                                    {Object.entries(charge.meta).map(([key, value]) => (
                                      <span
                                        key={`${charge.code}-${key}`}
                                        className="rounded bg-gray-100 px-2 py-0.5 dark:bg-gray-900/60"
                                      >
                                        {key}: {value}
                                      </span>
                                    ))}
                                  </div>
                                )}
                              </div>
                              <p className="text-sm font-semibold text-gray-900 dark:text-white">{formatMoney(charge.amount)}</p>
                            </div>
                          ))}
                        </div>
                      </div>

                      {activeEntry && (
                        <div className="space-y-2">
                          <h3 className="text-sm font-semibold text-gray-900 dark:text-white">Payment Details</h3>
                          <div className="flex justify-between text-sm text-gray-600 dark:text-gray-300">
                            <span>Status</span>
                            <span>{formatPaymentStatus(activeEntry.paymentStatus)}</span>
                          </div>
                          {activeEntry.paymentReference && (
                            <div className="flex justify-between text-sm text-gray-600 dark:text-gray-300">
                              <span>Reference</span>
                              <span>{activeEntry.paymentReference}</span>
                            </div>
                          )}
                          {activeEntry.paymentProcessedAt && (
                            <div className="flex justify-between text-sm text-gray-600 dark:text-gray-300">
                              <span>Processed</span>
                              <span>{formatDateTime(activeEntry.paymentProcessedAt)}</span>
                            </div>
                          )}
                          {activeEntry.adjustmentOfEntryId && (
                            <div className="flex justify-between text-sm text-gray-600 dark:text-gray-300">
                              <span>Adjustment Of</span>
                              <span>#{activeEntry.adjustmentOfEntryId}</span>
                            </div>
                          )}
                        </div>
                      )}

                      <div className="space-y-3">
                        <h3 className="text-sm font-semibold text-gray-900 dark:text-white">Actions</h3>
                        <div className="flex flex-wrap gap-3">
                          {canSettlePayment && (
                            <button
                              type="button"
                              onClick={handleSettlePayment}
                              disabled={settlementLoading}
                              className="rounded-lg bg-primary-600 px-4 py-2 text-sm font-semibold text-white shadow hover:bg-primary-700 disabled:cursor-not-allowed disabled:bg-primary-400"
                            >
                              {settlementLoading ? 'Processing…' : 'Settle Payment'}
                            </button>
                          )}
                          <button
                            type="button"
                            onClick={handleDownloadReceipt}
                            disabled={!canDownloadReceipt || receiptLoading}
                            className="rounded-lg border border-primary-600 px-4 py-2 text-sm font-semibold text-primary-600 hover:bg-primary-50 disabled:cursor-not-allowed disabled:border-gray-300 disabled:text-gray-400 dark:border-primary-400 dark:text-primary-200 dark:hover:bg-primary-900/30"
                            title={canDownloadReceipt ? 'Download PDF receipt' : 'Receipt available once payment is settled'}
                          >
                            {receiptLoading ? 'Preparing…' : 'Download Receipt'}
                          </button>
                          {isRider && (
                            <button
                              type="button"
                              onClick={() => setDisputeFormOpen((open) => !open)}
                              className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-semibold text-gray-700 hover:bg-gray-100 dark:border-gray-600 dark:text-gray-200 dark:hover:bg-gray-700"
                            >
                              {disputeFormOpen ? 'Close Dispute Form' : 'Submit Dispute'}
                            </button>
                          )}
                        </div>
                        {billingActionError && (
                          <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 dark:border-red-800 dark:bg-red-900/30 dark:text-red-200">
                            {billingActionError}
                          </div>
                        )}
                        {billingActionMessage && (
                          <div className="rounded-lg border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-700 dark:border-green-900 dark:bg-green-900/30 dark:text-green-200">
                            {billingActionMessage}
                          </div>
                        )}
                      </div>

                      {isRider && disputeFormOpen && (
                        <form
                          onSubmit={handleSubmitDispute}
                          className="space-y-3 rounded-xl border border-dashed border-gray-200 p-4 dark:border-gray-700"
                        >
                          <div>
                            <label className="text-sm font-medium text-gray-700 dark:text-gray-200" htmlFor="dispute-reason">
                              Reason
                            </label>
                            <textarea
                              id="dispute-reason"
                              value={disputeReason}
                              onChange={(event) => setDisputeReason(event.target.value)}
                              rows={3}
                              className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-900 focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-200 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
                              placeholder="Describe what seems incorrect about this charge"
                            />
                          </div>
                          <div>
                            <label className="text-sm font-medium text-gray-700 dark:text-gray-200" htmlFor="dispute-evidence">
                              Evidence URL (optional)
                            </label>
                            <input
                              id="dispute-evidence"
                              type="url"
                              value={disputeEvidenceUrl}
                              onChange={(event) => setDisputeEvidenceUrl(event.target.value)}
                              className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-900 focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-200 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
                              placeholder="https://"
                            />
                          </div>
                          <div className="flex items-center gap-3">
                            <button
                              type="submit"
                              disabled={submitDisputeLoading}
                              className="rounded-lg bg-primary-600 px-4 py-2 text-sm font-semibold text-white shadow hover:bg-primary-700 disabled:cursor-not-allowed disabled:bg-primary-400"
                            >
                              {submitDisputeLoading ? 'Submitting…' : 'Send Dispute'}
                            </button>
                            <button
                              type="button"
                              onClick={() => setDisputeFormOpen(false)}
                              className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-semibold text-gray-700 hover:bg-gray-100 dark:border-gray-600 dark:text-gray-200 dark:hover:bg-gray-700"
                            >
                              Cancel
                            </button>
                          </div>
                          {submitDisputeError && (
                            <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 dark:border-red-800 dark:bg-red-900/30 dark:text-red-200">
                              {submitDisputeError}
                            </div>
                          )}
                          {submitDisputeSuccess && (
                            <div className="rounded-lg border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-700 dark:border-green-900 dark:bg-green-900/30 dark:text-green-200">
                              {submitDisputeSuccess}
                            </div>
                          )}
                        </form>
                      )}
                    </div>
                  )}

                  {!summaryLoading && !summary && !summaryError && (
                    <div className="text-sm text-gray-500 dark:text-gray-400">
                      Select a billing entry to view detailed charges.
                    </div>
                  )}
                </div>
              </div>
            )}
          </section>
        )}

        {isRider && (
          <section className="space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="text-2xl font-semibold text-gray-900 dark:text-white">My Disputes</h2>
              {disputesLoading && (
                <span className="text-sm text-gray-500 dark:text-gray-400">Refreshing…</span>
              )}
            </div>

            {disputesError && (
              <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-800 dark:bg-red-900/30 dark:text-red-200">
                {disputesError}
              </div>
            )}

            {!disputesLoading && disputes.length === 0 && !disputesError && (
              <div className="rounded-lg border border-gray-200 bg-white px-4 py-6 text-center text-gray-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300">
                You have not opened any disputes yet.
              </div>
            )}

            {disputes.length > 0 && (
              <div className="grid gap-4">
                {disputes.map((ticket) => (
                  <div
                    key={ticket.id}
                    className="rounded-2xl border border-gray-200 bg-white p-5 shadow dark:border-gray-700 dark:bg-gray-800"
                  >
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div>
                        <p className="text-lg font-semibold text-gray-900 dark:text-white">Ticket #{ticket.id}</p>
                        <p className="text-sm text-gray-500 dark:text-gray-400">Ledger #{ticket.ledgerEntryId}</p>
                      </div>
                      <div className="text-right">
                        <span className="inline-flex items-center rounded-full bg-gray-100 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-gray-600 dark:bg-gray-900/50 dark:text-gray-300">
                          {formatDisputeStatus(ticket.status)}
                        </span>
                        <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">Opened {formatDateTime(ticket.createdAt)}</p>
                        {ticket.resolvedAt && (
                          <p className="text-xs text-gray-500 dark:text-gray-400">Closed {formatDateTime(ticket.resolvedAt)}</p>
                        )}
                      </div>
                    </div>
                    <p className="mt-3 text-sm text-gray-700 dark:text-gray-200">{ticket.reason}</p>
                    {ticket.evidenceUrl && (
                      <a
                        href={ticket.evidenceUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="mt-3 inline-flex text-sm font-medium text-primary-600 hover:underline dark:text-primary-300"
                      >
                        View submitted evidence
                      </a>
                    )}
                    {ticket.resolutionNote && (
                      <div className="mt-3 rounded-lg bg-gray-50 px-3 py-2 text-sm text-gray-700 dark:bg-gray-900/40 dark:text-gray-300">
                        <span className="font-semibold">Resolution:</span> {ticket.resolutionNote}
                      </div>
                    )}
                    {ticket.adjustmentEntryId && (
                      <p className="mt-2 text-xs text-gray-500 dark:text-gray-400">
                        Adjustment recorded under ledger #{ticket.adjustmentEntryId}
                      </p>
                    )}
                  </div>
                ))}
              </div>
            )}
          </section>
        )}

        {isOperator && (
          <section className="space-y-8">
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h2 className="text-2xl font-semibold text-gray-900 dark:text-white">Operations Toolkit</h2>
              </div>

              <div className="rounded-2xl border border-gray-200 bg-white p-6 shadow dark:border-gray-700 dark:bg-gray-800">
                <div className="flex flex-wrap items-center gap-3">
                  <button
                    type="button"
                    onClick={handleExportLedger}
                    disabled={exportingLedger}
                    className="rounded-lg bg-primary-600 px-4 py-2 text-sm font-semibold text-white shadow hover:bg-primary-700 disabled:cursor-not-allowed disabled:bg-primary-400"
                  >
                    {exportingLedger ? 'Preparing export…' : 'Download Ledger CSV'}
                  </button>
                </div>
                {operatorActionError && (
                  <div className="mt-3 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 dark:border-red-800 dark:bg-red-900/30 dark:text-red-200">
                    {operatorActionError}
                  </div>
                )}
                {operatorActionMessage && (
                  <div className="mt-3 rounded-lg border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-700 dark:border-green-900 dark:bg-green-900/30 dark:text-green-200">
                    {operatorActionMessage}
                  </div>
                )}
              </div>

              <div className="space-y-4">
                <div className="flex items-center justify-between">
                  <h3 className="text-xl font-semibold text-gray-900 dark:text-white">Open Disputes</h3>
                  {openDisputesLoading && (
                    <span className="text-sm text-gray-500 dark:text-gray-400">Refreshing…</span>
                  )}
                </div>

                {openDisputesError && (
                  <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-800 dark:bg-red-900/30 dark:text-red-200">
                    {openDisputesError}
                  </div>
                )}

                {!openDisputesLoading && openDisputes.length === 0 && !openDisputesError && (
                  <div className="rounded-lg border border-gray-200 bg-white px-4 py-6 text-center text-gray-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300">
                    All caught up—no open disputes awaiting review.
                  </div>
                )}

                {openDisputes.length > 0 && (
                  <div className="grid gap-4">
                    {openDisputes.map((ticket) => {
                      const draft = resolutionDrafts[ticket.id] ?? {};
                      return (
                        <div
                          key={ticket.id}
                          className="rounded-2xl border border-gray-200 bg-white p-5 shadow dark:border-gray-700 dark:bg-gray-800"
                        >
                          <div className="flex flex-wrap items-start justify-between gap-3">
                            <div>
                              <p className="text-lg font-semibold text-gray-900 dark:text-white">Ticket #{ticket.id}</p>
                              <p className="text-sm text-gray-500 dark:text-gray-400">Rider #{ticket.riderId}</p>
                              <p className="text-sm text-gray-500 dark:text-gray-400">Ledger #{ticket.ledgerEntryId}</p>
                            </div>
                            <div className="text-right text-xs text-gray-500 dark:text-gray-400">
                              <p>Created {formatDateTime(ticket.createdAt)}</p>
                              {ticket.reason && <p className="mt-2 text-sm text-gray-300 dark:text-gray-500">{ticket.reason}</p>}
                            </div>
                          </div>

                          <div className="mt-4 grid gap-3 md:grid-cols-2">
                            <div>
                              <label className="text-xs font-semibold uppercase tracking-wide text-gray-600 dark:text-gray-300" htmlFor={`adjustment-${ticket.id}`}>
                                Adjustment amount (USD)
                              </label>
                              <input
                                id={`adjustment-${ticket.id}`}
                                type="number"
                                step="0.01"
                                value={draft.adjustmentAmount ?? ''}
                                onChange={(event) => handleResolutionDraftChange(ticket.id, 'adjustmentAmount', event.target.value)}
                                className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-900 focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-200 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
                                placeholder="0.00"
                              />
                            </div>
                            <div>
                              <label className="text-xs font-semibold uppercase tracking-wide text-gray-600 dark:text-gray-300" htmlFor={`resolution-${ticket.id}`}>
                                Resolution note
                              </label>
                              <textarea
                                id={`resolution-${ticket.id}`}
                                rows={2}
                                value={draft.resolutionNote ?? ''}
                                onChange={(event) => handleResolutionDraftChange(ticket.id, 'resolutionNote', event.target.value)}
                                className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-900 focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-200 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
                                placeholder="Share decision notes for audit trail"
                              />
                            </div>
                          </div>

                          <div className="mt-4 flex flex-wrap gap-3">
                            <button
                              type="button"
                              onClick={() => handleResolveDispute(ticket.id, true)}
                              disabled={resolveLoadingId === ticket.id}
                              className="rounded-lg bg-primary-600 px-4 py-2 text-sm font-semibold text-white shadow hover:bg-primary-700 disabled:cursor-not-allowed disabled:bg-primary-400"
                            >
                              {resolveLoadingId === ticket.id ? 'Saving…' : 'Approve & Adjust'}
                            </button>
                            <button
                              type="button"
                              onClick={() => handleResolveDispute(ticket.id, false)}
                              disabled={resolveLoadingId === ticket.id}
                              className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-semibold text-gray-700 hover:bg-gray-100 disabled:cursor-not-allowed disabled:border-gray-300 dark:border-gray-600 dark:text-gray-200 dark:hover:bg-gray-700"
                            >
                              {resolveLoadingId === ticket.id ? 'Saving…' : 'Reject Dispute'}
                            </button>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            </div>

            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="text-xl font-semibold text-gray-900 dark:text-white">Manage Pricing Plans</h3>
                {adminPlansLoading && (
                  <span className="text-sm text-gray-500 dark:text-gray-400">Refreshing…</span>
                )}
              </div>

              {adminPlansError && (
                <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-800 dark:bg-red-900/30 dark:text-red-200">
                  {adminPlansError}
                </div>
              )}

              <div className="grid gap-6 lg:grid-cols-2">
                <form
                  onSubmit={handlePlanFormSubmit}
                  className="space-y-4 rounded-2xl border border-gray-200 bg-white p-6 shadow dark:border-gray-700 dark:bg-gray-800"
                >
                  <div>
                    <h4 className="text-lg font-semibold text-gray-900 dark:text-white">
                      {editingPlanId ? 'Edit Pricing Plan' : 'Create Pricing Plan'}
                    </h4>
                    <p className="text-sm text-gray-500 dark:text-gray-400">
                      Publishing makes the plan available to riders immediately after creation.
                    </p>
                  </div>

                  <div>
                    <label className="text-sm font-medium text-gray-700 dark:text-gray-200" htmlFor="plan-name">
                      Plan name
                    </label>
                    <input
                      id="plan-name"
                      type="text"
                      value={planForm.planName}
                      onChange={(event) => handlePlanFormChange('planName', event.target.value)}
                      className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-900 focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-200 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
                      required
                    />
                  </div>

                  <div className="grid gap-4 sm:grid-cols-2">
                    <div>
                      <label className="text-sm font-medium text-gray-700 dark:text-gray-200" htmlFor="plan-baseFee">
                        Base fee (USD)
                      </label>
                      <input
                        id="plan-baseFee"
                        type="number"
                        step="0.01"
                        value={planForm.baseFee}
                        onChange={(event) => handlePlanFormChange('baseFee', event.target.value)}
                        className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-900 focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-200 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
                        required
                      />
                    </div>
                    <div>
                      <label className="text-sm font-medium text-gray-700 dark:text-gray-200" htmlFor="plan-perMinute">
                        Per-minute rate (USD)
                      </label>
                      <input
                        id="plan-perMinute"
                        type="number"
                        step="0.01"
                        value={planForm.perMinuteRate}
                        onChange={(event) => handlePlanFormChange('perMinuteRate', event.target.value)}
                        className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-900 focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-200 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
                        required
                      />
                    </div>
                  </div>

                  <div className="grid gap-4 sm:grid-cols-2">
                    <div>
                      <label className="text-sm font-medium text-gray-700 dark:text-gray-200" htmlFor="plan-ebike">
                        E-bike surcharge (USD)
                      </label>
                      <input
                        id="plan-ebike"
                        type="number"
                        step="0.01"
                        value={planForm.ebikeSurcharge}
                        onChange={(event) => handlePlanFormChange('ebikeSurcharge', event.target.value)}
                        className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-900 focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-200 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
                        placeholder="0.00"
                      />
                    </div>
                    <div>
                      <label className="text-sm font-medium text-gray-700 dark:text-gray-200" htmlFor="plan-membership">
                        Membership tier
                      </label>
                      <select
                        id="plan-membership"
                        value={planForm.membershipTier}
                        onChange={(event) => handlePlanFormChange('membershipTier', event.target.value)}
                        className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-900 focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-200 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
                      >
                        {membershipOptions.map((option) => (
                          <option key={option} value={option}>
                            {option}
                          </option>
                        ))}
                      </select>
                    </div>
                  </div>

                  <div className="grid gap-4 sm:grid-cols-2">
                    <div>
                      <label className="text-sm font-medium text-gray-700 dark:text-gray-200" htmlFor="plan-effectiveFrom">
                        Effective from
                      </label>
                      <input
                        id="plan-effectiveFrom"
                        type="datetime-local"
                        value={planForm.effectiveFrom}
                        onChange={(event) => handlePlanFormChange('effectiveFrom', event.target.value)}
                        className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-900 focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-200 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
                      />
                    </div>
                    <div>
                      <label className="text-sm font-medium text-gray-700 dark:text-gray-200" htmlFor="plan-effectiveTo">
                        Effective to
                      </label>
                      <input
                        id="plan-effectiveTo"
                        type="datetime-local"
                        value={planForm.effectiveTo}
                        onChange={(event) => handlePlanFormChange('effectiveTo', event.target.value)}
                        className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-900 focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-200 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
                      />
                    </div>
                  </div>

                  <div>
                    <label className="text-sm font-medium text-gray-700 dark:text-gray-200" htmlFor="plan-city">
                      City code (optional)
                    </label>
                    <input
                      id="plan-city"
                      type="text"
                      value={planForm.cityId}
                      onChange={(event) => handlePlanFormChange('cityId', event.target.value)}
                      className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-900 focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-200 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
                      placeholder="e.g., MTL"
                    />
                  </div>

                  <div>
                    <label className="text-sm font-medium text-gray-700 dark:text-gray-200" htmlFor="plan-description">
                      Description
                    </label>
                    <textarea
                      id="plan-description"
                      rows={3}
                      value={planForm.description}
                      onChange={(event) => handlePlanFormChange('description', event.target.value)}
                      className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-900 focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-200 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
                      placeholder="Optional summary riders will see"
                    />
                  </div>

                  <label className="flex items-center gap-2 text-sm font-medium text-gray-700 dark:text-gray-200">
                    <input
                      type="checkbox"
                      checked={planForm.publish}
                      onChange={(event) => handlePlanFormChange('publish', event.target.checked)}
                      className="h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500"
                    />
                    Publish plan after saving
                  </label>

                  <div className="flex flex-wrap items-center gap-3">
                    <button
                      type="submit"
                      disabled={planSubmitLoading}
                      className="rounded-lg bg-primary-600 px-4 py-2 text-sm font-semibold text-white shadow hover:bg-primary-700 disabled:cursor-not-allowed disabled:bg-primary-400"
                    >
                      {planSubmitLoading ? 'Saving…' : editingPlanId ? 'Update Plan' : 'Create Plan'}
                    </button>
                    {editingPlanId && (
                      <button
                        type="button"
                        onClick={handleCancelPlanEdit}
                        className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-semibold text-gray-700 hover:bg-gray-100 dark:border-gray-600 dark:text-gray-200 dark:hover:bg-gray-700"
                      >
                        Cancel
                      </button>
                    )}
                  </div>

                  {planSubmitError && (
                    <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 dark:border-red-800 dark:bg-red-900/30 dark:text-red-200">
                      {planSubmitError}
                    </div>
                  )}
                  {planSubmitSuccess && (
                    <div className="rounded-lg border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-700 dark:border-green-900 dark:bg-green-900/30 dark:text-green-200">
                      {planSubmitSuccess}
                    </div>
                  )}
                </form>

                <div className="space-y-3">
                  {adminPlans.length === 0 ? (
                    <div className="rounded-2xl border border-gray-200 bg-white px-4 py-6 text-center text-gray-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300">
                      No plan versions recorded yet.
                    </div>
                  ) : (
                    adminPlans.map((plan) => (
                      <div
                        key={plan.planVersionId}
                        className="rounded-2xl border border-gray-200 bg-white p-5 shadow dark:border-gray-700 dark:bg-gray-800"
                      >
                        <div className="flex flex-wrap items-start justify-between gap-3">
                          <div>
                            <p className="text-lg font-semibold text-gray-900 dark:text-white">{plan.planName}</p>
                            <p className="text-sm text-gray-500 dark:text-gray-400">
                              Base {formatMoney(plan.baseFee)} • Per minute {formatMoney(plan.perMinuteRate)}
                            </p>
                          </div>
                          <button
                            type="button"
                            onClick={() => handleEditPlan(plan)}
                            className="text-sm font-semibold text-primary-600 hover:underline dark:text-primary-300"
                          >
                            Edit
                          </button>
                        </div>
                        <div className="mt-3 grid gap-2 text-sm text-gray-600 dark:text-gray-300 sm:grid-cols-2">
                          <div>Effective from {formatDateTime(plan.effectiveFrom)}</div>
                          <div>
                            {plan.effectiveTo ? `Effective to ${formatDateTime(plan.effectiveTo)}` : 'No scheduled end'}
                          </div>
                        </div>
                        {plan.description && (
                          <p className="mt-3 text-sm text-gray-600 dark:text-gray-300">{plan.description}</p>
                        )}
                      </div>
                    ))
                  )}
                </div>
              </div>
            </div>
          </section>
        )}
      </div>
    </motion.div>
  );
};

export default Pricing;
