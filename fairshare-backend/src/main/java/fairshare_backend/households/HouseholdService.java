package fairshare_backend.households;

import java.sql.Timestamp;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class HouseholdService {

    private final JdbcTemplate jdbcTemplate;

    public HouseholdService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public HouseholdResponse create(String requestedName, UUID ownerUserId) {
        String name = requestedName == null ? "" : requestedName.trim();
        if (name.isEmpty() || name.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Household name must be 1 to 100 characters");
        }

        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();
        jdbcTemplate.update(
                "insert into public.households (id, name, owner_user_id, created_at) values (?, ?, ?, ?)",
                id, name, ownerUserId, Timestamp.from(createdAt));
        jdbcTemplate.update(
                "insert into public.household_members (household_id, user_id) values (?, ?)",
                id, ownerUserId);

        return new HouseholdResponse(id, name, ownerUserId, List.of(ownerUserId), createdAt);
    }

    public List<HouseholdResponse> listForUser(UUID userId) {
        return jdbcTemplate.query("""
                select h.id, h.name, h.owner_user_id, h.created_at
                from public.households h
                join public.household_members m on m.household_id = h.id
                where m.user_id = ?
                order by h.created_at desc
                """, this::toResponse, userId);
    }

    @Transactional(readOnly = true)
    public HouseholdResponse getForUser(UUID householdId, UUID userId) {
        var households = jdbcTemplate.query("""
                select h.id, h.name, h.owner_user_id, h.created_at
                from public.households h
                where h.id = ? and exists (
                    select 1 from public.household_members m
                    where m.household_id = h.id and m.user_id = ?
                )
                """, this::toResponse, householdId, userId);
        // Use the same response for missing and inaccessible households.
        if (households.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Household not found");
        }
        return households.getFirst();
    }

    private HouseholdResponse toResponse(ResultSet row, int rowNum) throws SQLException {
        UUID householdId = row.getObject("id", UUID.class);
        List<UUID> members = jdbcTemplate.query(
                "select user_id from public.household_members where household_id = ? order by joined_at, user_id",
                (memberRow, memberIndex) -> memberRow.getObject("user_id", UUID.class), householdId);
        return new HouseholdResponse(householdId, row.getString("name"),
                row.getObject("owner_user_id", UUID.class), members,
                row.getTimestamp("created_at").toInstant());
    }
}
