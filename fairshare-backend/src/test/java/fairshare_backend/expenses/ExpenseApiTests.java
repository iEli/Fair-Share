package fairshare_backend.expenses;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.server.ResponseStatusException;

import fairshare_backend.households.SupabaseAuthClient;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql({"/households-schema.sql", "/expenses-schema.sql"})
class ExpenseApiTests {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean SupabaseAuthClient auth;

    private UUID payer;
    private UUID roommate;
    private UUID outsider;
    private String base;

    @BeforeEach
    void setUp() {
        payer = UUID.randomUUID();
        roommate = UUID.randomUUID();
        outsider = UUID.randomUUID();
        jdbc.batchUpdate("insert into auth.users (id) values (?)",
                List.of(new Object[] {payer}, new Object[] {roommate}));
        UUID householdId = createHousehold(payer);
        addMember(householdId, roommate, Instant.parse("2026-01-02T00:00:00Z"));
        base = "/api/households/" + householdId + "/expenses";

        when(auth.requireUserId("Bearer payer")).thenReturn(payer);
        when(auth.requireUserId("Bearer roommate")).thenReturn(roommate);
        when(auth.requireUserId("Bearer outsider")).thenReturn(outsider);
        when(auth.requireUserId(null)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void createsEvenSplitsAndListsWholeExpensesInDateOrder() throws Exception {
        String location = create("""
                {"description":"  Groceries  ","amount":10.01,"spentOn":"2026-01-02"}
                """).andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Groceries"))
                .andExpect(jsonPath("$.payerUserId").value(payer.toString()))
                .andExpect(jsonPath("$.amount").value(10.01))
                .andExpect(jsonPath("$.spentOn").value("2026-01-02"))
                .andExpect(jsonPath("$.outstandingAmount").value(5.00))
                .andReturn().getResponse().getHeader("Location");

        create("""
                {"description":"Paper towels","amount":0.01,"spentOn":"2026-01-01"}
                """).andExpect(status().isCreated()).andExpect(jsonPath("$.outstandingAmount").value(0));
        mvc.perform(get(location).header("Authorization", "Bearer roommate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shares.length()").value(2))
                .andExpect(jsonPath("$.shares[*].amount", containsInAnyOrder(5.01, 5.00)));

        mvc.perform(get(base).param("limit", "1").header("Authorization", "Bearer roommate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].description").value("Groceries"))
                .andExpect(jsonPath("$[0].shares.length()").value(2));
        mvc.perform(get(base).param("offset", "1").header("Authorization", "Bearer roommate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].description").value("Paper towels"));
    }

    @Test
    void rejectsInvalidSplitsBeforeStoringAnExpense() throws Exception {
        List<String> invalid = List.of(
                "{\"description\":\"Groceries\",\"amount\":10.001}",
                "{\"description\":\"Groceries\",\"amount\":0}",
                "{\"description\":\"Groceries\",\"amount\":10,\"shares\":[]}",
                "{\"description\":\"Groceries\",\"amount\":10,\"shares\":[null]}",
                customSplit(outsider, "10.00"),
                customSplit(roommate, "9.99"),
                """
                {"description":"Groceries","amount":10,"shares":[
                   {"userId":"%s","amount":5},{"userId":"%s","amount":5}]}
                """.formatted(roommate, roommate));
        for (String body : invalid) {
            create(body).andExpect(status().isBadRequest());
        }
        mvc.perform(get(base).header("Authorization", "Bearer payer"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void isolatesHouseholdsAndRequiresAuthentication() throws Exception {
        String location = create(customSplit(roommate, "10.00")).andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        UUID otherHousehold = createHousehold(payer);
        String wrongLocation = "/api/households/" + otherHousehold + "/expenses/" + location.substring(base.length() + 1);

        mvc.perform(get(base)).andExpect(status().isUnauthorized());
        mvc.perform(get(base).header("Authorization", "Bearer outsider")).andExpect(status().isNotFound());
        mvc.perform(get(wrongLocation).header("Authorization", "Bearer payer")).andExpect(status().isNotFound());
        mvc.perform(post(base).header("Authorization", "Bearer outsider").contentType("application/json")
                .content(customSplit(roommate, "10.00"))).andExpect(status().isNotFound());
        mvc.perform(get(base).param("limit", "101").header("Authorization", "Bearer payer"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void recordsPartialPaymentsAndDeduplicatesRetries() throws Exception {
        String location = create(customSplit(roommate, "10.00"))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.outstandingAmount").value(10))
                .andReturn().getResponse().getHeader("Location");
        UUID paymentId = UUID.randomUUID();
        pay(location, "roommate", paymentId, "4.00").andExpect(status().isForbidden());
        pay(location, "payer", paymentId, "4.00").andExpect(status().isOk())
                .andExpect(jsonPath("$.outstandingAmount").value(6));
        pay(location, "payer", paymentId, "4.00").andExpect(status().isOk())
                .andExpect(jsonPath("$.outstandingAmount").value(6));
        pay(location, "payer", paymentId, "5.00").andExpect(status().isConflict());
        pay(location, "payer", UUID.randomUUID(), "6.01").andExpect(status().isConflict());
        pay(location, "payer", UUID.randomUUID(), "6.00").andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(10)).andExpect(jsonPath("$.outstandingAmount").value(0));
    }

    @Test
    void concurrentPaymentsCannotOverpay() throws Exception {
        String location = create(customSplit(roommate, "10.00")).andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        var start = new CountDownLatch(1);
        Callable<Integer> paySixDollars = () -> {
            start.await();
            return pay(location, "payer", UUID.randomUUID(), "6.00").andReturn().getResponse().getStatus();
        };
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(paySixDollars);
            var second = executor.submit(paySixDollars);
            start.countDown();
            var statuses = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(List.of(200, 409), statuses.stream().sorted().toList());
        }
        mvc.perform(get(location).header("Authorization", "Bearer payer")).andExpect(status().isOk())
                .andExpect(jsonPath("$.outstandingAmount").value(4));
    }

    private ResultActions create(String body) throws Exception {
        return mvc.perform(post(base).header("Authorization", "Bearer payer")
                .contentType("application/json").content(body));
    }

    private ResultActions pay(String location, String actor, UUID paymentId, String amount) throws Exception {
        return mvc.perform(post(location + "/shares/" + roommate + "/payments")
                .header("Authorization", "Bearer " + actor).contentType("application/json")
                .content("""
                        {"paymentId":"%s","amount":%s}
                        """.formatted(paymentId, amount)));
    }

    private String customSplit(UUID userId, String shareAmount) {
        return """
                {"description":"Groceries","amount":10,"shares":[{"userId":"%s","amount":%s}]}
                """.formatted(userId, shareAmount);
    }

    private UUID createHousehold(UUID owner) {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        jdbc.update("insert into public.households (id, name, owner_user_id, created_at) values (?, ?, ?, ?)",
                id, "Test Home", owner, Timestamp.from(createdAt));
        addMember(id, owner, createdAt);
        return id;
    }

    private void addMember(UUID householdId, UUID userId, Instant joinedAt) {
        jdbc.update("insert into public.household_members (household_id, user_id, joined_at) values (?, ?, ?)",
                householdId, userId, Timestamp.from(joinedAt));
    }
}
