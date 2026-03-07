package com.smartplanner.smartplanner.dto.dashboard;

import java.math.BigDecimal;

public record CompleteSessionRequest(
        BigDecimal actualHoursLogged,
        String difficulty,
        String note) {
}
