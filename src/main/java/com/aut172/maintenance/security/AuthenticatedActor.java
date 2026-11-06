package com.aut172.maintenance.security;

public record AuthenticatedActor(String id, Role role) {
    public enum Role { REPORTER, COORDINATOR, ENGINEER, SUPERVISOR }
}
