package com.manucore.item.service;

import com.manucore.item.Item;
import com.manucore.item.dto.ItemType;
import com.manucore.item.dto.CatalogSummary;
import com.manucore.item.repository.ItemRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;

@Service
@AllArgsConstructor
public class CatalogService {

    private static final Set<String> STATUSES = Set.of("ACTIVE", "ARCHIVED", "ALL");
    private static final Set<String> STOCK_FILTERS = Set.of("ALL", "LOW", "OUT");

    private final ItemRepository items;

    @Transactional(readOnly = true)
    public Page<Item> search(String tenantId, String query, String category, String type,
                             String status, String stock, int page, int size) {
        String statusFilter = status.trim().toUpperCase(Locale.ROOT);
        String stockFilter = stock.trim().toUpperCase(Locale.ROOT);
        String typeFilter = type.trim().toUpperCase(Locale.ROOT);

        if (!STATUSES.contains(statusFilter) || !STOCK_FILTERS.contains(stockFilter)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status or stock filter");
        }

        if (!typeFilter.isEmpty()) {
            try {
                ItemType.valueOf(typeFilter);
            } catch (IllegalArgumentException unknown) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown item type: " + type);
            }
        }

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100));

        String pattern = "%" + escapeLike(query.trim().toLowerCase(Locale.ROOT)) + "%";

        return items.catalog(tenantId, pattern, category.trim(), typeFilter,
                statusFilter, stockFilter, pageable);
    }

    @Transactional(readOnly = true)
    public CatalogSummary summary(String tenantId) {
        return new CatalogSummary(
                items.countActive(tenantId),
                items.countLowStock(tenantId),
                items.countOutOfStock(tenantId),
                items.categories(tenantId));
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}