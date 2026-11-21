package fairshare_backend.expenses;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateExpenseRequest(
        @NotBlank @Size(max = 200) String description,
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal amount,
        LocalDate spentOn,
        @Size(min = 1) List<@NotNull @Valid Share> shares) {

    public record Share(
            @NotNull UUID userId,
            @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal amount) {}
}
