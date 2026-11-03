package fairshare_backend.inventory;

import java.time.Instant;
import java.util.UUID;

public record InventoryItemResponse(
        UUID id,
        String name,
        String category,
        String icon,
        int quantity,
        Instant updatedAt) {
}
