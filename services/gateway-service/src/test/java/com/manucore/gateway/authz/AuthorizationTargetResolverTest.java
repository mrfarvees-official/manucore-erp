package com.manucore.gateway.authz;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationTargetResolverTest {

    private static void assertTarget(String method, String path, String action, String resource) {
        assertThat(AuthorizationTargetResolver.resolve(method, path))
                .contains(new AuthorizationTargetResolver.Target(action, resource));
    }

    private static void assertUnmapped(String method, String path) {
        assertThat(AuthorizationTargetResolver.resolve(method, path)).isEmpty();
    }

    @Test
    void mapsHttpMethodsToActions() {
        assertTarget("GET", "/api/v1/inventory/items", "READ", "INVENTORY.ITEMS");
        assertTarget("GET", "/api/v1/inventory/items/123", "READ", "INVENTORY.ITEMS");
        assertTarget("HEAD", "/api/v1/inventory/items", "READ", "INVENTORY.ITEMS");
        assertTarget("POST", "/api/v1/inventory/items", "CREATE", "INVENTORY.ITEMS");
        assertTarget("PUT", "/api/v1/inventory/items/123", "UPDATE", "INVENTORY.ITEMS");
        assertTarget("PATCH", "/api/v1/inventory/items/123", "UPDATE", "INVENTORY.ITEMS");
        assertTarget("DELETE", "/api/v1/inventory/items/123", "DELETE", "INVENTORY.ITEMS");
    }

    @Test
    void mapsPostVerbToItsOwnAction() {
        assertTarget("POST", "/api/v1/inventory/items/123/adjust", "ADJUST", "INVENTORY.ITEMS");
    }

    @Test
    void normalizesCaseAndHyphens() {
        assertTarget("GET", "/api/v1/master-data/raw-materials", "READ", "MASTER_DATA.RAW_MATERIALS");
        assertTarget("get", "/api/v1/inventory/items", "READ", "INVENTORY.ITEMS");
    }

    @Test
    void ignoresASingleTrailingSlash() {
        assertTarget("GET", "/api/v1/inventory/items/", "READ", "INVENTORY.ITEMS");
    }

    @Test
    void mapsPingEndpoint() {
        assertTarget("GET", "/api/v1/inventory/ping", "READ", "INVENTORY.PING");
    }

    @Test
    void refusesAmbiguousPostShapes() {
        assertUnmapped("POST", "/api/v1/inventory/items/123");
        assertUnmapped("POST", "/api/v1/inventory/items/123/adjust/extra");
    }

    @Test
    void refusesPathsWithoutServiceAndCollection() {
        assertUnmapped("GET", "/api/v1/inventory");
        assertUnmapped("GET", "/api/v1/");
        assertUnmapped("GET", "/other/path");
    }

    @Test
    void refusesUnsafeOrEmptySegments() {
        assertUnmapped("GET", "/api/v1/inventory/../items");
        assertUnmapped("GET", "/api/v1/inventory/%2e%2e");
        assertUnmapped("GET", "/api/v1//items");
        assertUnmapped("GET", "/api/v1/inventory/it ems");
        assertUnmapped("GET", "/api/v1/inventory/items;x=1");
    }

    @Test
    void refusesUnknownMethodsAndNulls() {
        assertUnmapped("TRACE", "/api/v1/inventory/items");
        assertUnmapped(null, "/api/v1/inventory/items");
        assertUnmapped("GET", null);
    }
}