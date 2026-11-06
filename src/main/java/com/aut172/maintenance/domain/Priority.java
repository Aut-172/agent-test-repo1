package com.aut172.maintenance.domain;

import java.time.Duration;

public enum Priority {
    CRITICAL(Duration.ofMinutes(15), Duration.ofHours(4)),
    HIGH(Duration.ofMinutes(30), Duration.ofHours(8)),
    MEDIUM(Duration.ofHours(4), Duration.ofHours(24)),
    LOW(Duration.ofHours(8), Duration.ofHours(72));

    private final Duration acknowledgementTarget;
    private final Duration resolutionTarget;

    Priority(Duration acknowledgementTarget, Duration resolutionTarget) {
        this.acknowledgementTarget = acknowledgementTarget;
        this.resolutionTarget = resolutionTarget;
    }

    public Duration acknowledgementTarget() { return acknowledgementTarget; }
    public Duration resolutionTarget() { return resolutionTarget; }
}
