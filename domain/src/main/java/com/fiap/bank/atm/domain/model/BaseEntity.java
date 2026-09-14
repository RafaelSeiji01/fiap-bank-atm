package com.fiap.bank.atm.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class BaseEntity {
    private final UUID id;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    protected BaseEntity(UUID id) {
        this.id = id;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
