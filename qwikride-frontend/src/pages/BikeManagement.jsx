import { useState, useEffect, useMemo, useCallback, useRef } from 'react';
import { motion } from 'framer-motion';
import { useAuth } from '../hooks/useAuth';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';
import ReserveBikeModal from '../components/ReserveBikeModal';
import ReturnBikeModal from '../components/ReturnBikeModal';
import MoveBikeModal from '../components/MoveBikeModal';

const BikeManagement = () => {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');
  const [bikes, setBikes] = useState([]);
  const [stations, setStations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showReserveModal, setShowReserveModal] = useState(false);
  const [showMoveModal, setShowMoveModal] = useState(false);
  const [showReturnModal, setShowReturnModal] = useState(false);
  const [bikeToReturn, setBikeToReturn] = useState(null);
  const [returnStationId, setReturnStationId] = useState('');
  const [reservationData, setReservationData] = useState({ stationId: '', userId: '', expiresAfterMinutes: 15 });
  const [moveData, setMoveData] = useState({ bikeId: '', newStationId: '', operatorId: '' });
  
  // Use refs to prevent duplicate API calls
  const isLoadingRef = useRef(false);
  const lastLoadTimeRef = useRef(0);

  // Robust deduplication function using Map for O(1) lookup
  const deduplicateById = useCallback((items, idKey = 'id') => {
    if (!Array.isArray(items)) return [];
    const seen = new Map();
    const result = [];
    
    for (const item of items) {
      if (!item || !item[idKey]) continue; // Skip invalid items
      const id = item[idKey];
      if (!seen.has(id)) {
        seen.set(id, true);
        result.push(item);
      }
    }
    
    return result;
  }, []);

  // Update userId in forms when user changes
  useEffect(() => {
    if (user?.id) {
      setReservationData(prev => ({ ...prev, userId: user.id }));
      setMoveData(prev => ({ ...prev, operatorId: user.id }));
    }
  }, [user?.id]);

  // Load data on mount
  useEffect(() => {
    loadData();
  }, []);

  const loadData = useCallback(async () => {
    // Prevent concurrent loads
    if (isLoadingRef.current) {
      console.log('Load already in progress, skipping...');
      return;
    }

    // Throttle loads to max once per 2 seconds
    const now = Date.now();
    if (now - lastLoadTimeRef.current < 2000) {
      console.log('Load throttled, skipping...');
      return;
    }

    try {
      isLoadingRef.current = true;
      lastLoadTimeRef.current = now;
      setLoading(true);
      
      console.log('Loading data...');
      const [bikesResponse, stationsResponse] = await Promise.all([
        api.get('/bikes'),
        api.get('/stations')
      ]);
      
      // Handle paginated response (if content exists) or direct array
      const bikesData = bikesResponse.data?.content || bikesResponse.data || [];
      const stationsData = stationsResponse.data || [];
      
      // Properly deduplicate by ID
      const uniqueBikes = deduplicateById(Array.isArray(bikesData) ? bikesData : []);
      const uniqueStations = deduplicateById(Array.isArray(stationsData) ? stationsData : []);
      
      console.log(`Loaded ${uniqueBikes.length} unique bikes, ${uniqueStations.length} unique stations`);
      
      setBikes(uniqueBikes);
      setStations(uniqueStations);
      setError('');
    } catch (err) {
      console.error('Failed to load data:', err);
      setError('Failed to load data. Please refresh the page.');
    } finally {
      setLoading(false);
      isLoadingRef.current = false;
    }
  }, [deduplicateById]);

  const showSuccess = useCallback((message) => {
    setSuccessMessage(message);
    setTimeout(() => setSuccessMessage(''), 5000);
  }, []);

  const reserveBike = async () => {
    try {
      setError('');
      if (!user?.id) {
        setError('Please log in to reserve a bike.');
        return;
      }

      if (!reservationData.stationId) {
        setError('Please select a station.');
        return;
      }

      const payload = {
        stationId: Number(reservationData.stationId),
        userId: Number(user.id),
        expiresAfterMinutes: Number(reservationData.expiresAfterMinutes) || 15
      };

      console.log('Reserving bike with payload:', payload);
      await api.post('/bikes/reserve', payload);
      
      setShowReserveModal(false);
      setReservationData({ stationId: '', userId: user.id, expiresAfterMinutes: 15 });
      showSuccess('Bike reserved successfully! You have 15 minutes to checkout.');
      await loadData();
    } catch (err) {
      console.error('Reserve bike error:', err);
      const errorMessage = err.response?.data?.message || err.response?.data?.error || 'Failed to reserve bike. Please try again.';
      setError(`Error: ${errorMessage}`);
    }
  };

  const checkoutBike = async (bikeId) => {
    try {
      setError('');
      if (!user?.id) {
        setError('Please log in to checkout a bike.');
        return;
      }

      if (!bikeId) {
        setError('Invalid bike ID.');
        return;
      }

      // Find the bike to verify it exists and is in a valid state
      const bike = bikes.find(b => b.id === bikeId);
      if (!bike) {
        setError('Bike not found.');
        await loadData(); // Refresh data
        return;
      }

      if (bike.status !== 'AVAILABLE' && bike.status !== 'RESERVED') {
        setError(`Bike is ${bike.status} and cannot be checked out.`);
        await loadData(); // Refresh data
        return;
      }

      // If reserved, check if it's reserved by this user
      if (bike.status === 'RESERVED' && bike.reservedByUserId && Number(bike.reservedByUserId) !== Number(user.id)) {
        setError('This bike is reserved by another user.');
        await loadData(); // Refresh data
        return;
      }

      const payload = { 
        bikeId: String(bikeId), // UUID as string
        userId: Number(user.id) 
      };
      
      console.log('Checking out bike with payload:', payload);
      const response = await api.post('/bikes/checkout', payload);
      console.log('Checkout response:', response.data);
      
      showSuccess('Bike checked out successfully! Enjoy your ride.');
      await loadData();
    } catch (err) {
      console.error('Checkout bike error:', err);
      console.error('Error response:', err.response?.data);
      const errorMessage = err.response?.data?.message || err.response?.data?.error || 'Failed to checkout bike. Please try again.';
      setError(errorMessage);
      await loadData(); // Refresh to get latest state
    }
  };

  const openReturnModal = (bikeId) => {
    if (!bikeId) {
      setError('Invalid bike ID.');
      return;
    }

    // Find the bike
    const bike = bikes.find(b => b.id === bikeId);
    if (!bike) {
      setError('Bike not found.');
      loadData(); // Refresh data
      return;
    }
    
    // Verify bike is IN_USE
    if (bike.status !== 'IN_USE') {
      setError(`This bike is ${bike.status}, not in use.`);
      return;
    }

    // Verify it's the user's bike
    if (!bike.currentUserId || Number(bike.currentUserId) !== Number(user?.id)) {
      setError('You can only return bikes that you have checked out.');
      return;
    }

    setBikeToReturn(bikeId);
    setReturnStationId('');
    setShowReturnModal(true);
  };

  const returnBike = async () => {
    if (!returnStationId) {
      setError('Please select a station to return the bike to.');
      return;
    }

    if (!bikeToReturn) {
      setError('No bike selected to return.');
      return;
    }

    try {
      setError('');
      if (!user?.id) {
        setError('Please log in to return a bike.');
        return;
      }

      // Find the bike again to verify state
      const bike = bikes.find(b => b.id === bikeToReturn);
      if (!bike) {
        setError('Bike not found.');
        await loadData();
        return;
      }

      if (bike.status !== 'IN_USE') {
        setError(`Bike is ${bike.status}, not in use.`);
        await loadData();
        return;
      }

      if (!bike.currentUserId || Number(bike.currentUserId) !== Number(user.id)) {
        setError('You can only return bikes that you have checked out.');
        await loadData();
        return;
      }

      // Find the station using deduplicated stations
      const selectedReturnStation = uniqueStations.find(s => s.id === Number(returnStationId));
      if (!selectedReturnStation) {
        setError('Selected station not found.');
        return;
      }

      // Check if station has capacity
      if (selectedReturnStation.currentBikeCount >= selectedReturnStation.capacity) {
        setError('Selected station is full. Please choose another station.');
        return;
      }

      if (selectedReturnStation.status === 'OUT_OF_SERVICE') {
        setError('Selected station is out of service. Please choose another station.');
        return;
      }

      const durationMinutes = Math.random() * 60 + 10;
      const distanceKm = Math.random() * 20 + 1;
      
      const payload = {
        bikeId: String(bikeToReturn), // UUID as string
        returnStationId: Number(returnStationId),
        userId: Number(user.id),
        durationMinutes: Number(durationMinutes.toFixed(2)),
        distanceKm: Number(distanceKm.toFixed(2))
      };
      
      console.log('Returning bike with payload:', JSON.stringify(payload, null, 2));
      const response = await api.post('/bikes/return', payload);
      console.log('Return response:', response.data);
      
      setShowReturnModal(false);
      setBikeToReturn(null);
      setReturnStationId('');
      showSuccess('Bike returned successfully!');
      await loadData();
    } catch (err) {
      console.error('Return bike error:', err);
      console.error('Error response:', err.response?.data);
      const errorMessage = err.response?.data?.message || err.response?.data?.error || 'Failed to return bike. Please try again.';
      setError(errorMessage);
      await loadData(); // Refresh to get latest state
    }
  };

  const moveBike = async () => {
    try {
      setError('');
      if (!user?.id) {
        setError('Please log in to move bikes.');
        return;
      }

      if (!moveData.newStationId) {
        setError('Please select a destination station.');
        return;
      }

      if (!moveData.bikeId) {
        setError('No bike selected to move.');
        return;
      }

      const payload = {
        bikeId: String(moveData.bikeId),
        newStationId: Number(moveData.newStationId),
        operatorId: Number(user.id)
      };

      console.log('Moving bike with payload:', JSON.stringify(payload, null, 2));
      await api.post('/bikes/move', payload);
      
      setShowMoveModal(false);
      setMoveData({ bikeId: '', newStationId: '', operatorId: user.id });
      showSuccess('Bike moved successfully!');
      await loadData();
    } catch (err) {
      console.error('Move bike error:', err);
      const errorMessage = err.response?.data?.message || err.response?.data?.error || 'Failed to move bike. Please try again.';
      setError(errorMessage);
    }
  };

  const createBike = async (type, stationId) => {
    try {
      setError('');
      const payload = { type, stationId: Number(stationId) };
      console.log('Creating bike with payload:', payload);
      await api.post('/bikes/create', payload);
      showSuccess(`${type} bike created successfully!`);
      await loadData();
    } catch (err) {
      console.error('Create bike error:', err);
      const errorMessage = err.response?.data?.message || err.response?.data?.error || 'Failed to create bike. Please try again.';
      setError(errorMessage);
    }
  };

  const toggleStationStatus = async (stationId) => {
    try {
      setError('');
      const station = uniqueStations.find(s => s.id === stationId);
      if (!station) return;
      
      const newStatus = station.status === 'ACTIVE' ? 'OUT_OF_SERVICE' : 'ACTIVE';
      await api.patch(`/operator/stations/${stationId}/status`, { status: newStatus });
      
      showSuccess(`Station status updated to ${newStatus}`);
      await loadData();
    } catch (err) {
      console.error('Toggle station status error:', err);
      const errorMessage = err.response?.data?.message || err.response?.data?.error || 'Failed to update station status. Please try again.';
      setError(errorMessage);
    }
  };

  // Memoized deduplicated data
  const uniqueStations = useMemo(() => {
    const deduplicated = deduplicateById(stations);
    console.log(`Deduplicated stations: ${deduplicated.length} from ${stations.length}`);
    return deduplicated;
  }, [stations, deduplicateById]);

  const uniqueBikes = useMemo(() => {
    const deduplicated = deduplicateById(bikes);
    console.log(`Deduplicated bikes: ${deduplicated.length} from ${bikes.length}`);
    return deduplicated;
  }, [bikes, deduplicateById]);

  // Get displayable bikes (at stations or IN_USE)
  const displayableBikes = useMemo(() => {
    return uniqueBikes.filter(bike => {
      if (bike.stationId) return true; // At a station
      if (bike.status === 'IN_USE') return true; // In use
      return false; // Orphaned bikes
    });
  }, [uniqueBikes]);

  // Get user's active bike
  const userActiveBike = useMemo(() => {
    if (!user?.id) return null;
    return displayableBikes.find(b => 
      b.status === 'IN_USE' && 
      b.currentUserId && 
      Number(b.currentUserId) === Number(user.id)
    );
  }, [displayableBikes, user?.id]);

  // Helper functions for station stats
  const getAvailableBikesByStation = useCallback((stationId) => {
    return uniqueBikes.filter(b => b.stationId === stationId && b.status === 'AVAILABLE');
  }, [uniqueBikes]);

  const getReservedBikesByStation = useCallback((stationId) => {
    return uniqueBikes.filter(b => b.stationId === stationId && b.status === 'RESERVED');
  }, [uniqueBikes]);

  const getInUseBikesByStation = useCallback((stationId) => {
    return uniqueBikes.filter(b => b.stationId === stationId && b.status === 'IN_USE');
  }, [uniqueBikes]);

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-xl text-gray-600 dark:text-gray-400">Loading bike management...</div>
      </div>
    );
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      className="min-h-screen px-4 sm:px-6 lg:px-8 py-8"
    >
      {/* Back to Home Button */}
      <motion.button
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        onClick={() => navigate('/')}
        className="fixed top-24 right-6 z-[100] bg-white/95 dark:bg-gray-800/95 backdrop-blur-xl px-4 py-2.5 rounded-xl shadow-lg border border-gray-200/50 dark:border-gray-700/50 hover:shadow-xl transition-all group"
      >
        <div className="flex items-center gap-2">
          <svg className="w-4 h-4 text-gray-600 dark:text-gray-400 group-hover:text-primary-900 dark:group-hover:text-gray-100 transition-colors" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
          </svg>
          <span className="text-sm font-medium text-gray-700 dark:text-gray-300 group-hover:text-primary-900 dark:group-hover:text-gray-100 transition-colors">
            Back to Home
          </span>
        </div>
      </motion.button>

      <div className="max-w-7xl mx-auto">
        <div className="mb-8">
          <h1 className="text-4xl font-bold text-gray-900 dark:text-white mb-2">
            Bike Management System
          </h1>
          <p className="text-gray-600 dark:text-gray-400">
            Manage bike reservations, checkouts, returns, and rebalancing
          </p>
          
          {/* Success Message */}
          {successMessage && (
            <div className="mt-4 p-4 bg-green-100 dark:bg-green-900 border border-green-400 dark:border-green-700 text-green-700 dark:text-green-200 rounded-lg flex items-center justify-between">
              <span>{successMessage}</span>
              <button 
                onClick={() => setSuccessMessage('')}
                className="ml-4 text-sm underline hover:no-underline"
              >
                Dismiss
              </button>
            </div>
          )}

          {/* Error Message */}
          {error && (
            <div className="mt-4 p-4 bg-red-100 dark:bg-red-900 border border-red-400 dark:border-red-700 text-red-700 dark:text-red-200 rounded-lg flex items-center justify-between">
              <span>{error}</span>
              <button 
                onClick={() => setError('')}
                className="ml-4 text-sm underline hover:no-underline"
              >
                Dismiss
              </button>
            </div>
          )}

          {/* User's Active Bike Notice */}
          {userActiveBike && (
            <div className="mt-4 p-4 bg-blue-100 dark:bg-blue-900 border border-blue-400 dark:border-blue-700 text-blue-700 dark:text-blue-200 rounded-lg">
              <p className="font-medium">You have an active bike:</p>
              <p className="text-sm">Bike ID: {userActiveBike.id.substring(0, 8)}... - Click "Return" in the bike list below to return it.</p>
            </div>
          )}
        </div>

        {/* Station Overview */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mb-8">
          {uniqueStations.map((station) => (
            <motion.div
              key={`station-${station.id}`}
              whileHover={{ scale: 1.02 }}
              className="bg-white dark:bg-gray-800 rounded-lg shadow-lg p-6 border border-gray-200 dark:border-gray-700"
            >
              <div className="flex items-center justify-between mb-4">
                <h3 className="text-xl font-semibold text-gray-900 dark:text-white">
                  {station.name}
                </h3>
                <span className={`px-2 py-1 rounded-full text-xs font-medium ${
                  station.status === 'ACTIVE' 
                    ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200'
                    : 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200'
                }`}>
                  {station.status}
                </span>
              </div>
              
              <div className="space-y-2 mb-4">
                <div className="flex justify-between">
                  <span className="text-gray-600 dark:text-gray-400">Capacity:</span>
                  <span className="font-medium">{station.currentBikeCount}/{station.capacity}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-600 dark:text-gray-400">Available:</span>
                  <span className="font-medium text-green-600 dark:text-green-400">
                    {getAvailableBikesByStation(station.id).length}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-600 dark:text-gray-400">Reserved:</span>
                  <span className="font-medium text-yellow-600 dark:text-yellow-400">
                    {getReservedBikesByStation(station.id).length}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-600 dark:text-gray-400">In Use:</span>
                  <span className="font-medium text-blue-600 dark:text-blue-400">
                    {getInUseBikesByStation(station.id).length}
                  </span>
                </div>
              </div>

              <div className="space-y-2">
                <button
                  onClick={() => {
                    setReservationData({ 
                      stationId: station.id,
                      userId: user?.id || '',
                      expiresAfterMinutes: 15 
                    });
                    setShowReserveModal(true);
                  }}
                  className="w-full bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-lg transition-colors"
                >
                  Reserve Bike
                </button>
                
                {user?.role === 'OPERATOR' && (
                  <>
                    <div className="grid grid-cols-2 gap-2">
                      <button
                        onClick={() => createBike('STANDARD', station.id)}
                        className="bg-green-600 hover:bg-green-700 text-white px-3 py-2 rounded-lg text-sm transition-colors"
                      >
                        Add Standard
                      </button>
                      <button
                        onClick={() => createBike('E_BIKE', station.id)}
                        className="bg-purple-600 hover:bg-purple-700 text-white px-3 py-2 rounded-lg text-sm transition-colors"
                      >
                        Add E-Bike
                      </button>
                    </div>
                    <button
                      onClick={() => toggleStationStatus(station.id)}
                      className={`w-full ${
                        station.status === 'ACTIVE'
                          ? 'bg-orange-600 hover:bg-orange-700'
                          : 'bg-emerald-600 hover:bg-emerald-700'
                      } text-white px-3 py-2 rounded-lg text-sm transition-colors`}
                    >
                      {station.status === 'ACTIVE' ? 'Mark Out of Service' : 'Mark Active'}
                    </button>
                  </>
                )}
              </div>
            </motion.div>
          ))}
        </div>

        {/* Bike List */}
        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-lg overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between">
            <h2 className="text-xl font-semibold text-gray-900 dark:text-white">
              All Bikes ({displayableBikes.length})
            </h2>
            <div className="flex items-center gap-4">
              <div className="text-sm text-gray-500 dark:text-gray-400">
                Showing bikes at stations and active rides
              </div>
              <button
                onClick={loadData}
                className="text-sm text-blue-600 dark:text-blue-400 hover:underline"
              >
                Refresh
              </button>
            </div>
          </div>
          
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
              <thead className="bg-gray-50 dark:bg-gray-700">
                <tr>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">
                    Bike ID
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">
                    Type
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">
                    Status
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">
                    Station
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody className="bg-white dark:bg-gray-800 divide-y divide-gray-200 dark:divide-gray-700">
                {displayableBikes.length === 0 ? (
                  <tr>
                    <td colSpan="5" className="px-6 py-8 text-center text-gray-500 dark:text-gray-400">
                      No bikes to display
                    </td>
                  </tr>
                ) : (
                  displayableBikes.map((bike) => {
                    const isUserBike = bike.status === 'IN_USE' && 
                                      bike.currentUserId && 
                                      Number(bike.currentUserId) === Number(user?.id);
                    return (
                      <tr 
                        key={`bike-${bike.id}`}
                        className={`hover:bg-gray-50 dark:hover:bg-gray-700 ${
                          isUserBike ? 'bg-blue-50 dark:bg-blue-900/20' : ''
                        }`}
                      >
                        <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900 dark:text-white">
                          {bike.id.substring(0, 8)}...
                          {isUserBike && (
                            <span className="ml-2 text-xs text-blue-600 dark:text-blue-400">(Your bike)</span>
                          )}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500 dark:text-gray-400">
                          {bike.type}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap">
                          <span className={`px-2 py-1 rounded-full text-xs font-medium ${
                            bike.status === 'AVAILABLE' 
                              ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200'
                              : bike.status === 'RESERVED'
                              ? 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200'
                              : bike.status === 'IN_USE'
                              ? 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200'
                              : 'bg-gray-100 text-gray-800 dark:bg-gray-900 dark:text-gray-200'
                          }`}>
                            {bike.status}
                          </span>
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500 dark:text-gray-400">
                          {bike.stationId 
                            ? (uniqueStations.find(s => s.id === bike.stationId)?.name || `Station ${bike.stationId}`)
                            : (bike.status === 'IN_USE' ? 'In Use (No Station)' : 'N/A')
                          }
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm space-x-2">
                          {bike.status === 'AVAILABLE' && (
                            <button
                              onClick={() => checkoutBike(bike.id)}
                              className="bg-blue-600 hover:bg-blue-700 text-white px-3 py-1 rounded text-xs transition-colors"
                            >
                              Checkout
                            </button>
                          )}
                          {bike.status === 'RESERVED' && (
                            <button
                              onClick={() => checkoutBike(bike.id)}
                              className="bg-yellow-600 hover:bg-yellow-700 text-white px-3 py-1 rounded text-xs transition-colors"
                            >
                              Checkout Reserved
                            </button>
                          )}
                          {bike.status === 'IN_USE' && isUserBike && (
                            <button
                              onClick={() => openReturnModal(bike.id)}
                              className="bg-green-600 hover:bg-green-700 text-white px-3 py-1 rounded text-xs transition-colors"
                            >
                              Return
                            </button>
                          )}
                          {bike.status === 'IN_USE' && !isUserBike && (
                            <span className="text-xs text-gray-400 dark:text-gray-500">In use by another user</span>
                          )}
                          {user?.role === 'OPERATOR' && bike.status === 'AVAILABLE' && (
                            <button
                              onClick={() => {
                                setMoveData({ ...moveData, bikeId: bike.id });
                                setShowMoveModal(true);
                              }}
                              className="bg-purple-600 hover:bg-purple-700 text-white px-3 py-1 rounded text-xs transition-colors"
                            >
                              Move
                            </button>
                          )}
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </div>

        {/* Reserve Modal */}
        <ReserveBikeModal
          isOpen={showReserveModal}
          onClose={() => {
            setShowReserveModal(false);
            setReservationData({ stationId: '', userId: user?.id || '', expiresAfterMinutes: 15 });
            setError('');
          }}
          onReserve={reserveBike}
          stations={uniqueStations}
          bikes={uniqueBikes}
          reservationData={reservationData}
          onDataChange={setReservationData}
          error={error}
        />

        {/* Move Modal */}
        <MoveBikeModal
          isOpen={showMoveModal}
          onClose={() => {
            setShowMoveModal(false);
            setMoveData({ bikeId: '', newStationId: '', operatorId: user?.id || '' });
            setError('');
          }}
          onMove={moveBike}
          stations={uniqueStations}
          selectedBike={displayableBikes.find(b => b.id === moveData.bikeId)}
          destinationStationId={moveData.newStationId}
          onDestinationChange={(stationId) => setMoveData({ ...moveData, newStationId: stationId })}
          sourceStationId={displayableBikes.find(b => b.id === moveData.bikeId)?.stationId}
          error={error}
        />

        {/* Return Bike Modal */}
        <ReturnBikeModal
          isOpen={showReturnModal}
          onClose={() => {
            setShowReturnModal(false);
            setBikeToReturn(null);
            setReturnStationId('');
            setError('');
          }}
          onReturn={returnBike}
          stations={uniqueStations}
          selectedStationId={returnStationId}
          onStationChange={setReturnStationId}
          error={error}
        />
      </div>
    </motion.div>
  );
};

export default BikeManagement;
