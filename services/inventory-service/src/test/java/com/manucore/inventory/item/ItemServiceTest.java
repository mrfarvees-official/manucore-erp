package com.manucore.inventory.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import com.manucore.item.Item;
import com.manucore.item.ItemRepository;
import com.manucore.item.ItemService;
import com.manucore.item.dto.CreateItemRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    @Mock
    ItemRepository repository;

    @InjectMocks
    ItemService service;

    @Test
    void rejectsDuplicateSkuInSameTenant() {
        when(repository.existsByTenantIdAndSku("acme", "BOLT-1")).thenReturn(true);

        assertThatThrownBy(() -> service.create("acme",
                new CreateItemRequest(" bolt-1 ", "Bolt", "pcs", BigDecimal.TEN)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void otherTenantsItemLooksMissing() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, "globex")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("globex", id))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void adjustAddsAndRemovesStock() {
        Item item = new Item("acme", "BOLT-1", "Bolt", "pcs", new BigDecimal("5"));
        when(repository.findByIdAndTenantId(item.getId(), "acme")).thenReturn(Optional.of(item));

        Item result = service.adjust("acme", item.getId(), new BigDecimal("-2"));

        assertThat(result.getQuantity()).isEqualByComparingTo("3");
    }

    @Test
    void adjustCannotGoBelowZero() {
        Item item = new Item("acme", "BOLT-1", "Bolt", "pcs", new BigDecimal("5"));
        when(repository.findByIdAndTenantId(item.getId(), "acme")).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.adjust("acme", item.getId(), new BigDecimal("-6")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }
}