package com.manucore.item;

import com.manucore.item.repository.ItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * Seeds sample inventory items for local development. Idempotent: an item whose SKU
 * already exists for the tenant is left untouched, so quantities you changed are
 * never reset.
 */
@Component
@ConditionalOnProperty(name = "manucore.inventory.seed-demo-data", havingValue = "true")
public class DemoItemSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoItemSeeder.class);

    private record Sample(String sku, String name, String unit, String quantity) {
    }

    private static final List<Sample> SAMPLES = List.of(
            new Sample("RM-STEEL-2MM", "Steel Sheet 2 mm", "kg", "1500"),
            new Sample("RM-ALU-3MM", "Aluminium Plate 3 mm", "kg", "800"),
            new Sample("RM-COPPER-WIRE", "Copper Wire 1.5 mm", "m", "5000"),
            new Sample("RM-ABS-GRAN", "ABS Plastic Granules", "kg", "2000"),
            new Sample("RM-PAINT-BLK", "Black Powder Coat Paint", "l", "120.5"),
            new Sample("CMP-BOLT-M8", "Hex Bolt M8 x 30", "pcs", "10000"),
            new Sample("CMP-NUT-M8", "Hex Nut M8", "pcs", "10000"),
            new Sample("CMP-WASHER-M8", "Flat Washer M8", "pcs", "12000"),
            new Sample("CMP-BEARING-6204", "Ball Bearing 6204", "pcs", "350"),
            new Sample("CMP-MOTOR-12V", "DC Motor 12 V", "pcs", "75"),
            new Sample("CMP-PCB-CTRL", "Control Board PCB", "pcs", "120"),
            new Sample("FG-PUMP-100", "Water Pump 100 L/min", "pcs", "40"),
            new Sample("FG-GEARBOX-2A", "Gearbox Assembly 2A", "pcs", "25"),
            new Sample("PKG-BOX-M", "Cardboard Box Medium", "pcs", "500"),
            new Sample("PKG-FOAM-SHEET", "Packing Foam Sheet", "m", "300"),
            new Sample("CON-LUBE-OIL", "Machine Lubricant Oil", "l", "60"),
            new Sample("CON-GLOVES-L", "Work Gloves Size L", "box", "20"),
            new Sample("CON-CUTTING-DISC", "Cutting Disc 125 mm", "pcs", "0"));

    private final ItemRepository items;
    private final List<String> tenants;

    public DemoItemSeeder(ItemRepository items,
                          @Value("${manucore.inventory.seed-tenants:acme}") String tenants) {
        this.items = items;
        this.tenants = Arrays.stream(tenants.split(","))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .toList();
    }

    @Override
    public void run(String... args) {
        for (String tenantId : tenants) {
            int created = 0;

            for (Sample sample : SAMPLES) {
                if (items.existsByTenantIdAndSku(tenantId, sample.sku())) {
                    continue;
                }

                items.save(new Item(
                        tenantId,
                        sample.sku(),
                        sample.name(),
                        sample.unit(),
                        new BigDecimal(sample.quantity())));
                created++;
            }

            log.info("Demo items for tenant '{}': {} created, {} already present",
                    tenantId, created, SAMPLES.size() - created);
        }
    }
}