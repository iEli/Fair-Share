package fairshare_backend.households;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql("/households-schema.sql")
class HouseholdInviteApiTests {
    @Autowired MockMvc mvc;
    @Autowired HouseholdService households;
    @Autowired HouseholdInviteService invites;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @MockitoBean SupabaseAuthClient auth;
    UUID owner;
    HouseholdResponse household;

    @BeforeEach
    void setup() {
        owner = UUID.randomUUID();
        household = households.create("Our Home", owner);
        when(auth.requireUserId("Bearer owner")).thenReturn(owner);
    }

    private int inviteCount() {
        return jdbc.queryForObject("select count(*) from household_invites where household_id = ?",
            Integer.class, household.id());
    }

    @Test
    void ownerCreatesNormalizedExpiringInviteWithoutAddingMember() throws Exception {
        var response = mvc.perform(post("/api/households/{id}/invites", household.id())
            .header("Authorization", "Bearer owner").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"  Roommate@Example.COM  \"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.householdId").value(household.id().toString()))
            .andExpect(jsonPath("$.email").value("roommate@example.com"))
            .andExpect(jsonPath("$.token_hash").doesNotExist()).andReturn();
        var body = json.readTree(response.getResponse().getContentAsString());
        String code = body.get("code").asText();
        assertTrue(code.matches("[A-Za-z0-9_-]{43}"));
        assertEquals(Duration.ofDays(7), Duration.between(
            Instant.parse(body.get("createdAt").asText()), Instant.parse(body.get("expiresAt").asText())));
        String expectedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(code.getBytes(StandardCharsets.UTF_8)));
        String storedHash = jdbc.queryForObject("select token_hash from household_invites where household_id = ?",
            String.class, household.id());
        assertEquals(expectedHash, storedHash);
        assertNotEquals(code, storedHash);
        assertEquals(owner, jdbc.queryForObject("select invited_by from household_invites where household_id = ?",
            UUID.class, household.id()));
        assertEquals(1, inviteCount());
        assertEquals(1, households.getForUser(household.id(), owner).memberUserIds().size());
    }

    @Test
    void rejectsActiveDuplicateEmailsWithoutChangingCode() throws Exception {
        var original = invites.create(household.id(), owner, "roommate@example.com");
        String stored = jdbc.queryForObject("select token_hash from household_invites where id = ?", String.class, original.id());
        mvc.perform(post("/api/households/{id}/invites", household.id()).header("Authorization", "Bearer owner")
            .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"ROOMMATE@example.com\"}"))
            .andExpect(status().isConflict());
        assertEquals(1, inviteCount());
        assertEquals(stored, jdbc.queryForObject("select token_hash from household_invites where id = ?", String.class, original.id()));
    }

    @Test
    void reissuingExpiredInviteRotatesCode() {
        var original = invites.create(household.id(), owner, "roommate@example.com");
        jdbc.update("update household_invites set created_at = ?, expires_at = ? where id = ?",
            Timestamp.from(Instant.now().minus(Duration.ofDays(10))),
            Timestamp.from(Instant.now().minus(Duration.ofDays(1))), original.id());
        var replacement = invites.create(household.id(), owner, "roommate@example.com");
        assertEquals(original.id(), replacement.id());
        assertNotEquals(original.code(), replacement.code());
        assertTrue(replacement.expiresAt().isAfter(Instant.now()));
        assertEquals(1, inviteCount());
    }

    @Test
    void ordinaryMembersCannotInvite() throws Exception {
        UUID member = UUID.randomUUID();
        jdbc.update("insert into household_members (household_id, user_id) values (?, ?)", household.id(), member);
        when(auth.requireUserId("Bearer member")).thenReturn(member);
        mvc.perform(post("/api/households/{id}/invites", household.id()).header("Authorization", "Bearer member")
            .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"roommate@example.com\"}"))
            .andExpect(status().isForbidden());
        assertEquals(0, inviteCount());
    }

    @Test
    void outsidersAndMissingHouseholdsReturnNotFound() throws Exception {
        when(auth.requireUserId("Bearer outsider")).thenReturn(UUID.randomUUID());
        for (UUID id : new UUID[] { household.id(), UUID.randomUUID() }) {
            mvc.perform(post("/api/households/{id}/invites", id).header("Authorization", "Bearer outsider")
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"roommate@example.com\"}"))
                .andExpect(status().isNotFound());
        }
        assertEquals(0, inviteCount());
    }

    @Test
    void ownerWithoutMembershipCannotInvite() throws Exception {
        jdbc.update("delete from household_members where household_id = ? and user_id = ?", household.id(), owner);
        mvc.perform(post("/api/households/{id}/invites", household.id()).header("Authorization", "Bearer owner")
            .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"roommate@example.com\"}"))
            .andExpect(status().isNotFound());
        assertEquals(0, inviteCount());
    }

    @Test
    void missingOrInvalidTokenIsRejected() throws Exception {
        when(auth.requireUserId(null)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        when(auth.requireUserId("Bearer invalid")).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        mvc.perform(post("/api/households/{id}/invites", household.id()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"roommate@example.com\"}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/households/{id}/invites", household.id()).header("Authorization", "Bearer invalid")
            .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"roommate@example.com\"}"))
            .andExpect(status().isUnauthorized());
        assertEquals(0, inviteCount());
    }

    @Test
    void invalidEmailBodiesAreRejected() throws Exception {
        for (String body : new String[] { "{}", "null", "{\"email\":\"\"}", "{\"email\":\"not-an-email\"}",
                "{\"email\":\"" + "a".repeat(250) + "@example.com\"}" }) {
            mvc.perform(post("/api/households/{id}/invites", household.id()).header("Authorization", "Bearer owner")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/households/{id}/invites", household.id()).header("Authorization", "Bearer owner")
            .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
        assertEquals(0, inviteCount());
    }

    @Test
    void malformedHouseholdIdIsRejected() throws Exception {
        mvc.perform(post("/api/households/not-a-uuid/invites").header("Authorization", "Bearer owner")
            .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"roommate@example.com\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void simultaneousDuplicateInvitesCreateOneRecord() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> create = () -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                try {
                    invites.create(household.id(), owner, "roommate@example.com");
                    return 201;
                } catch (ResponseStatusException exception) {
                    return exception.getStatusCode().value();
                }
            };
            var first = executor.submit(create);
            var second = executor.submit(create);
            start.countDown();
            var statuses = new java.util.TreeSet<>(java.util.List.of(
                first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)));
            assertEquals(new java.util.TreeSet<>(java.util.List.of(201, 409)), statuses);
            assertEquals(1, inviteCount());
        }
    }
}
