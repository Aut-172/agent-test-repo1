package com.aut172.maintenance.api;

import com.aut172.maintenance.MaintenanceApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = MaintenanceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkOrderApiIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    private static final Instant CREATED = Instant.parse("2026-11-06T08:00:00Z");

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM outbox_events");
        jdbc.update("DELETE FROM work_order_audit_records");
        jdbc.update("DELETE FROM work_orders");
    }

    @Test
    void rejectsUnauthenticatedRequests() {
        ResponseEntity<Map> response = http.getForEntity("/api/v1/work-orders/missing", Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("UNAUTHENTICATED", response.getBody().get("code"));
    }

    @Test
    void createsIdempotentlyAndRejectsConflictingReuse() {
        ResponseEntity<Map> created = create("incident-1", "Spindle overheating");
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        ResponseEntity<Map> repeated = create("incident-1", "Spindle overheating");
        assertEquals(HttpStatus.OK, repeated.getStatusCode());
        assertEquals(created.getBody().get("id"), repeated.getBody().get("id"));
        ResponseEntity<Map> conflict = create("incident-1", "Different fault");
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        assertEquals("IDEMPOTENCY_CONFLICT", conflict.getBody().get("code"));
    }

    @Test
    void completesAuthorizedLifecycleAndWritesAuditAndOutbox() {
        Map created = create("incident-2", "Cooling failure").getBody();
        String id = created.get("id").toString();
        Map acknowledged = action(id, "acknowledgement", 1, CREATED.plusSeconds(60), null,
                "coordinator-token", HttpStatus.OK).getBody();
        Map started = action(id, "start", 2, CREATED.plusSeconds(120), null,
                "engineer-token", HttpStatus.OK).getBody();
        Map resolved = action(id, "resolution", 3, CREATED.plusSeconds(300), "Fan replaced",
                "engineer-token", HttpStatus.OK).getBody();
        Map closed = action(id, "closure", 4, CREATED.plusSeconds(360), null,
                "supervisor-token", HttpStatus.OK).getBody();
        assertEquals("ACKNOWLEDGED", acknowledged.get("status"));
        assertEquals("IN_PROGRESS", started.get("status"));
        assertEquals("RESOLVED", resolved.get("status"));
        assertEquals("CLOSED", closed.get("status"));
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM work_order_audit_records", Integer.class));
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events", Integer.class));
    }

    @Test
    void rejectsRoleEscalationAndIllegalState() {
        Map created = create("incident-3", "Hydraulic leak").getBody();
        String id = created.get("id").toString();
        ResponseEntity<Map> forbidden = action(id, "closure", 1, CREATED.plusSeconds(60), null,
                "engineer-token", HttpStatus.FORBIDDEN);
        assertEquals("FORBIDDEN_OPERATION", forbidden.getBody().get("code"));
        ResponseEntity<Map> invalidState = action(id, "start", 1, CREATED.plusSeconds(60), null,
                "engineer-token", HttpStatus.CONFLICT);
        assertEquals("WORK_ORDER_INVALID_STATE", invalidState.getBody().get("code"));
    }

    @Test
    void rejectsStaleExpectedVersion() {
        Map created = create("incident-4", "Conveyor jam").getBody();
        String id = created.get("id").toString();
        action(id, "acknowledgement", 1, CREATED.plusSeconds(60), null, "coordinator-token", HttpStatus.OK);
        ResponseEntity<Map> conflict = action(id, "start", 1, CREATED.plusSeconds(120), null,
                "engineer-token", HttpStatus.CONFLICT);
        assertEquals("WORK_ORDER_VERSION_CONFLICT", conflict.getBody().get("code"));
    }

    @Test
    void searchesAndReportsSlaOverdueFlags() {
        Map created = create("incident-5", "Old critical fault", Instant.parse("2026-11-01T00:00:00Z")).getBody();
        HttpHeaders headers = bearer("reporter-token");
        ResponseEntity<Map> response = http.exchange("/api/v1/work-orders?assetId=CNC-07&page=0&size=20",
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        var content = (java.util.List<Map<String, Object>>) response.getBody().get("content");
        Map<String, Object> found = content.stream().filter(item -> item.get("id").equals(created.get("id"))).findFirst().orElseThrow();
        assertEquals(true, found.get("acknowledgementOverdue"));
        assertEquals(true, found.get("resolutionOverdue"));
    }

    private ResponseEntity<Map> create(String key, String title) { return create(key, title, CREATED); }

    private ResponseEntity<Map> create(String key, String title, Instant createdAt) {
        Map<String, Object> body = Map.of("title", title, "description", "Production stopped",
                "assetId", "CNC-07", "priority", "CRITICAL", "reportedBy", "reporter-1",
                "idempotencyKey", key, "createdAt", createdAt.toString());
        return http.exchange("/api/v1/work-orders", HttpMethod.POST,
                new HttpEntity<>(body, bearer("reporter-token")), Map.class);
    }

    private ResponseEntity<Map> action(String id, String action, long version, Instant occurredAt, String text,
                                       String token, HttpStatus expected) {
        Map<String, Object> body = text == null
                ? Map.of("expectedVersion", version, "occurredAt", occurredAt.toString())
                : Map.of("expectedVersion", version, "occurredAt", occurredAt.toString(),
                action.equals("resolution") ? "summary" : "reason", text);
        ResponseEntity<Map> response = http.exchange("/api/v1/work-orders/" + id + "/" + action,
                HttpMethod.POST, new HttpEntity<>(body, bearer(token)), Map.class);
        assertEquals(expected, response.getStatusCode());
        return response;
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
