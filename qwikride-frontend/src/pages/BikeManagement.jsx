import { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { useAuth } from '../hooks/useAuth';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';

const BikeManagement = () => {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState('');
  const [bikes, setBikes] = useState([]);
  const [stations, setStations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedStation, setSelectedStation] = useState(null);
  const [showReserveModal, setShowReserveModal] = useState(false);
  const [showMoveModal, setShowMoveModal] = useState(false);
  const [reservationData, setReservationData] = useState({ stationId: '', userId: '', expiresAfterMinutes: 15 });
  const [moveData, setMoveData] = useState({ bikeId: '', newStationId: '', operatorId: '' });

  // Update userId in forms when user changes
  useEffect(() => {
    if (user?.id) {
      setReservationData(prev => ({ ...prev, userId: user.id }));
      setMoveData(prev => ({ ...prev, operatorId: user.id }));
    }
  }, [user?.id]);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      setLoading(true);
      const [bikesResponse, stationsResponse] = await Promise.all([
        api.get('/bikes'),
        api.get('/stations')
      ]);
      setBikes(bikesResponse.data);
      setStations(stationsResponse.data);
      setError('');
    } catch {
      setError('Failed to load data. Please refresh the page.');
    } finally {
      setLoading(false);
    }
  };

  const reserveBike = async () => {
    try {
      // Validate user is logged in
      if (!user?.id) {
        setError('Please log in to reserve a bike.');
        return;
      }

      // Validate station is selected
      if (!reservationData.stationId) {
        setError('Please select a station.');
        return;
      }

      // Ensure data types are correct - convert strings to numbers
      const payload = {
        stationId: Number(reservationData.stationId),
        userId: Number(user.id),
        expiresAfterMinutes: Number(reservationData.expiresAfterMinutes)
      };

      console.log('Reserving bike with payload:', payload); // Debug log
      console.log('Payload details:', JSON.stringify(payload, null, 2)); // Show exact values

      await api.post('/bikes/reserve', payload);
      setShowReserveModal(false);
      setReservationData({ stationId: '', userId: user.id, expiresAfterMinutes: 15 });
      setError('');
      loadData();
    } catch (err) {
      console.error('Reserve bike error:', err); // Debug log
      console.error('Error response:', err.response?.data); // Show backend error
      console.error('Error status:', err.response?.status); // Show status code
      
      const errorMessage = err.response?.data?.message || err.response?.data?.error || 'Failed to reserve bike. Please try again.';
      setError(`Error: ${errorMessage}`);
    }
  };

  const checkoutBike = async (bikeId) => {
    try {
      // Validate user is logged in
      if (!user?.id) {
        setError('Please log in to checkout a bike.');
        return;
      }

      // bikeId is a UUID string, userId is a Long number
      const payload = { 
        bikeId: String(bikeId), // Keep as string (UUID)
        userId: Number(user.id) 
      };
      console.log('Checking out bike with payload:', payload);
      console.log('Payload JSON:', JSON.stringify(payload, null, 2));

      await api.post('/bikes/checkout', payload);
      setError('');
      loadData();
    } catch (err) {
      console.error('Checkout bike error:', err);
      console.error('Error response:', err.response?.data);
      console.error('Error status:', err.response?.status);
      setError(err.response?.data?.message || err.response?.data?.error || 'Failed to checkout bike. Please try again.');
    }
  };

  const returnBike = async (bikeId) => {
    try {
      // Validate user is logged in
      if (!user?.id) {
        setError('Please log in to return a bike.');
        return;
      }

      const durationMinutes = Math.random() * 60 + 10; // Simulate trip duration
      const distanceKm = Math.random() * 20 + 1; // Simulate trip distance
      
      // bikeId is UUID string, IDs are numbers
      const payload = {
        bikeId: String(bikeId), // Keep as string (UUID)
        returnStationId: Number(selectedStation?.id || stations[0]?.id),
        userId: Number(user.id),
        durationMinutes: Number(durationMinutes.toFixed(2)),
        distanceKm: Number(distanceKm.toFixed(2))
      };
      
      console.log('Returning bike with payload:', JSON.stringify(payload, null, 2));
      await api.post('/bikes/return', payload);
      setError('');
      loadData();
    } catch (err) {
      console.error('Return bike error:', err);
      console.error('Error response:', err.response?.data);
      setError(err.response?.data?.message || err.response?.data?.error || 'Failed to return bike. Please try again.');
    }
  };

  const moveBike = async () => {
    try {
      // Validate user is logged in
      if (!user?.id) {
        setError('Please log in to move bikes.');
        return;
      }

      // Validate station is selected
      if (!moveData.newStationId) {
        setError('Please select a destination station.');
        return;
      }

      // bikeId is UUID string, IDs are numbers
      const payload = {
        bikeId: String(moveData.bikeId), // Keep as string (UUID)
        newStationId: Number(moveData.newStationId),
        operatorId: Number(user.id)
      };

      console.log('Moving bike with payload:', JSON.stringify(payload, null, 2));
      await api.post('/bikes/move', payload);
      setShowMoveModal(false);
      setMoveData({ bikeId: '', newStationId: '', operatorId: user.id });
      setError('');
      loadData();
    } catch (err) {
      console.error('Move bike error:', err);
      console.error('Error response:', err.response?.data);
      setError(err.response?.data?.message || err.response?.data?.error || 'Failed to move bike. Please try again.');
    }
  };

  const createBike = async (type, stationId) => {
    try {
      const payload = { type, stationId: Number(stationId) };
      console.log('Creating bike with payload:', payload);
      await api.post('/bikes/create', payload);
      setError('');
      loadData();
    } catch (err) {
      console.error('Create bike error:', err);
      console.error('Error response:', err.response?.data);
      setError(err.response?.data?.message || err.response?.data?.error || 'Failed to create bike. Please try again.');
    }
  };

  const toggleStationStatus = async (stationId) => {
    try {
      const station = stations.find(s => s.id === stationId);
      if (!station) return;
      
      const newStatus = station.status === 'ACTIVE' ? 'OUT_OF_SERVICE' : 'ACTIVE';
      await api.patch(`/operator/stations/${stationId}/status`, { status: newStatus });
      
      setError('');
      loadData();
    } catch (err) {
      console.error('Toggle station status error:', err);
      console.error('Error response:', err.response?.data);
      setError(err.response?.data?.message || err.response?.data?.error || 'Failed to update station status. Please try again.');
    }
  };

  const getAvailableBikesByStation = (stationId) => {
    return bikes.filter(bike => bike.stationId === stationId && bike.status === 'AVAILABLE');
  };

  const getReservedBikesByStation = (stationId) => {
    return bikes.filter(bike => bike.stationId === stationId && bike.status === 'RESERVED');
  };

  const getInUseBikesByStation = (stationId) => {
    return bikes.filter(bike => bike.stationId === stationId && bike.status === 'IN_USE');
  };

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
        </div>

        {/* Station Overview */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mb-8">
          {stations.map((station) => (
            <motion.div
              key={station.id}
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
                    setSelectedStation(station);
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
          <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-700">
            <h2 className="text-xl font-semibold text-gray-900 dark:text-white">
              All Bikes ({bikes.length})
            </h2>
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
                {bikes.map((bike) => (
                  <tr key={bike.id} className="hover:bg-gray-50 dark:hover:bg-gray-700">
                    <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900 dark:text-white">
                      {bike.id.substring(0, 8)}...
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
                      {stations.find(s => s.id === bike.stationId)?.name || 'Unknown'}
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
                      {bike.status === 'IN_USE' && (
                        <button
                          onClick={() => returnBike(bike.id)}
                          className="bg-green-600 hover:bg-green-700 text-white px-3 py-1 rounded text-xs transition-colors"
                        >
                          Return
                        </button>
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
                ))}
              </tbody>
            </table>
          </div>
        </div>

        {/* Reserve Modal */}
        {showReserveModal && (
          <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
            <motion.div
              initial={{ scale: 0.9, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              className="bg-white dark:bg-gray-800 rounded-lg p-6 w-full max-w-md mx-4"
            >
              <h3 className="text-lg font-semibold mb-4 text-gray-900 dark:text-white">
                Reserve Bike
              </h3>
              <div className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    Station
                  </label>
                  <select
                    value={reservationData.stationId}
                    onChange={(e) => setReservationData({ ...reservationData, stationId: e.target.value })}
                    className="w-full border border-gray-300 dark:border-gray-600 rounded-lg px-3 py-2 bg-white dark:bg-gray-700 text-gray-900 dark:text-white"
                  >
                    <option value="">Select Station</option>
                    {stations.map(station => (
                      <option key={station.id} value={station.id}>
                        {station.name} ({getAvailableBikesByStation(station.id).length} available)
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    Expires After (minutes)
                  </label>
                  <input
                    type="number"
                    value={reservationData.expiresAfterMinutes}
                    onChange={(e) => setReservationData({ ...reservationData, expiresAfterMinutes: parseInt(e.target.value) })}
                    className="w-full border border-gray-300 dark:border-gray-600 rounded-lg px-3 py-2 bg-white dark:bg-gray-700 text-gray-900 dark:text-white"
                    min="1"
                    max="60"
                  />
                </div>
              </div>
              <div className="flex justify-end space-x-3 mt-6">
                <button
                  onClick={() => setShowReserveModal(false)}
                  className="px-4 py-2 text-gray-600 dark:text-gray-400 hover:text-gray-800 dark:hover:text-gray-200 transition-colors"
                >
                  Cancel
                </button>
                <button
                  onClick={reserveBike}
                  className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition-colors"
                >
                  Reserve
                </button>
              </div>
            </motion.div>
          </div>
        )}

        {/* Move Modal */}
        {showMoveModal && (
          <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
            <motion.div
              initial={{ scale: 0.9, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              className="bg-white dark:bg-gray-800 rounded-lg p-6 w-full max-w-md mx-4"
            >
              <h3 className="text-lg font-semibold mb-4 text-gray-900 dark:text-white">
                Move Bike
              </h3>
              <div className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    Destination Station
                  </label>
                  <select
                    value={moveData.newStationId}
                    onChange={(e) => setMoveData({ ...moveData, newStationId: e.target.value })}
                    className="w-full border border-gray-300 dark:border-gray-600 rounded-lg px-3 py-2 bg-white dark:bg-gray-700 text-gray-900 dark:text-white"
                  >
                    <option value="">Select Destination</option>
                    {stations.map(station => (
                      <option key={station.id} value={station.id}>
                        {station.name} ({station.currentBikeCount}/{station.capacity})
                      </option>
                    ))}
                  </select>
                </div>
              </div>
              <div className="flex justify-end space-x-3 mt-6">
                <button
                  onClick={() => setShowMoveModal(false)}
                  className="px-4 py-2 text-gray-600 dark:text-gray-400 hover:text-gray-800 dark:hover:text-gray-200 transition-colors"
                >
                  Cancel
                </button>
                <button
                  onClick={moveBike}
                  className="px-4 py-2 bg-purple-600 hover:bg-purple-700 text-white rounded-lg transition-colors"
                >
                  Move Bike
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </div>
    </motion.div>
  );
};

export default BikeManagement;
