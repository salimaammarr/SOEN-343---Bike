import { motion } from 'framer-motion';
import { getFilteredStations, getAvailableBikesCount } from '../utils/bikeUtils';

/**
 * Modal component for reserving a bike
 * Modular and reusable component
 */
const ReserveBikeModal = ({
  isOpen,
  onClose,
  onReserve,
  stations = [],
  bikes = [],
  reservationData,
  onDataChange,
  error = ''
}) => {
  if (!isOpen) return null;

  // Get stations with available bikes, deduplicated
  const availableStations = getFilteredStations(stations, {
    status: 'ACTIVE',
    hasAvailableBikes: true,
    bikes
  });

  const handleSubmit = (e) => {
    e.preventDefault();
    if (reservationData.stationId) {
      onReserve();
    }
  };

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <motion.div
        initial={{ scale: 0.9, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        className="bg-white dark:bg-gray-800 rounded-lg p-6 w-full max-w-md mx-4"
      >
        <h3 className="text-lg font-semibold mb-4 text-gray-900 dark:text-white">
          Reserve Bike
        </h3>
        
        {error && (
          <div className="mb-4 p-3 bg-red-100 dark:bg-red-900 border border-red-400 dark:border-red-700 text-red-700 dark:text-red-200 rounded-lg text-sm">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Station
              </label>
              <select
                value={reservationData.stationId || ''}
                onChange={(e) => onDataChange({ ...reservationData, stationId: e.target.value })}
                className="w-full border border-gray-300 dark:border-gray-600 rounded-lg px-3 py-2 bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                required
              >
                <option value="">Select Station</option>
                {availableStations.map(station => {
                  const availableCount = getAvailableBikesCount(bikes, station.id);
                  return (
                    <option key={station.id} value={station.id}>
                      {station.name} ({availableCount} available)
                    </option>
                  );
                })}
              </select>
              {availableStations.length === 0 && (
                <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                  No stations with available bikes
                </p>
              )}
            </div>
            
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Reservation Duration (minutes)
              </label>
              <input
                type="number"
                value={reservationData.expiresAfterMinutes || 15}
                onChange={(e) => onDataChange({ 
                  ...reservationData, 
                  expiresAfterMinutes: parseInt(e.target.value) || 15 
                })}
                className="w-full border border-gray-300 dark:border-gray-600 rounded-lg px-3 py-2 bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                min="1"
                max="60"
                required
              />
              <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                Bike will be reserved for {reservationData.expiresAfterMinutes || 15} minutes
              </p>
            </div>
          </div>

          <div className="flex justify-end space-x-3 mt-6">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-gray-600 dark:text-gray-400 hover:text-gray-800 dark:hover:text-gray-200 transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={!reservationData.stationId || availableStations.length === 0}
              className="px-4 py-2 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed text-white rounded-lg transition-colors"
            >
              Reserve
            </button>
          </div>
        </form>
      </motion.div>
    </div>
  );
};

export default ReserveBikeModal;

