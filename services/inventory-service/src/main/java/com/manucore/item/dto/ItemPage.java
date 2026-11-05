package com.manucore.item.dto;

import com.manucore.item.Item;
import org.springframework.data.domain.Page;

import java.util.List;

public record ItemPage(
        List<ItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static ItemPage from(Page<Item> page) {
        return new ItemPage(
                page.getContent().stream().map(ItemResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
