package com.manucore.item;

import com.manucore.item.dto.ItemType;
import com.manucore.item.repository.ItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Seeds sample inventory items for local development. Idempotent: existing items keep
 * their quantities, and catalog details are only filled in where they are still blank.
 */
@Component
@ConditionalOnProperty(name = "manucore.inventory.seed-demo-data", havingValue = "true")
public class DemoItemSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoItemSeeder.class);

    private record Sample(String sku, String name, String unit, String quantity,
                          String category, ItemType type, String reorderLevel) {
    }

    private static final List<Sample> SAMPLES = List.of(
            new Sample("RM-STEEL-2MM", "Steel Sheet 2 mm", "kg", "1500", "Metals", ItemType.RAW_MATERIAL, "500"),
            new Sample("RM-ALU-3MM", "Aluminium Plate 3 mm", "kg", "800", "Metals", ItemType.RAW_MATERIAL, "300"),
            new Sample("RM-COPPER-WIRE", "Copper Wire 1.5 mm", "m", "5000", "Wire & Cable", ItemType.RAW_MATERIAL, "1000"),
            new Sample("RM-ABS-GRAN", "ABS Plastic Granules", "kg", "2000", "Plastics", ItemType.RAW_MATERIAL, "500"),
            new Sample("RM-PAINT-BLK", "Black Powder Coat Paint", "l", "120.5", "Coatings", ItemType.RAW_MATERIAL, "50"),
            new Sample("CMP-BOLT-M8", "Hex Bolt M8 x 30", "pcs", "10000", "Fasteners", ItemType.COMPONENT, "2000"),
            new Sample("CMP-NUT-M8", "Hex Nut M8", "pcs", "10000", "Fasteners", ItemType.COMPONENT, "2000"),
            new Sample("CMP-WASHER-M8", "Flat Washer M8", "pcs", "12000", "Fasteners", ItemType.COMPONENT, "2000"),
            new Sample("CMP-BEARING-6204", "Ball Bearing 6204", "pcs", "350", "Bearings & Motors", ItemType.COMPONENT, "100"),
            new Sample("CMP-MOTOR-12V", "DC Motor 12 V", "pcs", "75", "Bearings & Motors", ItemType.COMPONENT, "20"),
            new Sample("CMP-PCB-CTRL", "Control Board PCB", "pcs", "120", "Electronics", ItemType.COMPONENT, "50"),
            new Sample("FG-PUMP-100", "Water Pump 100 L/min", "pcs", "40", "Pumps", ItemType.FINISHED_GOOD, "10"),
            new Sample("FG-GEARBOX-2A", "Gearbox Assembly 2A", "pcs", "25", "Gearboxes", ItemType.FINISHED_GOOD, "30"),
            new Sample("PKG-BOX-M", "Cardboard Box Medium", "pcs", "500", "Packaging", ItemType.PACKAGING, "200"),
            new Sample("PKG-FOAM-SHEET", "Packing Foam Sheet", "m", "300", "Packaging", ItemType.PACKAGING, "100"),
            new Sample("CON-LUBE-OIL", "Machine Lubricant Oil", "l", "60", "Lubricants", ItemType.CONSUMABLE, "80"),
            new Sample("CON-GLOVES-L", "Work Gloves Size L", "box", "20", "Safety", ItemType.CONSUMABLE, "10"),
            new Sample("CON-CUTTING-DISC", "Cutting Disc 125 mm", "pcs", "0", "Abrasives", ItemType.CONSUMABLE, "20"));

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
            int categorised = 0;

            for (Sample sample : SAMPLES) {
                Optional<Item> existing = items.findByTenantIdAndSku(tenantId, sample.sku());

                if (existing.isPresent()) {
                    Item item = existing.get();

                    if (item.getCategory() == null && item.getType() == null
                            && item.getReorderLevel() == null) {
                        item.updateCatalog(sample.category(), sample.type(), null,
                                new BigDecimal(sample.reorderLevel()));
                        items.save(item);
                        categorised++;
                    }
                    continue;
                }

                Item item = new Item(tenantId, sample.sku(), sample.name(), sample.unit(),
                        new BigDecimal(sample.quantity()));
                item.updateCatalog(sample.category(), sample.type(), null,
                        new BigDecimal(sample.reorderLevel()));
                items.save(item);
                created++;
            }

            log.info("Demo items for tenant '{}': {} created, {} categorised, {} unchanged",
                    tenantId, created, categorised, SAMPLES.size() - created - categorised);
        }
    }
}