package fairshare_backend.inventory;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AdjustInventoryRequest(@NotNull @Min(-1) @Max(1) Integer delta) {
}
