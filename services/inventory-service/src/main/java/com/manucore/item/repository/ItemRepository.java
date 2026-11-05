package com.manucore.item.repository;

import com.manucore.item.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemRepository extends JpaRepository<Item, UUID> {

    List<Item> findByTenantIdOrderBySkuAsc(String tenantId);

    Optional<Item> findByIdAndTenantId(UUID id, String tenantId);

    boolean existsByTenantIdAndSku(String tenantId, String sku);

    @Query("""
            select i from Item i
            where i.tenantId = :tenantId
              and (lower(i.sku) like :pattern escape '!'
                   or lower(i.name) like :pattern escape '!')
            order by i.sku asc
            """)
    Page<Item> search(@Param("tenantId") String tenantId,
                      @Param("pattern") String pattern,
                      Pageable pageable);
}
