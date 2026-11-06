package com.aut172.maintenance.domain;

import java.time.Instant;
import java.util.Objects;

public final class SlaPolicy {
    private SlaPolicy() {}

    public static Instant acknowledgementDeadline(Priority priority, Instant createdAt) {
        return requireCreatedAt(createdAt).plus(Objects.requireNonNull(priority).acknowledgementTarget());
    }

    public static Instant resolutionDeadline(Priority priority, Instant createdAt) {
        return requireCreatedAt(createdAt).plus(Objects.requireNonNull(priority).resolutionTarget());
    }

    public static boolean isOverdue(Instant effectiveTime, Instant deadline) {
        return Objects.requireNonNull(effectiveTime).isAfter(Objects.requireNonNull(deadline));
    }

    private static Instant requireCreatedAt(Instant createdAt) {
        return Objects.requireNonNull(createdAt, "createdAt is required");
    }
}
