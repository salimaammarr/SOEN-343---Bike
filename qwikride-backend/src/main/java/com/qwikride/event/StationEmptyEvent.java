package com.qwikride.event;

import lombok.Getter;
import java.time.LocalDateTime;

@Getter
public class StationEmptyEvent extends BaseDomainEvent {
    private final Long stationId;

    public StationEmptyEvent(Long stationId) {
        this.stationId = stationId;
    }
}
