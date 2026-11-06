package com.aut172.maintenance.persistence;

import com.aut172.maintenance.domain.Priority;
import com.aut172.maintenance.domain.WorkOrder;
import com.aut172.maintenance.domain.WorkOrderStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class WorkOrderRepository {
    private final JdbcTemplate jdbc;
    private final RowMapper<StoredWorkOrder> mapper = this::map;

    public WorkOrderRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<StoredWorkOrder> findById(String id) {
        return jdbc.query("SELECT * FROM work_orders WHERE id = ?", mapper, id).stream().findFirst();
    }

    public Optional<StoredWorkOrder> findByIdempotencyKey(String key) {
        return jdbc.query("SELECT * FROM work_orders WHERE idempotency_key = ?", mapper, key).stream().findFirst();
    }

    public void insert(StoredWorkOrder stored) {
        WorkOrder order = stored.order();
        jdbc.update("""
                INSERT INTO work_orders (
                    id, idempotency_key, request_hash, title, description, asset_id, priority, reported_by,
                    created_at, acknowledgement_deadline, resolution_deadline, status, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, order.id(), stored.idempotencyKey(), stored.requestHash(), order.title(), order.description(),
                order.assetId(), order.priority().name(), order.reportedBy(), timestamp(order.createdAt()),
                timestamp(order.acknowledgementDeadline()), timestamp(order.resolutionDeadline()),
                order.status().name(), stored.version());
    }

    public boolean update(StoredWorkOrder stored, long expectedVersion) {
        WorkOrder order = stored.order();
        int updated = jdbc.update("""
                UPDATE work_orders SET status = ?, acknowledged_at = ?, acknowledged_by = ?, started_at = ?,
                    assigned_engineer = ?, resolved_at = ?, resolution_summary = ?, closed_at = ?,
                    cancelled_at = ?, cancellation_reason = ?, version = ?
                WHERE id = ? AND version = ?
                """, order.status().name(), timestamp(order.acknowledgedAt()), order.acknowledgedBy(),
                timestamp(order.startedAt()), order.assignedEngineer(), timestamp(order.resolvedAt()),
                order.resolutionSummary(), timestamp(order.closedAt()), timestamp(order.cancelledAt()),
                order.cancellationReason(), stored.version(), order.id(), expectedVersion);
        return updated == 1;
    }

    public List<StoredWorkOrder> search(String assetId, WorkOrderStatus status, Priority priority,
                                        Instant createdFrom, Instant createdTo, int page, int size) {
        StringBuilder sql = new StringBuilder("SELECT * FROM work_orders WHERE 1=1");
        List<Object> parameters = new ArrayList<>();
        if (assetId != null && !assetId.isBlank()) { sql.append(" AND asset_id = ?"); parameters.add(assetId); }
        if (status != null) { sql.append(" AND status = ?"); parameters.add(status.name()); }
        if (priority != null) { sql.append(" AND priority = ?"); parameters.add(priority.name()); }
        if (createdFrom != null) { sql.append(" AND created_at >= ?"); parameters.add(timestamp(createdFrom)); }
        if (createdTo != null) { sql.append(" AND created_at <= ?"); parameters.add(timestamp(createdTo)); }
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        parameters.add(size);
        parameters.add(page * size);
        return jdbc.query(sql.toString(), mapper, parameters.toArray());
    }

    private StoredWorkOrder map(ResultSet result, int row) throws SQLException {
        WorkOrder order = WorkOrder.restore(
                result.getString("id"), result.getString("title"), result.getString("description"),
                result.getString("asset_id"), Priority.valueOf(result.getString("priority")),
                result.getString("reported_by"), instant(result, "created_at"),
                WorkOrderStatus.valueOf(result.getString("status")), instant(result, "acknowledged_at"),
                result.getString("acknowledged_by"), instant(result, "started_at"),
                result.getString("assigned_engineer"), instant(result, "resolved_at"),
                result.getString("resolution_summary"), instant(result, "closed_at"),
                instant(result, "cancelled_at"), result.getString("cancellation_reason"));
        return new StoredWorkOrder(order, result.getLong("version"),
                result.getString("idempotency_key"), result.getString("request_hash"));
    }

    private Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
}
