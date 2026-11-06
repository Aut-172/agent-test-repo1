package com.aut172.maintenance.service;

import com.aut172.maintenance.api.ApiException;
import com.aut172.maintenance.domain.Priority;
import com.aut172.maintenance.domain.WorkOrder;
import com.aut172.maintenance.domain.WorkOrderStatus;
import com.aut172.maintenance.persistence.AuditOutboxRepository;
import com.aut172.maintenance.persistence.StoredWorkOrder;
import com.aut172.maintenance.persistence.WorkOrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class PersistentWorkOrderService {
    private final WorkOrderRepository workOrders;
    private final AuditOutboxRepository records;

    public PersistentWorkOrderService(WorkOrderRepository workOrders, AuditOutboxRepository records) {
        this.workOrders = workOrders;
        this.records = records;
    }

    @Transactional
    public CreationResult create(CreateCommand command, String authenticatedActor) {
        if (!authenticatedActor.equals(command.reportedBy())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN_OPERATION",
                    "reportedBy must match authenticated subject");
        }
        String requestHash = hash(command.title() + "\n" + command.description() + "\n" + command.assetId()
                + "\n" + command.priority() + "\n" + command.reportedBy());
        var existing = workOrders.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            StoredWorkOrder stored = existing.orElseThrow();
            if (!stored.requestHash().equals(requestHash)) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                        "Idempotency key was already used for a different request");
            }
            return new CreationResult(stored, false);
        }
        try {
            WorkOrder order = new WorkOrder(UUID.randomUUID().toString(), command.title(), command.description(),
                    command.assetId(), command.priority(), command.reportedBy(), command.createdAt());
            StoredWorkOrder stored = new StoredWorkOrder(order, 1, command.idempotencyKey(), requestHash);
            workOrders.insert(stored);
            records.record(order.id(), "WORK_ORDER_CREATED", authenticatedActor, command.createdAt(),
                    Map.of("status", order.status().name(), "priority", order.priority().name()));
            return new CreationResult(stored, true);
        } catch (IllegalArgumentException error) {
            throw validation(error.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public StoredWorkOrder get(String id) {
        return workOrders.findById(id).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "WORK_ORDER_NOT_FOUND", "Work order not found"));
    }

    @Transactional(readOnly = true)
    public List<StoredWorkOrder> search(String assetId, WorkOrderStatus status, Priority priority,
                                        Instant createdFrom, Instant createdTo, int page, int size) {
        if (page < 0 || size < 1 || size > 200) {
            throw validation("page must be >= 0 and size must be between 1 and 200");
        }
        return workOrders.search(assetId, status, priority, createdFrom, createdTo, page, size);
    }

    @Transactional
    public StoredWorkOrder acknowledge(String id, long expectedVersion, String actor, Instant occurredAt) {
        return mutate(id, expectedVersion, actor, occurredAt, "WORK_ORDER_ACKNOWLEDGED",
                order -> order.acknowledge(actor, occurredAt));
    }

    @Transactional
    public StoredWorkOrder start(String id, long expectedVersion, String actor, Instant occurredAt) {
        return mutate(id, expectedVersion, actor, occurredAt, "WORK_ORDER_STARTED",
                order -> order.startWork(actor, occurredAt));
    }

    @Transactional
    public StoredWorkOrder resolve(String id, long expectedVersion, String actor, String summary, Instant occurredAt) {
        return mutate(id, expectedVersion, actor, occurredAt, "WORK_ORDER_RESOLVED",
                order -> order.resolve(summary, occurredAt));
    }

    @Transactional
    public StoredWorkOrder close(String id, long expectedVersion, String actor, Instant occurredAt) {
        return mutate(id, expectedVersion, actor, occurredAt, "WORK_ORDER_CLOSED",
                order -> order.close(occurredAt));
    }

    @Transactional
    public StoredWorkOrder cancel(String id, long expectedVersion, String actor, String reason, Instant occurredAt) {
        return mutate(id, expectedVersion, actor, occurredAt, "WORK_ORDER_CANCELLED",
                order -> order.cancel(reason, occurredAt));
    }

    private StoredWorkOrder mutate(String id, long expectedVersion, String actor, Instant occurredAt,
                                   String eventType, Consumer<WorkOrder> action) {
        StoredWorkOrder current = get(id);
        if (current.version() != expectedVersion) throw versionConflict(current.version());
        try { action.accept(current.order()); }
        catch (IllegalStateException error) {
            throw new ApiException(HttpStatus.CONFLICT, "WORK_ORDER_INVALID_STATE", error.getMessage(),
                    Map.of("currentStatus", current.order().status().name()));
        } catch (IllegalArgumentException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WORK_ORDER_INVALID_TIME", error.getMessage());
        }
        StoredWorkOrder updated = new StoredWorkOrder(current.order(), current.version() + 1,
                current.idempotencyKey(), current.requestHash());
        if (!workOrders.update(updated, expectedVersion)) throw versionConflict(expectedVersion);
        records.record(id, eventType, actor, occurredAt,
                Map.of("status", updated.order().status().name(), "version", updated.version()));
        return updated;
    }

    private ApiException versionConflict(long currentVersion) {
        return new ApiException(HttpStatus.CONFLICT, "WORK_ORDER_VERSION_CONFLICT",
                "Work order version does not match", Map.of("currentVersion", currentVersion));
    }

    private ApiException validation(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "WORK_ORDER_VALIDATION_FAILED", message);
    }

    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }

    public record CreateCommand(String title, String description, String assetId, Priority priority,
                                String reportedBy, String idempotencyKey, Instant createdAt) {}
    public record CreationResult(StoredWorkOrder stored, boolean created) {}
}
