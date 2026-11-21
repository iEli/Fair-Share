package fairshare_backend.expenses;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ExpenseResponse(
        UUID id,
        UUID householdId,
        UUID payerUserId,
        String description,
        BigDecimal amount,
        LocalDate spentOn,
        Instant createdAt,
        BigDecimal outstandingAmount,
        List<Share> shares) {

    public record Share(UUID userId, BigDecimal amount, BigDecimal outstandingAmount) {}
}
