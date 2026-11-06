package com.aut172.maintenance.service;

import com.aut172.maintenance.domain.Priority;
import com.aut172.maintenance.domain.WorkOrder;

import java.time.Instant;

public final class WorkOrderService {
    public WorkOrder create(String id, String title, String description, String assetId,
                            Priority priority, String reportedBy, Instant createdAt) {
        return new WorkOrder(id, title, description, assetId, priority, reportedBy, createdAt);
    }
}
