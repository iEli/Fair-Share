package fairshare_backend.households;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HouseholdInviteService {
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    public HouseholdInviteService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public HouseholdInviteResponse create(UUID householdId, UUID userId, String email) {
        // Lock the household to serialize invite creation and owner changes.
        var owners = jdbc.query("""
                select h.owner_user_id from public.households h
                where h.id = ? and exists (
                    select 1 from public.household_members m
                    where m.household_id = h.id and m.user_id = ?
                ) for update
                """, (row, index) -> row.getObject("owner_user_id", UUID.class), householdId, userId);
        if (owners.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Household not found");
        }
        if (!owners.getFirst().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the household owner can invite roommates");
        }

        Instant now = Instant.now();
        var previous = jdbc.query("""
                select id, expires_at, accepted_at from public.household_invites
                where household_id = ? and email = ?
                """, (row, index) -> new PreviousInvite(row.getObject("id", UUID.class),
                    row.getTimestamp("expires_at").toInstant(), row.getTimestamp("accepted_at") != null),
                householdId, email);
        if (!previous.isEmpty() && !previous.getFirst().accepted()
                && previous.getFirst().expiresAt().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An active invitation already exists for this email");
        }

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = hash(code);
        Instant expiresAt = now.plus(Duration.ofDays(7));
        UUID id = previous.isEmpty() ? UUID.randomUUID() : previous.getFirst().id();
        if (previous.isEmpty()) {
            jdbc.update("""
                    insert into public.household_invites
                        (id, household_id, email, invited_by, token_hash, created_at, expires_at)
                    values (?, ?, ?, ?, ?, ?, ?)
                    """, id, householdId, email, userId, hash, Timestamp.from(now), Timestamp.from(expiresAt));
        } else {
            // Reissuing an expired/completed invite invalidates its previous code.
            jdbc.update("""
                    update public.household_invites
                    set invited_by = ?, token_hash = ?, created_at = ?, expires_at = ?, accepted_at = null
                    where id = ?
                    """, userId, hash, Timestamp.from(now), Timestamp.from(expiresAt), id);
        }
        return new HouseholdInviteResponse(id, householdId, email, code, now, expiresAt);
    }

    private String hash(String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private record PreviousInvite(UUID id, Instant expiresAt, boolean accepted) {}
}
