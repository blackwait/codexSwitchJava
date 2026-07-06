package com.codexswitcher.model;

import java.time.LocalDateTime;

public class UsageSnapshot {

    private final String accountName;
    private final String baseUrl;
    private final boolean valid;
    private final Double remaining;
    private final String unit;
    private final Double dailyLimitUsd;
    private final Double dailyUsageUsd;
    private final Double weeklyLimitUsd;
    private final Double weeklyUsageUsd;
    private final LocalDateTime updatedAt;

    public UsageSnapshot(
        String accountName,
        String baseUrl,
        boolean valid,
        Double remaining,
        String unit,
        Double dailyLimitUsd,
        Double dailyUsageUsd,
        Double weeklyLimitUsd,
        Double weeklyUsageUsd,
        LocalDateTime updatedAt
    ) {
        this.accountName = accountName;
        this.baseUrl = baseUrl;
        this.valid = valid;
        this.remaining = remaining;
        this.unit = unit;
        this.dailyLimitUsd = dailyLimitUsd;
        this.dailyUsageUsd = dailyUsageUsd;
        this.weeklyLimitUsd = weeklyLimitUsd;
        this.weeklyUsageUsd = weeklyUsageUsd;
        this.updatedAt = updatedAt;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public boolean isValid() {
        return valid;
    }

    public Double getRemaining() {
        return remaining;
    }

    public String getUnit() {
        return unit;
    }

    public Double getDailyLimitUsd() {
        return dailyLimitUsd;
    }

    public Double getDailyUsageUsd() {
        return dailyUsageUsd;
    }

    public Double getWeeklyLimitUsd() {
        return weeklyLimitUsd;
    }

    public Double getWeeklyUsageUsd() {
        return weeklyUsageUsd;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public Double getDailyRemainingUsd() {
        if (dailyLimitUsd == null || dailyUsageUsd == null) {
            return remaining;
        }
        return dailyLimitUsd - dailyUsageUsd;
    }

    public Double getWeeklyRemainingUsd() {
        if (weeklyLimitUsd == null || weeklyUsageUsd == null) {
            return null;
        }
        return weeklyLimitUsd - weeklyUsageUsd;
    }
}
