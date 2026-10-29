package com.manucore.item;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemRepository extends JpaRepository<Item, UUID> {

    List<Item> findByTenantIdOrderBySkuAsc(String tenantId);

    Optional<Item> findByIdAndTenantId(UUID id, String tenantId);

    boolean existsByTenantIdAndSku(String tenantId, String sku);
}
