package com.smartplanner.smartplanner.dto.preference;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public class StudyPreferenceUpsertRequest {

    // optional
    private LocalDate planStartDate;
    private LocalDate planEndDate;



    @NotBlank
    @Pattern(
            regexp = "^(MON|TUE|WED|THU|FRI|SAT|SUN)(,(MON|TUE|WED|THU|FRI|SAT|SUN))*$",
            message = "allowedDays must be comma-separated values of MON..SUN (e.g., MON,TUE,FRI)"
    )
    private String allowedDays;

    @NotNull
    @Min(30)
    @Max(120)
    private Integer blockMinutes;

                public LocalDate getPlanStartDate() { return planStartDate; }
    public void setPlanStartDate(LocalDate planStartDate) { this.planStartDate = planStartDate; }

    public LocalDate getPlanEndDate() { return planEndDate; }
    public void setPlanEndDate(LocalDate planEndDate) { this.planEndDate = planEndDate; }



    public String getAllowedDays() { return allowedDays; }
    public void setAllowedDays(String allowedDays) { this.allowedDays = allowedDays; }

    public Integer getBlockMinutes() { return blockMinutes; }
    public void setBlockMinutes(Integer blockMinutes) { this.blockMinutes = blockMinutes; }
}
