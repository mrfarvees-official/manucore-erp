package com.manucore.item.service;

import com.manucore.item.Item;
import com.manucore.item.StockAdjustment;
import com.manucore.item.dto.CreateItemRequest;
import com.manucore.item.dto.UpdateItemRequest;
import com.manucore.item.repository.ItemRepository;
import com.manucore.item.repository.StockAdjustmentRepository;
import lombok.AllArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@AllArgsConstructor
public class ItemService {

    private final ItemRepository items;
    private final StockAdjustmentRepository adjustments;

    @Transactional
    public Item create(String tenantId, CreateItemRequest request) {
        String sku = request.sku().trim().toUpperCase(Locale.ROOT);
        if (items.existsByTenantIdAndSku(tenantId, sku)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already exists: " + sku);
        }

        Item item = new Item(tenantId, sku, request.name(), request.unit(), request.quantity());
        item.updateCatalog(request.category(), request.type(), request.description(), request.reorderLevel());

        return items.save(item);
    }

    @Transactional(readOnly = true)
    public Page<Item> page(String tenantId, String query, int pageNumber, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(pageNumber, 0),
                Math.min(Math.max(size, 1), 100));

        String pattern = "%" + escapeLike(query == null ? "" : query.trim().toLowerCase(Locale.ROOT)) + "%";

        return items.search(tenantId, pattern, pageable);
    }

    @Transactional(readOnly = true)
    public Item get(String tenantId, UUID id) {
        return find(tenantId, id);
    }

    @Transactional
    public Item update(String tenantId, UUID id, UpdateItemRequest request) {
        Item item = find(tenantId, id);
        item.rename(request.name().trim(), request.unit().trim());
        item.updateCatalog(request.category(), request.type(), request.description(), request.reorderLevel());

        if (request.archived() != null) {
            item.setArchived(request.archived());
        }

        return item;
    }

    @Transactional
    public void delete(String tenantId, UUID id) {
        items.delete(find(tenantId, id));
    }

    @Transactional
    public Item adjust(String tenantId, UUID id, BigDecimal delta, String reason, String actorId) {
        if (delta.signum() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Adjustment must not be zero");
        }

        Item item = find(tenantId, id);
        BigDecimal before = item.getQuantity();
        BigDecimal after = before.add(delta);

        if (after.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Insufficient stock: available " + before + ", requested " + delta.abs());
        }

        item.setQuantity(after);
        adjustments.save(new StockAdjustment(item, delta, before, after, cleanReason(reason), actorId));

        return item;
    }

    private static String cleanReason(String reason) {
        return reason == null || reason.isBlank() ? null : reason.trim();
    }

    /* Looks up by id AND tenant. Another tenant's item looks exactly like a missing item (404). */
    private Item find(String tenantId, UUID id) {
        return items.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
    }

    /* A user typing % or _ in the search box must match those characters literally. */
    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
