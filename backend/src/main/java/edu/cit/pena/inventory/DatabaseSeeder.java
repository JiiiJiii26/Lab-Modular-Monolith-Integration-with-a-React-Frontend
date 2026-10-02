package edu.cit.pena.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSeeder.class);

    private final InventoryRepository inventoryRepository;
    private final JdbcTemplate jdbcTemplate;

    public DatabaseSeeder(InventoryRepository inventoryRepository, JdbcTemplate jdbcTemplate) {
        this.inventoryRepository = inventoryRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            // Relax constraints on legacy orders table columns if present
            jdbcTemplate.execute("ALTER TABLE orders ALTER COLUMN product_id DROP NOT NULL");
            jdbcTemplate.execute("ALTER TABLE orders ALTER COLUMN quantity DROP NOT NULL");
            // Drop old status check constraint created by Hibernate in Lab 1 (CONFIRMED, REJECTED only)
            jdbcTemplate.execute("ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check");
            jdbcTemplate.execute("ALTER TABLE orders ADD CONSTRAINT orders_status_check CHECK (status IN ('CONFIRMED', 'REJECTED', 'CANCELLED'))");
            log.info("Schema compatibility: updated orders constraints and status check for CANCELLED.");
        } catch (Exception e) {
            log.debug("Schema migration notice: {}", e.getMessage());
        }

        if (inventoryRepository.count() == 0) {
            log.info("Seeding initial inventory data...");
            inventoryRepository.saveAll(List.of(
                    new InventoryItem("P100", "Wireless Mouse", 25),
                    new InventoryItem("P200", "Mechanical Keyboard", 10),
                    new InventoryItem("P300", "USB-C Hub", 0)
            ));
            log.info("Inventory seeded successfully with P100 (25), P200 (10), P300 (0).");
        } else {
            log.info("Inventory table already populated. Skipping seeder.");
        }
    }
}

