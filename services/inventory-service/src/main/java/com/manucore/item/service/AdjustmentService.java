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
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        return itemId == null
                ? adjustments.findByTenantId(tenantId, pageable)
                : adjustments.findByTenantIdAndItemId(tenantId, itemId, pageable);
    }
}