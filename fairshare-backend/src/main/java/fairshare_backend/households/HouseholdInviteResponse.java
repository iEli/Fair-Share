package fairshare_backend.households;

import java.time.Instant;
import java.util.UUID;

public record HouseholdInviteResponse(UUID id, UUID householdId, String email,
                                     String code, Instant createdAt, Instant expiresAt) {}
