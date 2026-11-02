package fairshare_backend.households;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
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
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql("/households-schema.sql")
class HouseholdDetailsApiTests {
    @Autowired MockMvc mvc;
    @Autowired HouseholdService households;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean SupabaseAuthClient auth;

    @Test
    void ownerAndMemberCanReadHouseholdDetails() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        var household = households.create("Our Home", owner);
        jdbc.update("insert into household_members (household_id, user_id) values (?, ?)", household.id(), member);
        when(auth.requireUserId("Bearer owner")).thenReturn(owner);
        when(auth.requireUserId("Bearer member")).thenReturn(member);
        for (String token : new String[] { "owner", "member" }) {
            mvc.perform(get("/api/households/{id}", household.id()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(household.id().toString()))
                .andExpect(jsonPath("$.name").value("Our Home"))
                .andExpect(jsonPath("$.ownerUserId").value(owner.toString()))
                .andExpect(jsonPath("$.memberUserIds.length()").value(2))
                .andExpect(jsonPath("$.createdAt").exists());
        }
        assertEquals(2, households.getForUser(household.id(), member).memberUserIds().size());
    }

    @Test
    void missingAndNonMemberHouseholdsReturnNotFound() throws Exception {
        var household = households.create("Private Home", UUID.randomUUID());
        when(auth.requireUserId("Bearer outsider")).thenReturn(UUID.randomUUID());
        for (UUID id : new UUID[] { household.id(), UUID.randomUUID() }) {
            mvc.perform(get("/api/households/{id}", id).header("Authorization", "Bearer outsider"))
                .andExpect(status().isNotFound());
        }
    }

    @Test
    void requiresAuthentication() throws Exception {
        when(auth.requireUserId(null)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        mvc.perform(get("/api/households/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void malformedHouseholdIdReturnsBadRequest() throws Exception {
        mvc.perform(get("/api/households/not-a-uuid").header("Authorization", "Bearer member"))
            .andExpect(status().isBadRequest());
    }
}
