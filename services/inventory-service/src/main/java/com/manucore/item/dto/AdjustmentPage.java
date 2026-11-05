package com.manucore.item.dto;

import com.manucore.item.StockAdjustment;
import org.springframework.data.domain.Page;

import java.util.List;

public record AdjustmentPage(
        List<AdjustmentResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static AdjustmentPage from(Page<StockAdjustment> page) {
        return new AdjustmentPage(
                page.getContent().stream().map(AdjustmentResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}