package com.aut172.maintenance.service;

import com.aut172.maintenance.domain.Priority;
import com.aut172.maintenance.domain.WorkOrder;
import com.aut172.maintenance.domain.WorkOrderStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class WorkOrderServiceTest {
    private static final Instant CREATED = Instant.parse("2026-11-06T08:00:00Z");
    private final WorkOrderService service = new WorkOrderService();

    @Test
    void createsCriticalWorkOrderWithExpectedDeadlines() {
        WorkOrder order = criticalOrder();
        assertEquals(WorkOrderStatus.OPEN, order.status());
        assertEquals(Instant.parse("2026-11-06T08:15:00Z"), order.acknowledgementDeadline());
        assertEquals(Instant.parse("2026-11-06T12:00:00Z"), order.resolutionDeadline());
    }

    @Test
    void followsCompleteHappyPath() {
        WorkOrder order = criticalOrder();
        order.acknowledge("coordinator-8", CREATED.plusSeconds(300));
        order.startWork("engineer-22", CREATED.plusSeconds(600));
        order.resolve("Replaced cooling fan", CREATED.plusSeconds(3600));
        order.close(CREATED.plusSeconds(3900));
        assertEquals(WorkOrderStatus.CLOSED, order.status());
        assertEquals("coordinator-8", order.acknowledgedBy());
        assertEquals("engineer-22", order.assignedEngineer());
        assertEquals("Replaced cooling fan", order.resolutionSummary());
    }

    @Test
    void rejectsIllegalStateTransition() {
        WorkOrder order = criticalOrder();
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> order.startWork("engineer-22", CREATED.plusSeconds(60)));
        assertTrue(error.getMessage().contains("ACKNOWLEDGED"));
        assertEquals(WorkOrderStatus.OPEN, order.status());
    }

    @Test
    void allowsCancellationOnlyBeforeWorkStarts() {
        WorkOrder open = criticalOrder();
        open.cancel("Duplicate request", CREATED.plusSeconds(30));
        assertEquals(WorkOrderStatus.CANCELLED, open.status());

        WorkOrder started = criticalOrder();
        started.acknowledge("coordinator-8", CREATED.plusSeconds(30));
        started.startWork("engineer-22", CREATED.plusSeconds(60));
        assertThrows(IllegalStateException.class,
                () -> started.cancel("No longer needed", CREATED.plusSeconds(90)));
    }

    @Test
    void evaluatesAcknowledgementBoundaryStrictlyAfterDeadline() {
        WorkOrder order = criticalOrder();
        assertFalse(order.acknowledgementOverdueAt(order.acknowledgementDeadline()));
        assertTrue(order.acknowledgementOverdueAt(order.acknowledgementDeadline().plusNanos(1)));
    }

    @Test
    void preservesHistoricalSlaResultAfterActionCompletes() {
        WorkOrder order = criticalOrder();
        order.acknowledge("coordinator-8", order.acknowledgementDeadline().minusSeconds(1));
        assertFalse(order.acknowledgementOverdueAt(CREATED.plusSeconds(86_400)));
    }

    @Test
    void rejectsActionTimeBeforeCreation() {
        WorkOrder order = criticalOrder();
        assertThrows(IllegalArgumentException.class,
                () -> order.acknowledge("coordinator-8", CREATED.minusSeconds(1)));
        assertEquals(WorkOrderStatus.OPEN, order.status());
    }

    @Test
    void rejectsBlankRequiredFields() {
        assertThrows(IllegalArgumentException.class,
                () -> service.create("WO-1", " ", "description", "CNC-07",
                        Priority.HIGH, "operator-104", CREATED));
    }

    private WorkOrder criticalOrder() {
        return service.create("WO-20261106-0001", "Spindle overheating",
                "Temperature exceeded 95C", "CNC-07", Priority.CRITICAL,
                "operator-104", CREATED);
    }
}
