package com.aut172.maintenance.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Repository
public class AuditOutboxRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AuditOutboxRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void record(String workOrderId, String action, String actor, Instant occurredAt,
                       Map<String, Object> payload) {
        String serialized = serialize(payload);
        jdbc.update("INSERT INTO work_order_audit_records (id, work_order_id, action, actor_id, occurred_at, payload_json) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), workOrderId, action, actor, Timestamp.from(occurredAt), serialized);
        jdbc.update("INSERT INTO outbox_events (id, aggregate_id, event_type, payload_json, occurred_at) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), workOrderId, action, serialized, Timestamp.from(occurredAt));
    }

    private String serialize(Map<String, Object> payload) {
        try { return json.writeValueAsString(payload); }
        catch (JsonProcessingException error) { throw new IllegalStateException("Could not serialize event", error); }
    }
}
