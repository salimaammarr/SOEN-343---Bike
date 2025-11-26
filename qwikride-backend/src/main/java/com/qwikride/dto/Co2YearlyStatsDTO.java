package com.qwikride.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Co2YearlyStatsDTO {
    private Integer year;
    private Double co2Saved;
}
