package fairshare_backend.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("test")
@Sql("/inventory-schema.sql")
class InventoryServiceTests {

    @Autowired InventoryService inventory;
    @Autowired JdbcTemplate jdbc;

    @Test
    void createsListsAndAdjustsHouseholdItems() {
        UUID userId = UUID.randomUUID();
        UUID householdId = createHousehold(userId);
        var created = inventory.create(householdId, userId,
                new CreateInventoryItemRequest("  Milk  ", "  Grocery  ", "🥛", 2));

        assertEquals("Milk", created.name());
        assertEquals("Grocery", created.category());
        assertEquals(2, inventory.list(householdId, userId).getFirst().quantity());
        assertEquals(3, inventory.adjust(householdId, created.id(), userId, 1).quantity());
        assertEquals(2, inventory.adjust(householdId, created.id(), userId, -1).quantity());
    }

    @Test
    void rejectsNegativeQuantitiesAndNonMemberAccess() {
        UUID userId = UUID.randomUUID();
        UUID householdId = createHousehold(userId);
        var created = inventory.create(householdId, userId,
                new CreateInventoryItemRequest("Eggs", "Grocery", "🥚", 0));
        ResponseStatusException negative = assertThrows(ResponseStatusException.class,
                () -> inventory.adjust(householdId, created.id(), userId, -1));
        assertEquals(409, negative.getStatusCode().value());

        ResponseStatusException inaccessible = assertThrows(ResponseStatusException.class,
                () -> inventory.list(householdId, UUID.randomUUID()));
        assertEquals(404, inaccessible.getStatusCode().value());
    }

    private UUID createHousehold(UUID ownerId) {
        UUID householdId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("insert into public.households (id, name, owner_user_id, created_at) values (?, ?, ?, ?)",
                householdId, "Test Home", ownerId, now);
        jdbc.update("insert into public.household_members (household_id, user_id) values (?, ?)",
                householdId, ownerId);
        return householdId;
    }
}
