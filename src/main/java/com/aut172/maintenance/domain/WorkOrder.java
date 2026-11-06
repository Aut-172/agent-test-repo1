package com.aut172.maintenance.domain;

import java.time.Instant;
import java.util.Objects;

public final class WorkOrder {
    private final String id;
    private final String title;
    private final String description;
    private final String assetId;
    private final Priority priority;
    private final String reportedBy;
    private final Instant createdAt;
    private final Instant acknowledgementDeadline;
    private final Instant resolutionDeadline;
    private WorkOrderStatus status;
    private Instant acknowledgedAt;
    private String acknowledgedBy;
    private Instant startedAt;
    private String assignedEngineer;
    private Instant resolvedAt;
    private String resolutionSummary;
    private Instant closedAt;
    private Instant cancelledAt;
    private String cancellationReason;

    public WorkOrder(String id, String title, String description, String assetId, Priority priority,
                     String reportedBy, Instant createdAt) {
        this.id = required(id, "id");
        this.title = required(title, "title");
        this.description = required(description, "description");
        this.assetId = required(assetId, "assetId");
        this.priority = Objects.requireNonNull(priority, "priority is required");
        this.reportedBy = required(reportedBy, "reportedBy");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.acknowledgementDeadline = SlaPolicy.acknowledgementDeadline(priority, createdAt);
        this.resolutionDeadline = SlaPolicy.resolutionDeadline(priority, createdAt);
        this.status = WorkOrderStatus.OPEN;
    }

    public void acknowledge(String actor, Instant occurredAt) {
        requireStatus(WorkOrderStatus.OPEN);
        acknowledgedBy = required(actor, "actor");
        acknowledgedAt = validActionTime(occurredAt);
        status = WorkOrderStatus.ACKNOWLEDGED;
    }

    public void startWork(String engineer, Instant occurredAt) {
        requireStatus(WorkOrderStatus.ACKNOWLEDGED);
        assignedEngineer = required(engineer, "engineer");
        startedAt = validActionTime(occurredAt);
        status = WorkOrderStatus.IN_PROGRESS;
    }

    public void resolve(String summary, Instant occurredAt) {
        requireStatus(WorkOrderStatus.IN_PROGRESS);
        resolutionSummary = required(summary, "summary");
        resolvedAt = validActionTime(occurredAt);
        status = WorkOrderStatus.RESOLVED;
    }

    public void close(Instant occurredAt) {
        requireStatus(WorkOrderStatus.RESOLVED);
        Instant candidate = validActionTime(occurredAt);
        if (candidate.isBefore(resolvedAt)) throw new IllegalArgumentException("closedAt cannot be before resolvedAt");
        closedAt = candidate;
        status = WorkOrderStatus.CLOSED;
    }

    public void cancel(String reason, Instant occurredAt) {
        if (status != WorkOrderStatus.OPEN && status != WorkOrderStatus.ACKNOWLEDGED) {
            throw new IllegalStateException("Only OPEN or ACKNOWLEDGED work orders can be cancelled");
        }
        cancellationReason = required(reason, "reason");
        cancelledAt = validActionTime(occurredAt);
        status = WorkOrderStatus.CANCELLED;
    }

    public boolean acknowledgementOverdueAt(Instant checkedAt) {
        return SlaPolicy.isOverdue(acknowledgedAt == null ? checkedAt : acknowledgedAt, acknowledgementDeadline);
    }

    public boolean resolutionOverdueAt(Instant checkedAt) {
        return SlaPolicy.isOverdue(resolvedAt == null ? checkedAt : resolvedAt, resolutionDeadline);
    }

    private void requireStatus(WorkOrderStatus expected) {
        if (status != expected) throw new IllegalStateException("Expected status " + expected + " but was " + status);
    }

    private Instant validActionTime(Instant occurredAt) {
        Instant value = Objects.requireNonNull(occurredAt, "occurredAt is required");
        if (value.isBefore(createdAt)) throw new IllegalArgumentException("Action time cannot be before createdAt");
        return value;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    public String id() { return id; }
    public String title() { return title; }
    public String description() { return description; }
    public String assetId() { return assetId; }
    public Priority priority() { return priority; }
    public String reportedBy() { return reportedBy; }
    public Instant createdAt() { return createdAt; }
    public Instant acknowledgementDeadline() { return acknowledgementDeadline; }
    public Instant resolutionDeadline() { return resolutionDeadline; }
    public WorkOrderStatus status() { return status; }
    public Instant acknowledgedAt() { return acknowledgedAt; }
    public String acknowledgedBy() { return acknowledgedBy; }
    public Instant startedAt() { return startedAt; }
    public String assignedEngineer() { return assignedEngineer; }
    public Instant resolvedAt() { return resolvedAt; }
    public String resolutionSummary() { return resolutionSummary; }
    public Instant closedAt() { return closedAt; }
    public Instant cancelledAt() { return cancelledAt; }
    public String cancellationReason() { return cancellationReason; }
}
