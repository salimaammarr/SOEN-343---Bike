/**
 * Utility functions for bike and station filtering
 */

/**
 * Filter and deduplicate stations
 * @param {Array} stations - Array of station objects
 * @param {Object} options - Filter options
 * @returns {Array} - Deduplicated and filtered stations
 */
export const getFilteredStations = (stations, options = {}) => {
  if (!Array.isArray(stations)) return [];
  
  const {
    status = null, // 'ACTIVE' or 'OUT_OF_SERVICE' or null for all
    hasAvailableBikes = false, // Only stations with available bikes
    bikes = [], // Array of bikes to check availability
    minCapacity = 0 // Minimum free capacity
  } = options;

  // Deduplicate by ID
  const uniqueStations = stations.reduce((acc, station) => {
    const existing = acc.find(s => s.id === station.id);
    if (!existing) {
      acc.push(station);
    }
    return acc;
  }, []);

  // Apply filters
  return uniqueStations.filter(station => {
    // Status filter
    if (status && station.status !== status) {
      return false;
    }

    // Available bikes filter
    if (hasAvailableBikes) {
      const availableCount = bikes.filter(
        b => b.stationId === station.id && b.status === 'AVAILABLE'
      ).length;
      if (availableCount === 0) {
        return false;
      }
    }

    // Minimum capacity filter
    if (minCapacity > 0) {
      const freeDocks = station.capacity - station.currentBikeCount;
      if (freeDocks < minCapacity) {
        return false;
      }
    }

    return true;
  });
};

/**
 * Filter bikes by various criteria
 * @param {Array} bikes - Array of bike objects
 * @param {Object} options - Filter options
 * @returns {Array} - Filtered bikes
 */
export const getFilteredBikes = (bikes, options = {}) => {
  if (!Array.isArray(bikes)) return [];

  const {
    status = null, // 'AVAILABLE', 'RESERVED', 'IN_USE', 'MAINTENANCE', or null for all
    stationId = null, // Filter by station ID
    userId = null, // Filter by user ID (for IN_USE or RESERVED bikes)
    excludeStatus = [] // Array of statuses to exclude
  } = options;

  // Deduplicate by ID
  const uniqueBikes = bikes.reduce((acc, bike) => {
    const existing = acc.find(b => b.id === bike.id);
    if (!existing) {
      acc.push(bike);
    }
    return acc;
  }, []);

  // Apply filters
  return uniqueBikes.filter(bike => {
    // Status filter
    if (status && bike.status !== status) {
      return false;
    }

    // Exclude status filter
    if (excludeStatus.includes(bike.status)) {
      return false;
    }

    // Station filter
    if (stationId !== null && bike.stationId !== stationId) {
      return false;
    }

    // User filter (for IN_USE or RESERVED bikes)
    if (userId !== null) {
      if (bike.status === 'IN_USE' && bike.currentUserId !== userId) {
        return false;
      }
      if (bike.status === 'RESERVED' && bike.reservedByUserId !== userId) {
        return false;
      }
    }

    return true;
  });
};

/**
 * Get bikes grouped by station
 * @param {Array} bikes - Array of bike objects
 * @returns {Object} - Object with stationId as keys and arrays of bikes as values
 */
export const getBikesByStation = (bikes) => {
  if (!Array.isArray(bikes)) return {};
  
  return bikes.reduce((acc, bike) => {
    const stationId = bike.stationId;
    if (stationId) {
      if (!acc[stationId]) {
        acc[stationId] = [];
      }
      acc[stationId].push(bike);
    }
    return acc;
  }, {});
};

/**
 * Get available bikes count for a station
 * @param {Array} bikes - Array of bike objects
 * @param {Number} stationId - Station ID
 * @returns {Number} - Count of available bikes
 */
export const getAvailableBikesCount = (bikes, stationId) => {
  if (!Array.isArray(bikes) || !stationId) return 0;
  return bikes.filter(
    b => b.stationId === stationId && b.status === 'AVAILABLE'
  ).length;
};

/**
 * Get bikes that should be displayed in the "All Bikes" list
 * Only shows bikes that are at stations (not IN_USE bikes that are out)
 * @param {Array} bikes - Array of bike objects
 * @returns {Array} - Filtered bikes for display
 */
export const getDisplayableBikes = (bikes) => {
  if (!Array.isArray(bikes)) return [];
  
  // Deduplicate first
  const uniqueBikes = bikes.reduce((acc, bike) => {
    const existing = acc.find(b => b.id === bike.id);
    if (!existing) {
      acc.push(bike);
    }
    return acc;
  }, []);

  // Filter: Show bikes that are at stations (have stationId) or are IN_USE (user's active bike)
  return uniqueBikes.filter(bike => {
    // Always show bikes at stations
    if (bike.stationId) {
      return true;
    }
    // Show IN_USE bikes (they might not have stationId while in use)
    if (bike.status === 'IN_USE') {
      return true;
    }
    // Don't show bikes without stationId and not IN_USE (orphaned bikes)
    return false;
  });
};

