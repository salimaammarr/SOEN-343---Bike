package com.qwikride.service;

import com.qwikride.model.Bike;
import com.qwikride.model.BikeStation;
import com.qwikride.repository.BikeRepository;
import com.qwikride.repository.BikeStationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CleanupService {
    private final BikeStationRepository stationRepository;
    private final BikeRepository bikeRepository;

    @Transactional
    public void keepOnlyThreeStations() {
        log.info("🧹 Starting cleanup: Keeping only 3 stations...");
        
        List<BikeStation> allStations = stationRepository.findAll();
        log.info("Found {} stations in database", allStations.size());
        
        if (allStations.size() <= 3) {
            log.info("✅ Already have 3 or fewer stations. No cleanup needed.");
            return;
        }
        
        // Keep the first 3 stations (by ID order)
        List<BikeStation> stationsToKeep = allStations.stream()
                .sorted((s1, s2) -> Long.compare(s1.getId(), s2.getId()))
                .limit(3)
                .toList();
        
        log.info("Keeping stations: {}", stationsToKeep.stream()
                .map(s -> s.getName() + " (ID: " + s.getId() + ")")
                .toList());
        
        // Get IDs of stations to keep
        List<Long> keepStationIds = stationsToKeep.stream()
                .map(BikeStation::getId)
                .toList();
        
        // Delete all bikes from stations that will be deleted
        int deletedBikes = 0;
        for (BikeStation station : allStations) {
            if (!keepStationIds.contains(station.getId())) {
                List<Bike> stationBikes = bikeRepository.findByStationId(station.getId());
                for (Bike bike : stationBikes) {
                    bikeRepository.delete(bike);
                    deletedBikes++;
                }
                log.info("Deleted {} bikes from station: {} (ID: {})", 
                        stationBikes.size(), station.getName(), station.getId());
            }
        }
        
        // Delete stations that are not in the keep list
        int deletedStations = 0;
        for (BikeStation station : allStations) {
            if (!keepStationIds.contains(station.getId())) {
                stationRepository.delete(station);
                deletedStations++;
                log.info("Deleted station: {} (ID: {})", station.getName(), station.getId());
            }
        }
        
        log.info("✅ Cleanup completed:");
        log.info("   - Deleted {} stations", deletedStations);
        log.info("   - Deleted {} bikes", deletedBikes);
        log.info("   - Kept {} stations", stationsToKeep.size());
    }
}

