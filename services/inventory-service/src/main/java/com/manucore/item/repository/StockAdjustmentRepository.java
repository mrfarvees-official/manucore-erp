package com.manucore.item.repository;

import com.manucore.item.StockAdjustment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, UUID> {

    Page<StockAdjustment> findByTenantId(String tenantId, Pageable pageable);

    Page<StockAdjustment> findByTenantIdAndItemId(String tenantId, UUID itemId, Pageable pageable);
}