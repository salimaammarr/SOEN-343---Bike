import { motion } from 'framer-motion';
import { getFilteredStations } from '../utils/bikeUtils';

/**
 * Modal component for moving a bike (operator only)
 * Modular and reusable component
 */
const MoveBikeModal = ({
  isOpen,
  onClose,
  onMove,
  stations = [],
  selectedBike = null,
  destinationStationId,
  onDestinationChange,
  sourceStationId = null,
  error = ''
}) => {
  if (!isOpen) return null;

  // Get active stations (excluding source station), deduplicated
  const destinationStations = getFilteredStations(stations, {
    status: 'ACTIVE',
    minCapacity: 1 // At least 1 free dock
  }).filter(s => s.id !== sourceStationId); // Exclude source station

  const handleSubmit = (e) => {
    e.preventDefault();
    if (destinationStationId) {
      onMove();
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
          Move Bike
        </h3>
        
        {error && (
          <div className="mb-4 p-3 bg-red-100 dark:bg-red-900 border border-red-400 dark:border-red-700 text-red-700 dark:text-red-200 rounded-lg text-sm">
            {error}
          </div>
        )}

        {selectedBike && (
          <div className="mb-4 p-3 bg-gray-100 dark:bg-gray-700 rounded-lg">
            <p className="text-sm text-gray-700 dark:text-gray-300">
              <span className="font-medium">Bike:</span> {selectedBike.id?.substring(0, 8)}... ({selectedBike.type})
            </p>
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Destination Station
              </label>
              <select
                value={destinationStationId || ''}
                onChange={(e) => onDestinationChange(e.target.value)}
                className="w-full border border-gray-300 dark:border-gray-600 rounded-lg px-3 py-2 bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-purple-500 focus:border-transparent"
                required
              >
                <option value="">Select Destination</option>
                {destinationStations.map(station => {
                  const freeDocks = station.capacity - station.currentBikeCount;
                  return (
                    <option key={station.id} value={station.id}>
                      {station.name} ({freeDocks} free dock{freeDocks !== 1 ? 's' : ''})
                    </option>
                  );
                })}
              </select>
              {destinationStations.length === 0 && (
                <p className="text-xs text-red-500 dark:text-red-400 mt-1">
                  No available destination stations
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
              disabled={!destinationStationId || destinationStations.length === 0}
              className="px-4 py-2 bg-purple-600 hover:bg-purple-700 disabled:bg-gray-400 disabled:cursor-not-allowed text-white rounded-lg transition-colors"
            >
              Move Bike
            </button>
          </div>
        </form>
      </motion.div>
    </div>
  );
};

export default MoveBikeModal;

