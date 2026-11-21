package fairshare_backend.expenses;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import fairshare_backend.households.SupabaseAuthClient;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/households/{householdId}/expenses")
public class ExpenseController {

    private final SupabaseAuthClient authClient;
    private final ExpenseService expenses;

    public ExpenseController(SupabaseAuthClient authClient, ExpenseService expenses) {
        this.authClient = authClient;
        this.expenses = expenses;
    }

    @PostMapping
    public ResponseEntity<ExpenseResponse> create(@PathVariable UUID householdId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody CreateExpenseRequest request) {
        ExpenseResponse expense = expenses.create(householdId, authClient.requireUserId(authorization), request);
        URI location = URI.create("/api/households/" + householdId + "/expenses/" + expense.id());
        return ResponseEntity.created(location).body(expense);
    }

    @GetMapping
    public List<ExpenseResponse> list(@PathVariable UUID householdId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return expenses.list(householdId, authClient.requireUserId(authorization), limit, offset);
    }

    @GetMapping("/{expenseId}")
    public ExpenseResponse get(@PathVariable UUID householdId, @PathVariable UUID expenseId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return expenses.get(householdId, authClient.requireUserId(authorization), expenseId);
    }

    @PostMapping("/{expenseId}/shares/{memberId}/payments")
    public ExpenseResponse recordPayment(@PathVariable UUID householdId, @PathVariable UUID expenseId,
            @PathVariable UUID memberId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody RecordExpensePaymentRequest request) {
        return expenses.recordPayment(householdId, authClient.requireUserId(authorization), expenseId, memberId, request);
    }
}
