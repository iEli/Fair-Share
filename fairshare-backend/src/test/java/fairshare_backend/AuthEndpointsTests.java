package fairshare_backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AuthEndpointsTests.MockSupabase.class)
class AuthEndpointsTests {
    @Autowired MockMvc mvc;
    @Autowired MockRestServiceServer server;

    private static final String BASE = "https://test-project.supabase.co/auth/v1";
    private static final String USER = """
        {"id":"a7465d26-1c7b-42d3-a59c-92c57f1fc045","email":"test@example.com",
         "user_metadata":{"role":"admin"}}
        """;
    private static final String SESSION = """
        {"access_token":"access-token","refresh_token":"new-refresh-token",
         "token_type":"bearer","expires_in":3600,"user":%s}
        """.formatted(USER);
    private static final String CREDENTIALS = """
        {"email":"test@example.com","password":"a strong password"}
        """;

    @TestConfiguration
    static class MockSupabase {
        private final RestClient.Builder builder = RestClient.builder().baseUrl(BASE)
            .defaultHeader("apikey", "sb_publishable_test");
        @Bean MockRestServiceServer server() { return MockRestServiceServer.bindTo(builder).build(); }
        @Bean @Primary RestClient mockSupabaseClient(MockRestServiceServer server) { return builder.build(); }
    }

    @BeforeEach void reset() { server.reset(); }
    @AfterEach void verify() { server.verify(); }

    private void expectPost(String path, String body, String response) {
        server.expect(requestTo(BASE + path)).andExpect(method(HttpMethod.POST))
            .andExpect(header("apikey", "sb_publishable_test"))
            .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content().json(body)).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }
    private void expectUser() {
        server.expect(requestTo(BASE + "/user")).andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer access-token"))
            .andExpect(header("apikey", "sb_publishable_test"))
            .andRespond(withSuccess(USER, MediaType.APPLICATION_JSON));
    }

    @Test void signupSupportsEmailConfirmationWithoutSession() throws Exception {
        expectPost("/signup", CREDENTIALS, USER);
        var result = mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
            .content(CREDENTIALS.replace("test@example.com", " TEST@Example.COM ")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.user.id").value("a7465d26-1c7b-42d3-a59c-92c57f1fc045"))
            .andExpect(jsonPath("$.user.email").value("test@example.com"))
            .andExpect(jsonPath("$.access_token").isEmpty())
            .andExpect(jsonPath("$.user.user_metadata").doesNotExist()).andReturn();
        assertNull(result.getRequest().getSession(false));
    }

    @Test void signupPreservesNamesAsDisplayMetadata() throws Exception {
        expectPost("/signup", """
            {"email":"test@example.com","password":"a strong password",
             "data":{"first_name":"Taylor","last_name":"Rivera"}}
            """, USER);
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
            {"email":"test@example.com","password":"a strong password",
             "firstName":" Taylor ","lastName":"Rivera"}
            """))
            .andExpect(status().isCreated());
    }

    @Test void signupSupportsImmediateSessionWhenConfirmationDisabled() throws Exception {
        expectPost("/signup", CREDENTIALS, SESSION);
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(CREDENTIALS))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.access_token").value("access-token"));
    }

    @Test void loginReturnsSupabaseTokensWithoutCreatingLocalSession() throws Exception {
        expectPost("/token?grant_type=password", CREDENTIALS, SESSION);
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(CREDENTIALS))
            .andExpect(status().isOk()).andExpect(jsonPath("$.access_token").value("access-token"))
            .andExpect(jsonPath("$.refresh_token").value("new-refresh-token"))
            .andExpect(jsonPath("$.expires_in").value(3600))
            .andExpect(jsonPath("$.password").doesNotExist()).andReturn();
        assertNull(result.getRequest().getSession(false));
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test void refreshReturnsRotatedTokens() throws Exception {
        expectPost("/token?grant_type=refresh_token", "{\"refresh_token\":\"old-refresh-token\"}", SESSION);
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
            .content("{\"refresh_token\":\"old-refresh-token\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.refresh_token").value("new-refresh-token"));
    }

    @Test void bearerTokenIsValidatedBySupabase() throws Exception {
        expectUser();
        var result = mvc.perform(get("/api/auth/me").header("Authorization", "Bearer access-token"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("test@example.com"))
            .andExpect(jsonPath("$.id").value("a7465d26-1c7b-42d3-a59c-92c57f1fc045"))
            .andExpect(jsonPath("$.role").doesNotExist()).andReturn();
        assertNull(result.getRequest().getSession(false));
    }

    @Test void invalidTokenIsRejected() throws Exception {
        server.expect(requestTo(BASE + "/user"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"msg\":\"invalid token\"}"));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer forged-token"))
            .andExpect(status().isUnauthorized());
    }

    @Test void missingTokenIsRejected() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
    }

    @Test void logoutRevokesSupabaseSession() throws Exception {
        server.expect(requestTo(BASE + "/logout?scope=local")).andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer access-token"))
            .andRespond(withNoContent());
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer access-token"))
            .andExpect(status().isNoContent());
    }

    @Test void invalidInputsNeverReachSupabase() throws Exception {
        for (String body : new String[] { "{}", "null", "{\"email\":\"bad\",\"password\":\"valid\"}",
            "{\"email\":\"test@example.com\",\"password\":\" \"}" }) {
            mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test void incorrectCredentialsAreSanitized() throws Exception {
        server.expect(requestTo(BASE + "/token?grant_type=password"))
            .andRespond(withBadRequest().body("{\"error_code\":\"invalid_credentials\",\"msg\":\"private upstream detail\"}"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(CREDENTIALS))
            .andExpect(status().isUnauthorized()).andExpect(content().string(""));
    }

    @Test void signupRejectionIsHandled() throws Exception {
        server.expect(requestTo(BASE + "/signup")).andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT));
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(CREDENTIALS))
            .andExpect(status().isBadRequest());
    }

    @Test void supabaseRateLimitsArePreserved() throws Exception {
        server.expect(requestTo(BASE + "/signup")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(CREDENTIALS))
            .andExpect(status().isTooManyRequests());
    }

    @Test void upstreamOutageIsHandled() throws Exception {
        server.expect(requestTo(BASE + "/token?grant_type=password")).andRespond(withServerError());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(CREDENTIALS))
            .andExpect(status().isServiceUnavailable());
    }

    @Test void networkFailureIsHandled() throws Exception {
        server.expect(requestTo(BASE + "/signup")).andRespond(withException(new IOException("connection failed")));
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(CREDENTIALS))
            .andExpect(status().isServiceUnavailable());
    }

    @Test void malformedUpstreamUserIsRejected() throws Exception {
        server.expect(requestTo(BASE + "/user"))
            .andRespond(withSuccess("{\"id\":\"not-a-uuid\"}", MediaType.APPLICATION_JSON));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer access-token"))
            .andExpect(status().isBadGateway());
    }
}
