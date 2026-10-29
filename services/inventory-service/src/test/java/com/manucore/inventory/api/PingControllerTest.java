package com.manucore.inventory.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.manucore.api.PingController;
import com.manucore.api.PingResponse;
import org.junit.jupiter.api.Test;

public class PingControllerTest {

    private final PingController controller = new PingController();

    @Test
    void echoesGatewayHeaders() {
        PingResponse response = controller.ping("acme", "user-1", "corr-1");

        assertThat(response.service()).isEqualTo("inventory-service");
        assertThat(response.status()).isEqualTo("UP");
        assertThat(response.tenant()).isEqualTo("acme");
        assertThat(response.user()).isEqualTo("user-1");
        assertThat(response.correlationId()).isEqualTo("corr-1");
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void worksWithoutHeaders() {
        PingResponse response = controller.ping(null, null, null);

        assertThat(response.status()).isEqualTo("UP");
        assertThat(response.tenant()).isNull();
    }
}
