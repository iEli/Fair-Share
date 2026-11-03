package fairshare_backend.inventory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InventoryService {

    private static final int MAX_QUANTITY = 1_000_000;

    private final JdbcTemplate jdbcTemplate;

    public InventoryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<InventoryItemResponse> list(UUID householdId, UUID userId) {
        requireMembership(householdId, userId);
        return jdbcTemplate.query("""
                select id, name, category, icon, quantity, updated_at
                from public.inventory_items
                where household_id = ?
                order by lower(name), id
                """, this::toResponse, householdId);
    }

    @Transactional
    public InventoryItemResponse create(UUID householdId, UUID userId, CreateInventoryItemRequest request) {
        requireMembership(householdId, userId);

        String name = request.name().trim();
        String category = request.category().trim();
        String icon = request.icon() == null || request.icon().isBlank() ? "📦" : request.icon().trim();
        if (name.isEmpty() || name.length() > 100 || category.isEmpty() || category.length() > 60) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item name and category are required");
        }
        if (request.quantity() > MAX_QUANTITY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity cannot exceed " + MAX_QUANTITY);
        }

        UUID id = UUID.randomUUID();
        Instant updatedAt = Instant.now();
        jdbcTemplate.update("""
                insert into public.inventory_items
                    (id, household_id, name, category, icon, quantity, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, householdId, name, category, icon, request.quantity(),
                Timestamp.from(updatedAt), Timestamp.from(updatedAt));
        return new InventoryItemResponse(id, name, category, icon, request.quantity(), updatedAt);
    }

    @Transactional
    public InventoryItemResponse adjust(UUID householdId, UUID itemId, UUID userId, Integer delta) {
        requireMembership(householdId, userId);
        if (delta == null || (delta != -1 && delta != 1)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity adjustment must be +1 or -1");
        }

        List<InventoryItemResponse> items = jdbcTemplate.query("""
                select id, name, category, icon, quantity, updated_at
                from public.inventory_items
                where id = ? and household_id = ?
                for update
                """, this::toResponse, itemId, householdId);
        if (items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory item not found");
        }

        InventoryItemResponse item = items.getFirst();
        int quantity = item.quantity() + delta;
        if (quantity < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Inventory quantity cannot be negative");
        }
        if (quantity > MAX_QUANTITY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Inventory quantity cannot exceed " + MAX_QUANTITY);
        }

        Instant updatedAt = Instant.now();
        jdbcTemplate.update("""
                update public.inventory_items
                set quantity = ?, updated_at = ?
                where id = ? and household_id = ?
                """, quantity, Timestamp.from(updatedAt), itemId, householdId);
        return new InventoryItemResponse(item.id(), item.name(), item.category(), item.icon(), quantity, updatedAt);
    }

    private void requireMembership(UUID householdId, UUID userId) {
        Integer membershipCount = jdbcTemplate.queryForObject("""
                select count(*)
                from public.household_members
                where household_id = ? and user_id = ?
                """, Integer.class, householdId, userId);
        if (membershipCount == null || membershipCount == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Household not found");
        }
    }

    private InventoryItemResponse toResponse(ResultSet row, int rowNum) throws SQLException {
        return new InventoryItemResponse(
                row.getObject("id", UUID.class),
                row.getString("name"),
                row.getString("category"),
                row.getString("icon"),
                row.getInt("quantity"),
                row.getTimestamp("updated_at").toInstant());
    }
}
