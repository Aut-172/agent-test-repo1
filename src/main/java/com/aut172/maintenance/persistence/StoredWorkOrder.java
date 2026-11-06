package com.aut172.maintenance.persistence;

import com.aut172.maintenance.domain.WorkOrder;

public record StoredWorkOrder(WorkOrder order, long version, String idempotencyKey, String requestHash) {}
