package fairshare_backend.inventory;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import fairshare_backend.households.SupabaseAuthClient;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql("/inventory-schema.sql")
class InventoryApiTests {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @MockitoBean SupabaseAuthClient auth;

    private UUID userId;
    private UUID householdId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        householdId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("insert into public.households (id, name, owner_user_id, created_at) values (?, ?, ?, ?)",
                householdId, "API Home", userId, now);
        jdbc.update("insert into public.household_members (household_id, user_id) values (?, ?)",
                householdId, userId);
        when(auth.requireUserId("Bearer test-token")).thenReturn(userId);
    }

    @Test
    void createsAndAdjustsInventoryItems() throws Exception {
        String base = "/api/households/" + householdId + "/inventory";
        String itemId = mvc.perform(post(base)
                .header("Authorization", "Bearer test-token")
                .contentType("application/json")
                .content("{\"name\":\"Milk\",\"category\":\"Grocery\",\"icon\":\"🥛\",\"quantity\":2}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Milk"))
            .andExpect(jsonPath("$.quantity").value(2))
            .andReturn().getResponse().getContentAsString();
        UUID createdId = UUID.fromString(json.readTree(itemId).get("id").asText());

        mvc.perform(patch(base + "/" + createdId + "/quantity")
                .header("Authorization", "Bearer test-token")
                .contentType("application/json")
                .content("{\"delta\":1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quantity").value(3));

        mvc.perform(get(base).header("Authorization", "Bearer test-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Milk"))
            .andExpect(jsonPath("$[0].quantity").value(3));
    }

    @Test
    void rejectsInvalidAdjustmentsAndNonMemberAccess() throws Exception {
        String base = "/api/households/" + householdId + "/inventory";
        UUID itemId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                insert into public.inventory_items
                    (id, household_id, name, category, icon, quantity, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, itemId, householdId, "Eggs", "Grocery", "🥚", 0, now, now);

        mvc.perform(patch(base + "/" + itemId + "/quantity")
                .header("Authorization", "Bearer test-token")
                .contentType("application/json")
                .content("{\"delta\":0}"))
            .andExpect(status().isBadRequest());

        when(auth.requireUserId("Bearer stranger-token")).thenReturn(UUID.randomUUID());
        mvc.perform(get(base).header("Authorization", "Bearer stranger-token"))
            .andExpect(status().isNotFound());
    }
}
