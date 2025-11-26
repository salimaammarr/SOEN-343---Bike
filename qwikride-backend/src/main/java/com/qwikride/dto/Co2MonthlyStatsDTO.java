package com.qwikride.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Co2MonthlyStatsDTO {
    private String month;
    private Double monthlyCo2Saved;
    private Double cumulativeCo2Saved;
}
