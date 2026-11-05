package com.manucore.item;

import com.manucore.item.dto.AdjustStockRequest;
import com.manucore.item.dto.CreateItemRequest;
import com.manucore.item.dto.ItemResponse;
import com.manucore.item.dto.UpdateItemRequest;
import com.manucore.item.dto.ItemPage;
import com.manucore.item.service.ItemService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory/items")
@AllArgsConstructor
public class ItemController {

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String USER_HEADER = "X-User-Id";

    private final ItemService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ItemResponse create(@RequestHeader(TENANT_HEADER) String tenantId,
                               @Valid @RequestBody CreateItemRequest request) {
        return ItemResponse.from(service.create(tenantId, request));
    }

    @GetMapping
    public ItemPage list(@RequestHeader(TENANT_HEADER) String tenantId,
                         @RequestParam(defaultValue = "") String q,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "20") int size) {
        return ItemPage.from(service.page(tenantId, q, page, size));
    }

    @GetMapping("/{id}")
    public ItemResponse get(@RequestHeader(TENANT_HEADER) String tenantId,
                            @PathVariable UUID id) {
        return ItemResponse.from(service.get(tenantId, id));
    }

    @PutMapping("/{id}")
    public ItemResponse update(@RequestHeader(TENANT_HEADER) String tenantId,
                               @PathVariable UUID id,
                               @Valid @RequestBody UpdateItemRequest request) {
        return ItemResponse.from(service.update(tenantId, id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(TENANT_HEADER) String tenantId,
                       @PathVariable UUID id) {
        service.delete(tenantId, id);
    }

    @PostMapping("/{id}/adjust")
    public ItemResponse adjust(@RequestHeader(TENANT_HEADER) String tenantId,
                               @RequestHeader(value = USER_HEADER, required = false) String userId,
                               @PathVariable UUID id,
                               @Valid @RequestBody AdjustStockRequest request) {
        return ItemResponse.from(
                service.adjust(tenantId, id, request.delta(), request.reason(), userId));
    }
}
