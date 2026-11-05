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

    Optional<Item> findByTenantIdAndSku(String tenantId, String sku);

    @Query("""
            select i from Item i
            where i.tenantId = :tenantId
              and (lower(i.sku) like :pattern escape '!'
                   or lower(i.name) like :pattern escape '!')
              and (:category = '' or i.category = :category)
              and (:type = '' or cast(i.type as String) = :type)
              and (:status = 'ALL'
                   or (:status = 'ACTIVE' and i.archivedAt is null)
                   or (:status = 'ARCHIVED' and i.archivedAt is not null))
              and (:stock = 'ALL'
                   or (:stock = 'OUT' and i.quantity = 0)
                   or (:stock = 'LOW' and i.reorderLevel is not null
                       and i.quantity > 0 and i.quantity <= i.reorderLevel))
            order by i.sku asc
            """)
    Page<Item> catalog(@Param("tenantId") String tenantId,
                       @Param("pattern") String pattern,
                       @Param("category") String category,
                       @Param("type") String type,
                       @Param("status") String status,
                       @Param("stock") String stock,
                       Pageable pageable);

    @Query("select count(i) from Item i where i.tenantId = :tenantId and i.archivedAt is null")
    long countActive(@Param("tenantId") String tenantId);

    @Query("""
            select count(i) from Item i
            where i.tenantId = :tenantId and i.archivedAt is null
              and i.reorderLevel is not null
              and i.quantity > 0 and i.quantity <= i.reorderLevel
            """)
    long countLowStock(@Param("tenantId") String tenantId);

    @Query("""
            select count(i) from Item i
            where i.tenantId = :tenantId and i.archivedAt is null and i.quantity = 0
            """)
    long countOutOfStock(@Param("tenantId") String tenantId);

    @Query("""
            select distinct i.category from Item i
            where i.tenantId = :tenantId and i.category is not null
            order by i.category
            """)
    List<String> categories(@Param("tenantId") String tenantId);
}
