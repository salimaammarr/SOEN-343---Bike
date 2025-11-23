import { motion } from 'framer-motion';
import { getFilteredStations } from '../utils/bikeUtils';

/**
 * Modal component for returning a bike
 * Modular and reusable component
 */
const ReturnBikeModal = ({
  isOpen,
  onClose,
  onReturn,
  stations = [],
  selectedStationId,
  onStationChange,
  error = ''
}) => {
  if (!isOpen) return null;

  // Get active stations with capacity, deduplicated
  const returnableStations = getFilteredStations(stations, {
    status: 'ACTIVE',
    minCapacity: 1 // At least 1 free dock
  });

  const handleSubmit = (e) => {
    e.preventDefault();
    if (selectedStationId) {
      onReturn();
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
          Return Bike
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
                Select Return Station
              </label>
              <select
                value={selectedStationId || ''}
                onChange={(e) => onStationChange(e.target.value)}
                className="w-full border border-gray-300 dark:border-gray-600 rounded-lg px-3 py-2 bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-green-500 focus:border-transparent"
                required
              >
                <option value="">Select Station</option>
                {returnableStations.map(station => {
                  const freeDocks = station.capacity - station.currentBikeCount;
                  const isFull = freeDocks === 0;
                  return (
                    <option 
                      key={station.id} 
                      value={station.id}
                      disabled={isFull}
                    >
                      {station.name} ({freeDocks} free dock{freeDocks !== 1 ? 's' : ''}{isFull ? ' - FULL' : ''})
                    </option>
                  );
                })}
              </select>
              <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                Select the station where you want to return the bike
              </p>
              {returnableStations.length === 0 && (
                <p className="text-xs text-red-500 dark:text-red-400 mt-1">
                  No stations available for return (all stations are full)
                </p>
              )}
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
              disabled={!selectedStationId || returnableStations.length === 0}
              className="px-4 py-2 bg-green-600 hover:bg-green-700 disabled:bg-gray-400 disabled:cursor-not-allowed text-white rounded-lg transition-colors"
            >
              Return Bike
            </button>
          </div>
        </form>
      </motion.div>
    </div>
  );
};

export default ReturnBikeModal;

