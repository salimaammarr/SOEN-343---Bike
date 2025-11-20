import { useState } from 'react';
import { motion } from 'framer-motion';

const StationDetailsPanel = ({
  station,
  bikes, // Bikes at this station
  allBikes, // All bikes in the system
  onClose,
  onReturn,
  onReserve,
  onCheckout,
  onMove,
  onMarkMaintenance,
  onToggleStationStatus,
  stations,
  userRole
}) => {
  const [showMoveModal, setShowMoveModal] = useState(false);
  const [selectedBike, setSelectedBike] = useState(null);
  const [destinationStationId, setDestinationStationId] = useState('');

  const availableBikes = bikes.filter(b => b.status === 'AVAILABLE');
  const reservedBikes = bikes.filter(b => b.status === 'RESERVED');
  const inUseBikes = bikes.filter(b => b.status === 'IN_USE');
  const maintenanceBikes = bikes.filter(b => b.status === 'MAINTENANCE');
  
  const freeDocks = station.capacity - station.currentBikeCount;
  const occupancyPercentage = (station.currentBikeCount / station.capacity) * 100;

  const handleMoveClick = (bike) => {
    setSelectedBike(bike);
    setShowMoveModal(true);
  };

  const handleMoveConfirm = () => {
    if (selectedBike && destinationStationId) {
      onMove(selectedBike.id, station.id, parseInt(destinationStationId));
      setShowMoveModal(false);
      setSelectedBike(null);
      setDestinationStationId('');
    }
  };

  // Create a grid representation of docks
  const renderDockGrid = () => {
    const docks = [];
    
    // Add occupied docks (bikes)
    bikes.forEach((bike, index) => {
      docks.push({
        id: `dock-${index}`,
        occupied: true,
        bike: bike
      });
    });
    
    // Add empty docks
    for (let i = bikes.length; i < station.capacity; i++) {
      docks.push({
        id: `dock-${i}`,
        occupied: false,
        bike: null
      });
    }

    return (
      <div className="grid grid-cols-5 gap-2 mb-6">
        {docks.map(dock => (
          <motion.div
            key={dock.id}
            whileHover={{ scale: 1.05 }}
            className={`
              h-16 rounded-lg flex items-center justify-center font-bold text-sm cursor-pointer
              ${dock.occupied 
                ? 'bg-gray-800 dark:bg-gray-700 text-white border-2 border-gray-600' 
                : 'bg-gray-200 dark:bg-gray-600 text-gray-500 border-2 border-gray-300 dark:border-gray-500'
              }
            `}
            title={dock.occupied ? `${dock.bike.type} - ${dock.bike.status}` : 'Empty dock'}
          >
          {dock.occupied && dock.bike.type === 'E_BIKE' && (
            <span className="text-yellow-400 font-bold text-lg">E</span>
          )}
          {dock.occupied && dock.bike.type === 'STANDARD' && (
            <span className="text-blue-400 font-bold text-lg">S</span>
          )}
          </motion.div>
        ))}
      </div>
    );
  };

  return (
    <>
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        className="fixed inset-0 bg-black bg-opacity-50 z-50 flex items-center justify-center p-4"
        onClick={onClose}
      >
        <motion.div
          initial={{ scale: 0.9, y: 50 }}
          animate={{ scale: 1, y: 0 }}
          exit={{ scale: 0.9, y: 50 }}
          className="bg-white dark:bg-gray-800 rounded-lg shadow-2xl max-w-4xl w-full max-h-[90vh] overflow-y-auto"
          onClick={(e) => e.stopPropagation()}
        >
          {/* Header */}
          <div className="sticky top-0 bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 p-6 flex items-center justify-between">
            <div>
              <h2 className="text-2xl font-bold text-gray-900 dark:text-white mb-1">
                {station.name}
              </h2>
              <p className="text-gray-600 dark:text-gray-400">{station.address}</p>
            </div>
            <div className="flex items-center gap-3">
              <span className={`px-3 py-1 rounded-full text-sm font-medium ${
                station.status === 'ACTIVE'
                  ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200'
                  : 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200'
              }`}>
                {station.status}
              </span>
              <button
                onClick={onClose}
                className="p-2 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-full transition-colors"
              >
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
          </div>

          {/* Content */}
          <div className="p-6">
            {/* Station Stats */}
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
              <div className="bg-gray-50 dark:bg-gray-700 rounded-lg p-4">
                <div className="text-sm text-gray-600 dark:text-gray-400 mb-1">Capacity</div>
                <div className="text-2xl font-bold">{station.capacity}</div>
              </div>
              <div className="bg-gray-50 dark:bg-gray-700 rounded-lg p-4">
                <div className="text-sm text-gray-600 dark:text-gray-400 mb-1">Current Bikes</div>
                <div className="text-2xl font-bold text-blue-600 dark:text-blue-400">
                  {station.currentBikeCount}
                </div>
              </div>
              <div className="bg-gray-50 dark:bg-gray-700 rounded-lg p-4">
                <div className="text-sm text-gray-600 dark:text-gray-400 mb-1">Free Docks</div>
                <div className="text-2xl font-bold text-green-600 dark:text-green-400">
                  {freeDocks}
                </div>
              </div>
              <div className="bg-gray-50 dark:bg-gray-700 rounded-lg p-4">
                <div className="text-sm text-gray-600 dark:text-gray-400 mb-1">Occupancy</div>
                <div className="text-2xl font-bold text-purple-600 dark:text-purple-400">
                  {occupancyPercentage.toFixed(0)}%
                </div>
              </div>
            </div>

            {/* Dock Grid */}
            <div className="mb-6">
              <h3 className="text-lg font-semibold mb-3">Dock Overview</h3>
              {renderDockGrid()}
              <div className="flex items-center gap-4 text-sm text-gray-600 dark:text-gray-400">
                <div className="flex items-center gap-2">
                  <div className="w-5 h-5 bg-blue-400 rounded flex items-center justify-center text-white font-bold text-xs">S</div>
                  <span>Standard Bike</span>
                </div>
                <div className="flex items-center gap-2">
                  <div className="w-5 h-5 bg-yellow-400 rounded flex items-center justify-center text-white font-bold text-xs">E</div>
                  <span>E-Bike</span>
                </div>
                <div className="flex items-center gap-2">
                  <div className="w-4 h-4 bg-gray-200 dark:bg-gray-600 rounded border-2 border-gray-300 dark:border-gray-500"></div>
                  <span>Empty Dock</span>
                </div>
              </div>
            </div>

            {/* Bike Details */}
            <div className="mb-6">
              <h3 className="text-lg font-semibold mb-3">Bike Details</h3>
              <div className="space-y-2">
                <div className="flex justify-between p-2 bg-green-50 dark:bg-green-900/20 rounded">
                  <span className="font-medium">Available:</span>
                  <span className="text-green-600 dark:text-green-400 font-bold">
                    {availableBikes.length} bikes
                  </span>
                </div>
                <div className="flex justify-between p-2 bg-yellow-50 dark:bg-yellow-900/20 rounded">
                  <span className="font-medium">Reserved:</span>
                  <span className="text-yellow-600 dark:text-yellow-400 font-bold">
                    {reservedBikes.length} bikes
                  </span>
                </div>
                <div className="flex justify-between p-2 bg-blue-50 dark:bg-blue-900/20 rounded">
                  <span className="font-medium">In Use:</span>
                  <span className="text-blue-600 dark:text-blue-400 font-bold">
                    {inUseBikes.length} bikes
                  </span>
                </div>
                {maintenanceBikes.length > 0 && (
                  <div className="flex justify-between p-2 bg-red-50 dark:bg-red-900/20 rounded">
                    <span className="font-medium">Maintenance:</span>
                    <span className="text-red-600 dark:text-red-400 font-bold">
                      {maintenanceBikes.length} bikes
                    </span>
                  </div>
                )}
              </div>
            </div>

            {/* Actions */}
            <div className="border-t border-gray-200 dark:border-gray-700 pt-6">
              <h3 className="text-lg font-semibold mb-3">Actions</h3>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                {/* Rider Actions */}
                {userRole === 'RIDER' && (
                  <>
                    <button
                      onClick={() => onReserve(station.id)}
                      disabled={availableBikes.length === 0 || station.status === 'OUT_OF_SERVICE'}
                      className="btn-primary disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      Reserve Bike
                    </button>
                    <button
                      onClick={() => {
                        // Checkout an available bike or a reserved bike at this station
                        const bikeToCheckout = bikes.find(b => b.status === 'RESERVED' || b.status === 'AVAILABLE');
                        if (bikeToCheckout) {
                          onCheckout(bikeToCheckout.id);
                        } else {
                          alert('No bikes available to checkout at this station.');
                        }
                      }}
                      disabled={(availableBikes.length === 0 && reservedBikes.length === 0) || station.status === 'OUT_OF_SERVICE'}
                      className="bg-blue-600 hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed text-white px-4 py-2 rounded-lg font-medium transition-colors"
                    >
                      Checkout Bike
                    </button>
                    <button
                      onClick={() => {
                        // Find the user's bike that is currently IN_USE from all bikes
                        const userBike = allBikes?.find(b => b.status === 'IN_USE') || allBikes?.find(b => b.status === 'RESERVED');
                        if (userBike) {
                          onReturn(userBike.id, station.id);
                        } else {
                          alert('No active bike to return. Please reserve or checkout a bike first.');
                        }
                      }}
                      disabled={freeDocks === 0 || station.status === 'OUT_OF_SERVICE'}
                      className="btn-secondary disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      Return Bike Here
                    </button>
                  </>
                )}

                {/* Operator Actions */}
                {userRole === 'OPERATOR' && (
                  <>
                    <button
                      onClick={() => onToggleStationStatus(station.id)}
                      className="bg-orange-600 hover:bg-orange-700 text-white px-4 py-2 rounded-lg font-medium transition-colors"
                    >
                      {station.status === 'ACTIVE' ? 'Mark Out of Service' : 'Mark Active'}
                    </button>
                    <button
                      onClick={() => {
                        const bike = availableBikes[0];
                        if (bike) handleMoveClick(bike);
                      }}
                      disabled={availableBikes.length === 0}
                      className="bg-purple-600 hover:bg-purple-700 disabled:opacity-50 disabled:cursor-not-allowed text-white px-4 py-2 rounded-lg font-medium transition-colors"
                    >
                      Move Bike
                    </button>
                  </>
                )}
              </div>
            </div>

            {/* Detailed Bike List */}
            {userRole === 'OPERATOR' && bikes.length > 0 && (
              <div className="border-t border-gray-200 dark:border-gray-700 pt-6 mt-6">
                <h3 className="text-lg font-semibold mb-3">All Bikes at Station</h3>
                <div className="overflow-x-auto">
                  <table className="min-w-full text-sm">
                    <thead className="bg-gray-50 dark:bg-gray-700">
                      <tr>
                        <th className="px-4 py-2 text-left">ID</th>
                        <th className="px-4 py-2 text-left">Type</th>
                        <th className="px-4 py-2 text-left">Status</th>
                        <th className="px-4 py-2 text-left">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-200 dark:divide-gray-700">
                      {bikes.map(bike => (
                        <tr key={bike.id}>
                          <td className="px-4 py-2 font-mono text-xs">
                            {bike.id.substring(0, 8)}...
                          </td>
                          <td className="px-4 py-2">
                            <span className="flex items-center gap-2">
                              <span className={`w-5 h-5 rounded flex items-center justify-center text-white font-bold text-xs ${
                                bike.type === 'E_BIKE' ? 'bg-yellow-400' : 'bg-blue-400'
                              }`}>
                                {bike.type === 'E_BIKE' ? 'E' : 'S'}
                              </span>
                              {bike.type === 'E_BIKE' ? 'E-Bike' : 'Standard'}
                            </span>
                          </td>
                          <td className="px-4 py-2">
                            <span className={`px-2 py-1 rounded text-xs font-medium ${
                              bike.status === 'AVAILABLE' ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200' :
                              bike.status === 'RESERVED' ? 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200' :
                              bike.status === 'IN_USE' ? 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200' :
                              'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-200'
                            }`}>
                              {bike.status}
                            </span>
                          </td>
                          <td className="px-4 py-2 space-x-2">
                            {bike.status === 'AVAILABLE' && (
                              <>
                                <button
                                  onClick={() => handleMoveClick(bike)}
                                  className="text-purple-600 hover:text-purple-800 dark:text-purple-400 dark:hover:text-purple-300"
                                >
                                  Move
                                </button>
                                <button
                                  onClick={() => onMarkMaintenance(bike.id)}
                                  className="text-orange-600 hover:text-orange-800 dark:text-orange-400 dark:hover:text-orange-300"
                                >
                                  Maintenance
                                </button>
                              </>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        </motion.div>
      </motion.div>

      {/* Move Bike Modal */}
      {showMoveModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 z-[60] flex items-center justify-center p-4">
          <motion.div
            initial={{ scale: 0.9, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            className="bg-white dark:bg-gray-800 rounded-lg p-6 max-w-md w-full"
          >
            <h3 className="text-lg font-semibold mb-4">Move Bike</h3>
            <div className="mb-4">
              <label className="block text-sm font-medium mb-2">
                Bike: {selectedBike?.id.substring(0, 8)}... ({selectedBike?.type})
              </label>
              <label className="block text-sm font-medium mb-2">Destination Station</label>
              <select
                value={destinationStationId}
                onChange={(e) => setDestinationStationId(e.target.value)}
                className="w-full border border-gray-300 dark:border-gray-600 rounded-lg px-3 py-2 bg-white dark:bg-gray-700"
              >
                <option value="">Select destination</option>
                {stations
                  .filter(s => s.id !== station.id)
                  .map(s => (
                    <option key={s.id} value={s.id}>
                      {s.name} ({s.capacity - s.currentBikeCount} free docks)
                    </option>
                  ))}
              </select>
            </div>
            <div className="flex justify-end space-x-3">
              <button
                onClick={() => {
                  setShowMoveModal(false);
                  setSelectedBike(null);
                  setDestinationStationId('');
                }}
                className="px-4 py-2 text-gray-600 dark:text-gray-400 hover:text-gray-800 dark:hover:text-gray-200"
              >
                Cancel
              </button>
              <button
                onClick={handleMoveConfirm}
                disabled={!destinationStationId}
                className="btn-primary disabled:opacity-50 disabled:cursor-not-allowed"
              >
                Move Bike
              </button>
            </div>
          </motion.div>
        </div>
      )}
    </>
  );
};

export default StationDetailsPanel;

