package com.manucore.item.controller;

import com.manucore.item.dto.CatalogSummary;
import com.manucore.item.dto.ItemPage;
import com.manucore.item.service.CatalogService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory/catalog")
@AllArgsConstructor
public class CatalogController {

    private static final String TENANT_HEADER = "X-Tenant-Id";

    private final CatalogService service;

    @GetMapping
    public ItemPage list(@RequestHeader(TENANT_HEADER) String tenantId,
                         @RequestParam(defaultValue = "") String q,
                         @RequestParam(defaultValue = "") String category,
                         @RequestParam(defaultValue = "") String type,
                         @RequestParam(defaultValue = "ACTIVE") String status,
                         @RequestParam(defaultValue = "ALL") String stock,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "24") int size) {
        return ItemPage.from(service.search(tenantId, q, category, type, status, stock, page, size));
    }

    @GetMapping("/summary")
    public CatalogSummary summary(@RequestHeader(TENANT_HEADER) String tenantId) {
        return service.summary(tenantId);
    }
}