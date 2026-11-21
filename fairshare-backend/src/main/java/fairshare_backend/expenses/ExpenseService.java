package fairshare_backend.expenses;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import fairshare_backend.households.HouseholdService;

@Service
public class ExpenseService {

    private static final String WITH_SHARES = """
            select e.*, s.user_id, s.amount_cents as share_cents,
                case when s.user_id = e.payer_user_id then 0
                    else s.amount_cents - coalesce((
                        select sum(p.amount_cents) from public.expense_payments p
                        where p.expense_id = s.expense_id and p.user_id = s.user_id
                    ), 0)
                end as outstanding_cents
            from selected_expenses e
            join public.expense_shares s on s.expense_id = e.id
            order by e.spent_on desc, e.created_at desc, e.id desc, s.user_id
            """;

    private final JdbcTemplate jdbc;
    private final HouseholdService households;

    public ExpenseService(JdbcTemplate jdbc, HouseholdService households) {
        this.jdbc = jdbc;
        this.households = households;
    }

    @Transactional
    public ExpenseResponse create(UUID householdId, UUID payerId, CreateExpenseRequest request) {
        var household = households.getForUser(householdId, payerId);
        String description = request.description().strip();
        if (description.isBlank() || description.indexOf('\0') >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid expense description");
        }
        Instant now = Instant.now();
        LocalDate spentOn = request.spentOn() == null ? LocalDate.ofInstant(now, ZoneOffset.UTC) : request.spentOn();
        if (spentOn.getYear() < 1 || spentOn.getYear() > 9999) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expense date must have a year between 1 and 9999");
        }

        long totalCents = toCents(request.amount());
        var shares = split(totalCents, household.memberUserIds(), request.shares());
        UUID expenseId = UUID.randomUUID();
        jdbc.update("""
                insert into public.expenses
                    (id, household_id, payer_user_id, description, amount_cents, spent_on, created_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """, expenseId, householdId, payerId, description, totalCents, spentOn, Timestamp.from(now));
        jdbc.batchUpdate("""
                insert into public.expense_shares (expense_id, user_id, amount_cents)
                values (?, ?, ?)
                """, shares.entrySet().stream().map(share ->
                new Object[] {expenseId, share.getKey(), share.getValue()}).toList());
        return find(householdId, expenseId);
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> list(UUID householdId, UUID userId, int limit, int offset) {
        households.getForUser(householdId, userId);
        if (limit < 1 || limit > 100 || offset < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit must be 1 to 100 and offset must be nonnegative");
        }
        return jdbc.query("""
                with selected_expenses as (
                    select * from public.expenses
                    where household_id = ?
                    order by spent_on desc, created_at desc, id desc
                    limit ? offset ?
                )
                """ + WITH_SHARES, this::readExpenses, householdId, limit, offset);
    }

    @Transactional(readOnly = true)
    public ExpenseResponse get(UUID householdId, UUID userId, UUID expenseId) {
        households.getForUser(householdId, userId);
        return find(householdId, expenseId);
    }

    @Transactional
    public ExpenseResponse recordPayment(UUID householdId, UUID userId, UUID expenseId, UUID memberId,
            RecordExpensePaymentRequest request) {
        households.getForUser(householdId, userId);
        // Serialize payment validation and insertion for the same expense.
        List<UUID> payers = jdbc.queryForList("""
                select payer_user_id from public.expenses
                where household_id = ? and id = ?
                for update
                """, UUID.class, householdId, expenseId);
        if (payers.isEmpty()) {
            throw expenseNotFound();
        }
        if (!payers.getFirst().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the expense payer can record payments");
        }
        if (userId.equals(memberId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The payer's own share is already covered");
        }

        ExpenseResponse expense = find(householdId, expenseId);
        ExpenseResponse.Share share = expense.shares().stream()
                .filter(candidate -> candidate.userId().equals(memberId))
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense share not found"));
        long amountCents = toCents(request.amount());
        List<Long> previousPayment = jdbc.queryForList("""
                select amount_cents from public.expense_payments
                where expense_id = ? and user_id = ? and id = ?
                """, Long.class, expenseId, memberId, request.paymentId());
        if (!previousPayment.isEmpty()) {
            if (previousPayment.getFirst() != amountCents) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment ID already used with a different amount");
            }
            return expense;
        }
        if (amountCents > toCents(share.outstandingAmount())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment exceeds the outstanding share");
        }
        jdbc.update("""
                insert into public.expense_payments (expense_id, user_id, id, amount_cents)
                values (?, ?, ?, ?)
                """, expenseId, memberId, request.paymentId(), amountCents);
        return find(householdId, expenseId);
    }

    private Map<UUID, Long> split(long totalCents, List<UUID> memberIds, List<CreateExpenseRequest.Share> custom) {
        Map<UUID, Long> shares = new LinkedHashMap<>();
        if (custom == null) {
            long base = totalCents / memberIds.size();
            long remainder = totalCents % memberIds.size();
            for (int index = 0; index < memberIds.size(); index++) {
                shares.put(memberIds.get(index), base + (index < remainder ? 1 : 0));
            }
            return shares;
        }

        var members = new HashSet<>(memberIds);
        long unallocated = totalCents;
        for (var share : custom) {
            if (!members.contains(share.userId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shares must belong to household members");
            }
            long shareCents = toCents(share.amount());
            if (shares.putIfAbsent(share.userId(), shareCents) != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A member may only have one share");
            }
            unallocated -= shareCents;
            if (unallocated < 0) {
                break;
            }
        }
        if (unallocated != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shares must add up to the expense amount");
        }
        return shares;
    }

    private ExpenseResponse find(UUID householdId, UUID expenseId) {
        List<ExpenseResponse> results = jdbc.query("""
                with selected_expenses as (
                    select * from public.expenses where household_id = ? and id = ?
                )
                """ + WITH_SHARES, this::readExpenses, householdId, expenseId);
        if (results.isEmpty()) {
            throw expenseNotFound();
        }
        return results.getFirst();
    }

    private List<ExpenseResponse> readExpenses(ResultSet rows) throws SQLException {
        Map<Expense, List<ExpenseResponse.Share>> expenses = new LinkedHashMap<>();
        while (rows.next()) {
            var expense = new Expense(rows.getObject("id", UUID.class), rows.getObject("household_id", UUID.class),
                    rows.getObject("payer_user_id", UUID.class), rows.getString("description"),
                    amount(rows.getLong("amount_cents")), rows.getObject("spent_on", LocalDate.class),
                    rows.getTimestamp("created_at").toInstant());
            var share = new ExpenseResponse.Share(rows.getObject("user_id", UUID.class),
                    amount(rows.getLong("share_cents")), amount(rows.getLong("outstanding_cents")));
            expenses.computeIfAbsent(expense, key -> new ArrayList<>()).add(share);
        }
        return expenses.entrySet().stream()
                .map(entry -> entry.getKey().withShares(entry.getValue()))
                .toList();
    }

    private static long toCents(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }

    private static BigDecimal amount(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }

    private static ResponseStatusException expenseNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found");
    }

    private record Expense(UUID id, UUID householdId, UUID payerUserId, String description, BigDecimal amount,
            LocalDate spentOn, Instant createdAt) {

        ExpenseResponse withShares(List<ExpenseResponse.Share> shares) {
            BigDecimal outstanding = shares.stream().map(ExpenseResponse.Share::outstandingAmount)
                    .reduce(ExpenseService.amount(0), BigDecimal::add);
            return new ExpenseResponse(id, householdId, payerUserId, description, amount, spentOn, createdAt,
                    outstanding, List.copyOf(shares));
        }
    }
}
