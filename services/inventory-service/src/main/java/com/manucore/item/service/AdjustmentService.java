package com.manucore.item.service;

import com.manucore.item.StockAdjustment;
import com.manucore.item.repository.StockAdjustmentRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@AllArgsConstructor
public class AdjustmentService {

    private final StockAdjustmentRepository adjustments;

    @Transactional(readOnly = true)
    public Page<StockAdjustment> list(String tenantId, UUID itemId, int page, int size) {
        return list(tenantId, itemId, null, page, size);
    }

    @Transactional(readOnly = true)
    public Page<StockAdjustment> list(String tenantId, UUID itemId, String actorId, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        boolean byActor = actorId != null && !actorId.isBlank();
        String actor = byActor ? actorId.trim() : null;

        if (itemId != null && byActor) {
            return adjustments.findByTenantIdAndItemIdAndActorId(tenantId, itemId, actor, pageable);
        }

        if (itemId != null) {
            return adjustments.findByTenantIdAndItemId(tenantId, itemId, pageable);
        }

        if (byActor) {
            return adjustments.findByTenantIdAndActorId(tenantId, actor, pageable);
        }

        return adjustments.findByTenantId(tenantId, pageable);
    }
}