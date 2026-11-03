package fairshare_backend.inventory;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateInventoryItemRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 60) String category,
        @Size(max = 16) String icon,
        @NotNull @Min(0) @Max(1_000_000) Integer quantity) {
}
