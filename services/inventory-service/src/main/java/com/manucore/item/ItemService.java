package com.manucore.item;

import com.manucore.item.dto.CreateItemRequest;
import com.manucore.item.dto.UpdateItemRequest;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@AllArgsConstructor
public class ItemService {

    private final ItemRepository items;

    @Transactional
    public Item create(String tenantId, CreateItemRequest request) {
        String sku = request.sku().trim().toUpperCase(Locale.ROOT);
        if (items.existsByTenantIdAndSku(tenantId, sku)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already exists: " + sku);
        }

        return items.save(new Item(tenantId, sku, request.name(), request.unit(), request.quantity()));
    }

    @Transactional(readOnly = true)
    public List<Item> list(String tenantId) {
        return items.findByTenantIdOrderBySkuAsc(tenantId);
    }

    @Transactional(readOnly = true)
    public Item get(String tenantId, UUID id) {
        return find(tenantId, id);
    }

    @Transactional
    public Item update(String tenantId, UUID id, UpdateItemRequest request) {
        Item item = find(tenantId, id);
        item.rename(request.name().trim(), request.unit().trim());
        return item;
    }

    @Transactional
    public void delete(String tenantId, UUID id) {
        items.delete(find(tenantId, id));
    }

    @Transactional
    public Item adjust(String tenantId, UUID id, BigDecimal delta) {
        Item item = find(tenantId, id);
        BigDecimal newQuantity = item.getQuantity().add(delta);
        if (newQuantity.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Insufficient stock: available " + item.getQuantity() + ", requested " + delta.abs());
        }
        item.setQuantity(newQuantity);
        return item;
    }

    /* Looks up by id AND tenant. Another tenant's item looks exactly like a missing item (404). */
    private Item find(String tenantId, UUID id) {
        return items.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
    }
}
