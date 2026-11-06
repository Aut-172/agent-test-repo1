package com.aut172.maintenance.api;

import com.aut172.maintenance.domain.Priority;
import com.aut172.maintenance.domain.WorkOrder;
import com.aut172.maintenance.domain.WorkOrderStatus;
import com.aut172.maintenance.persistence.StoredWorkOrder;
import com.aut172.maintenance.security.AuthenticatedActor;
import com.aut172.maintenance.security.BearerAuthenticationFilter;
import com.aut172.maintenance.service.PersistentWorkOrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/work-orders")
public class WorkOrderController {
    private final PersistentWorkOrderService service;

    public WorkOrderController(PersistentWorkOrderService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<WorkOrderResponse> create(@Valid @RequestBody CreateRequest request,
                                                    HttpServletRequest servletRequest) {
        AuthenticatedActor actor = actor(servletRequest);
        var result = service.create(new PersistentWorkOrderService.CreateCommand(
                request.title(), request.description(), request.assetId(), request.priority(), request.reportedBy(),
                request.idempotencyKey(), request.createdAt() == null ? Instant.now() : request.createdAt()), actor.id());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(response(result.stored(), Instant.now()));
    }

    @GetMapping("/{id}")
    public WorkOrderResponse get(@PathVariable String id) { return response(service.get(id), Instant.now()); }

    @GetMapping
    public PageResponse search(@RequestParam(required = false) String assetId,
                               @RequestParam(required = false) WorkOrderStatus status,
                               @RequestParam(required = false) Priority priority,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
                               @RequestParam(defaultValue = "0") @Min(0) int page,
                               @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        Instant evaluatedAt = Instant.now();
        List<WorkOrderResponse> content = service.search(assetId, status, priority, createdFrom, createdTo, page, size)
                .stream().map(value -> response(value, evaluatedAt)).toList();
        return new PageResponse(content, page, size);
    }

    @PostMapping("/{id}/acknowledgement")
    public WorkOrderResponse acknowledge(@PathVariable String id, @Valid @RequestBody TimedAction request,
                                         HttpServletRequest servletRequest) {
        AuthenticatedActor actor = requireRole(servletRequest, AuthenticatedActor.Role.COORDINATOR);
        return response(service.acknowledge(id, request.expectedVersion(), actor.id(), request.occurredAt()), Instant.now());
    }

    @PostMapping("/{id}/start")
    public WorkOrderResponse start(@PathVariable String id, @Valid @RequestBody TimedAction request,
                                   HttpServletRequest servletRequest) {
        AuthenticatedActor actor = requireRole(servletRequest, AuthenticatedActor.Role.ENGINEER);
        return response(service.start(id, request.expectedVersion(), actor.id(), request.occurredAt()), Instant.now());
    }

    @PostMapping("/{id}/resolution")
    public WorkOrderResponse resolve(@PathVariable String id, @Valid @RequestBody ResolutionRequest request,
                                     HttpServletRequest servletRequest) {
        AuthenticatedActor actor = requireRole(servletRequest, AuthenticatedActor.Role.ENGINEER);
        return response(service.resolve(id, request.expectedVersion(), actor.id(), request.summary(), request.occurredAt()), Instant.now());
    }

    @PostMapping("/{id}/closure")
    public WorkOrderResponse close(@PathVariable String id, @Valid @RequestBody TimedAction request,
                                   HttpServletRequest servletRequest) {
        AuthenticatedActor actor = requireRole(servletRequest, AuthenticatedActor.Role.SUPERVISOR);
        return response(service.close(id, request.expectedVersion(), actor.id(), request.occurredAt()), Instant.now());
    }

    @PostMapping("/{id}/cancellation")
    public WorkOrderResponse cancel(@PathVariable String id, @Valid @RequestBody CancellationRequest request,
                                    HttpServletRequest servletRequest) {
        AuthenticatedActor actor = requireRole(servletRequest, AuthenticatedActor.Role.COORDINATOR);
        return response(service.cancel(id, request.expectedVersion(), actor.id(), request.reason(), request.occurredAt()), Instant.now());
    }

    private AuthenticatedActor actor(HttpServletRequest request) {
        return (AuthenticatedActor) request.getAttribute(BearerAuthenticationFilter.ACTOR_ATTRIBUTE);
    }

    private AuthenticatedActor requireRole(HttpServletRequest request, AuthenticatedActor.Role... allowed) {
        AuthenticatedActor actor = actor(request);
        if (!Set.of(allowed).contains(actor.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN_OPERATION", "Role cannot perform this operation");
        }
        return actor;
    }

    private WorkOrderResponse response(StoredWorkOrder stored, Instant evaluatedAt) {
        WorkOrder order = stored.order();
        return new WorkOrderResponse(order.id(), order.title(), order.description(), order.assetId(), order.priority(),
                order.reportedBy(), order.status(), stored.version(), order.createdAt(),
                order.acknowledgementDeadline(), order.resolutionDeadline(), order.acknowledgedAt(),
                order.acknowledgedBy(), order.startedAt(), order.assignedEngineer(), order.resolvedAt(),
                order.resolutionSummary(), order.closedAt(), order.cancelledAt(), order.cancellationReason(),
                order.acknowledgementOverdueAt(evaluatedAt), order.resolutionOverdueAt(evaluatedAt), evaluatedAt);
    }

    public record CreateRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 10000) String description,
            @NotBlank @Size(max = 100) String assetId,
            @NotNull Priority priority,
            @NotBlank @Size(max = 100) String reportedBy,
            @NotBlank @Size(max = 200) String idempotencyKey,
            Instant createdAt) {}
    public record TimedAction(@NotNull Instant occurredAt, @Min(1) long expectedVersion) {}
    public record ResolutionRequest(@NotNull Instant occurredAt, @Min(1) long expectedVersion,
                                    @NotBlank @Size(max = 4000) String summary) {}
    public record CancellationRequest(@NotNull Instant occurredAt, @Min(1) long expectedVersion,
                                      @NotBlank @Size(max = 4000) String reason) {}
    public record PageResponse(List<WorkOrderResponse> content, int page, int size) {}
    public record WorkOrderResponse(
            String id, String title, String description, String assetId, Priority priority, String reportedBy,
            WorkOrderStatus status, long version, Instant createdAt, Instant acknowledgementDeadline,
            Instant resolutionDeadline, Instant acknowledgedAt, String acknowledgedBy, Instant startedAt,
            String assignedEngineer, Instant resolvedAt, String resolutionSummary, Instant closedAt,
            Instant cancelledAt, String cancellationReason, boolean acknowledgementOverdue,
            boolean resolutionOverdue, Instant evaluatedAt) {}
}
