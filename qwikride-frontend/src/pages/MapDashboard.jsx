import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { useAuth } from '../hooks/useAuth';
import { MapContainer, TileLayer, Marker, Popup } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import api from '../services/api';
import StationDetailsPanel from '../components/StationDetailsPanel';
import SystemConsole from '../components/SystemConsole';
import MapLegend from '../components/MapLegend';

// Fix for default marker icon
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
});

// Montreal coordinates for stations
const STATION_COORDINATES = {
  'Downtown Central': [45.5017, -73.5673],
  'University Campus': [45.5048, -73.5772],
  'Shopping Mall': [45.4975, -73.5675],
  'Park & Ride': [45.4950, -73.5825],
  'Waterfront': [45.5088, -73.5540]
};

const MONTREAL_CENTER = [45.5017, -73.5673];

// Custom marker icons - minimalistic and clean
const createStationIcon = (color) => {
  return L.divIcon({
    className: 'custom-marker',
    html: `
      <div style="
        background-color: ${color};
        width: 32px;
        height: 32px;
        border-radius: 50% 50% 50% 0;
        border: 3px solid white;
        transform: rotate(-45deg);
        box-shadow: 0 4px 8px rgba(0,0,0,0.3);
      ">
        <div style="
          width: 8px;
          height: 8px;
          background: white;
          border-radius: 50%;
          position: absolute;
          top: 50%;
          left: 50%;
          transform: translate(-50%, -50%);
        "></div>
      </div>
    `,
    iconSize: [32, 32],
    iconAnchor: [12, 32],
    popupAnchor: [0, -32]
  });
};

const getStationColor = (currentBikes, capacity) => {
  if (currentBikes === 0 || currentBikes === capacity) return '#EF4444'; // Red
  const percentage = (currentBikes / capacity) * 100;
  if (percentage < 25 || percentage > 85) return '#F59E0B'; // Yellow
  return '#10B981'; // Green
};

const MapDashboard = () => {
  const { user } = useAuth();
  const [stations, setStations] = useState([]);
  const [bikes, setBikes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedStation, setSelectedStation] = useState(null);
  const [showDetailsPanel, setShowDetailsPanel] = useState(false);
  const [consoleMessages, setConsoleMessages] = useState([]);

  useEffect(() => {
    loadData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const loadData = async () => {
    try {
      setLoading(true);
      const [stationsResponse, bikesResponse] = await Promise.all([
        api.get('/stations'),
        api.get('/bikes')
      ]);
      setStations(stationsResponse.data);
      setBikes(bikesResponse.data);
      addConsoleMessage('System loaded successfully', 'success');
    } catch {
      addConsoleMessage('Failed to load system data', 'error');
    } finally {
      setLoading(false);
    }
  };

  const addConsoleMessage = (message, type = 'info') => {
    const timestamp = new Date().toLocaleTimeString();
    // Use crypto.randomUUID() or a combination of Date.now() and random for unique IDs
    const id = `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
    setConsoleMessages(prev => [...prev, { message, type, timestamp, id }]);
  };

  const handleStationClick = (station) => {
    setSelectedStation(station);
    setShowDetailsPanel(true);
    addConsoleMessage(`Selected station: ${station.name}`, 'info');
  };

  const handleReturn = async (bikeId, stationId) => {
    try {
      // Validate user is logged in
      if (!user?.id) {
        addConsoleMessage('Please log in to return a bike', 'error');
        return;
      }

      const station = stations.find(s => s.id === stationId);
      const freeDocks = station.capacity - station.currentBikeCount;
      
      // DM-06: Validate return conditions
      if (freeDocks === 0 || station.status === 'OUT_OF_SERVICE') {
        const errorMsg = station.status === 'OUT_OF_SERVICE' 
          ? 'Cannot return bike to out-of-service station. Please select another nearby station.'
          : 'Station is full. Please return bike to another nearby station.';
        addConsoleMessage(errorMsg, 'error');
        return;
      }

      const durationMinutes = Math.random() * 60 + 10;
      const distanceKm = Math.random() * 20 + 1;
      
      // bikeId is UUID string, IDs are numbers
      const payload = {
        bikeId: String(bikeId), // Keep as string (UUID)
        returnStationId: Number(stationId),
        userId: Number(user.id),
        durationMinutes: Number(durationMinutes.toFixed(2)),
        distanceKm: Number(distanceKm.toFixed(2))
      };

      console.log('MapDashboard returning bike:', JSON.stringify(payload, null, 2));
      await api.post('/bikes/return', payload);
      await loadData();
      addConsoleMessage('Bike returned successfully', 'success');
    } catch (error) {
      console.error('MapDashboard return error:', error.response?.data);
      const errorMsg = error.response?.data?.message || error.response?.data?.error || 'Failed to return bike';
      addConsoleMessage(errorMsg, 'error');
    }
  };

  const handleReserve = async (stationId) => {
    try {
      // Validate user is logged in
      if (!user?.id) {
        addConsoleMessage('Please log in to reserve a bike', 'error');
        return;
      }

      const station = stations.find(s => s.id === stationId);
      const availableBikes = bikes.filter(b => b.stationId === stationId && b.status === 'AVAILABLE');
      
      // DM-05: Validate checkout conditions
      if (availableBikes.length === 0 || station.status === 'OUT_OF_SERVICE') {
        const errorMsg = station.status === 'OUT_OF_SERVICE'
          ? 'Cannot reserve bike from out-of-service station'
          : 'No bikes available at this station';
        addConsoleMessage(errorMsg, 'error');
        return;
      }

      const payload = {
        stationId: Number(stationId),
        userId: Number(user.id),
        expiresAfterMinutes: 15
      };

      console.log('MapDashboard reserving bike:', payload);
      await api.post('/bikes/reserve', payload);
      await loadData();
      addConsoleMessage('Bike reserved successfully (expires in 15 minutes)', 'success');
    } catch (error) {
      console.error('MapDashboard reserve error:', error.response?.data);
      const errorMsg = error.response?.data?.message || error.response?.data?.error || 'Failed to reserve bike';
      addConsoleMessage(errorMsg, 'error');
    }
  };

  const handleCheckout = async (bikeId) => {
    try {
      // Validate user is logged in
      if (!user?.id) {
        addConsoleMessage('Please log in to checkout a bike', 'error');
        return;
      }

      // bikeId is UUID string, userId is a number
      const payload = {
        bikeId: String(bikeId),
        userId: Number(user.id)
      };

      console.log('MapDashboard checking out bike:', JSON.stringify(payload, null, 2));
      await api.post('/bikes/checkout', payload);
      await loadData();
      addConsoleMessage('Bike checked out successfully - enjoy your ride!', 'success');
    } catch (error) {
      console.error('MapDashboard checkout error:', error.response?.data);
      const errorMsg = error.response?.data?.message || error.response?.data?.error || 'Failed to checkout bike';
      addConsoleMessage(errorMsg, 'error');
    }
  };

  const handleMove = async (bikeId, sourceStationId, destinationStationId) => {
    try {
      // Validate user is logged in
      if (!user?.id) {
        addConsoleMessage('Please log in to move bikes', 'error');
        return;
      }

      const sourceStation = stations.find(s => s.id === sourceStationId);
      const destStation = stations.find(s => s.id === destinationStationId);
      const sourceBikes = bikes.filter(b => b.stationId === sourceStationId && b.status === 'AVAILABLE');
      const destFreeDocks = destStation.capacity - destStation.currentBikeCount;

      // DM-07: Validate move conditions
      if (sourceBikes.length === 0) {
        addConsoleMessage('Source station has no available bikes to move', 'error');
        return;
      }
      if (destFreeDocks === 0) {
        addConsoleMessage('Destination station has no free docks', 'error');
        return;
      }
      if (sourceStationId === destinationStationId) {
        addConsoleMessage('Cannot move bike to the same station', 'error');
        return;
      }

      // bikeId is UUID string, IDs are numbers
      const payload = {
        bikeId: String(bikeId), // Keep as string (UUID)
        newStationId: Number(destinationStationId),
        operatorId: Number(user.id)
      };

      console.log('MapDashboard moving bike:', JSON.stringify(payload, null, 2));
      await api.post('/bikes/move', payload);
      await loadData();
      addConsoleMessage(`Bike moved from ${sourceStation.name} to ${destStation.name}`, 'success');
    } catch (error) {
      console.error('MapDashboard move error:', error.response?.data);
      const errorMsg = error.response?.data?.message || error.response?.data?.error || 'Failed to move bike';
      addConsoleMessage(errorMsg, 'error');
    }
  };

  const handleMarkMaintenance = async () => {
    addConsoleMessage('Bike marked for maintenance (feature pending backend support)', 'warning');
  };

  const handleToggleStationStatus = async (stationId) => {
    try {
      const station = stations.find(s => s.id === stationId);
      const newStatus = station.status === 'ACTIVE' ? 'OUT_OF_SERVICE' : 'ACTIVE';
      await api.patch(`/operator/stations/${stationId}/status`, { status: newStatus });
      await loadData();
      addConsoleMessage(`Station marked as ${newStatus}`, 'success');
    } catch {
      addConsoleMessage('Failed to update station status', 'error');
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-xl text-gray-600 dark:text-gray-400">Loading dashboard...</div>
      </div>
    );
  }

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      className="min-h-[calc(100vh-5rem)] bg-gray-50 dark:bg-gray-900"
    >
      {/* Main Content */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
        <div className="space-y-4">
            {/* Map and Console Layout */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
              {/* Map Container */}
              <div className="lg:col-span-2">
                <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm overflow-hidden border border-gray-200 dark:border-gray-700">
                  {/* DM-09: Handle empty map gracefully */}
                  {stations.length === 0 ? (
                    <div className="h-[600px] flex items-center justify-center text-gray-500 dark:text-gray-400">
                      <div className="text-center">
                        <svg className="w-16 h-16 mx-auto mb-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 20l-5.447-2.724A1 1 0 013 16.382V5.618a1 1 0 011.447-.894L9 7m0 13l6-3m-6 3V7m6 10l4.553 2.276A1 1 0 0021 18.382V7.618a1 1 0 00-.553-.894L15 4m0 13V4m0 0L9 7" />
                        </svg>
                        <p className="text-lg font-medium">No stations available</p>
                        <p className="text-sm mt-2">The map will display when stations are added to the system</p>
                      </div>
                    </div>
                  ) : (
                    <MapContainer
                      center={MONTREAL_CENTER}
                      zoom={13}
                      style={{ height: '600px', width: '100%' }}
                      className="z-0"
                    >
                      <TileLayer
                        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                      />
                      {stations.map(station => {
                        const coords = STATION_COORDINATES[station.name] || MONTREAL_CENTER;
                        const color = getStationColor(station.currentBikeCount, station.capacity);
                        const icon = createStationIcon(color);
                        
                        return (
                          <Marker
                            key={station.id}
                            position={coords}
                            icon={icon}
                            eventHandlers={{
                              click: () => handleStationClick(station)
                            }}
                          >
                            <Popup>
                              <div className="text-sm">
                                <div className="font-bold mb-1">{station.name}</div>
                                <div className="text-gray-600">
                                  {station.currentBikeCount}/{station.capacity} bikes
                                </div>
                              </div>
                            </Popup>
                          </Marker>
                        );
                      })}
                    </MapContainer>
                  )}
                  
                  {/* Legend */}
                  <MapLegend />
                </div>
              </div>

              {/* System Console */}
              <div className="lg:col-span-1">
                <SystemConsole messages={consoleMessages} onClear={() => setConsoleMessages([])} />
              </div>
            </div>

            {/* Station Details Panel */}
            <AnimatePresence>
              {showDetailsPanel && selectedStation && (
                <StationDetailsPanel
                  station={selectedStation}
                  bikes={bikes.filter(b => b.stationId === selectedStation.id)}
                  allBikes={bikes} // Pass all bikes so we can find user's IN_USE bike
                  onClose={() => setShowDetailsPanel(false)}
                  onReturn={handleReturn}
                  onReserve={handleReserve}
                  onCheckout={handleCheckout}
                  onMove={handleMove}
                  onMarkMaintenance={handleMarkMaintenance}
                  onToggleStationStatus={handleToggleStationStatus}
                  stations={stations}
                  userRole={user?.role}
                />
              )}
            </AnimatePresence>
          </div>
      </div>
    </motion.div>
  );
};

export default MapDashboard;
