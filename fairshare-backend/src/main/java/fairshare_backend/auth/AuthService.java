package fairshare_backend.auth;

import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@Service
public class AuthService {
    private final RestClient client;

    public AuthService(RestClient supabaseAuthRestClient) { this.client = supabaseAuthRestClient; }

    AuthResponse signup(String email, String password, String firstName, String lastName) {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);
        if (!firstName.isBlank() || !lastName.isBlank()) {
            body.put("data", Map.of("first_name", firstName, "last_name", lastName));
        }
        return authResponse(post("/signup", body, false));
    }

    AuthResponse login(String email, String password) {
        return authResponse(post("/token?grant_type=password", Map.of("email", email, "password", password), true));
    }

    AuthResponse refresh(String refreshToken) {
        return authResponse(post("/token?grant_type=refresh_token", Map.of("refresh_token", refreshToken), true));
    }

    UserResponse user(String accessToken) {
        try {
            return userResponse(client.get().uri("/user").headers(h -> h.setBearerAuth(accessToken))
                .retrieve().body(JsonNode.class));
        } catch (RestClientResponseException exception) {
            throw upstreamError(exception, true);
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    void logout(String accessToken) {
        try {
            client.post().uri("/logout?scope=local").headers(h -> h.setBearerAuth(accessToken))
                .retrieve().toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw upstreamError(exception, true);
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    private JsonNode post(String path, Map<String, ?> body, boolean authenticating) {
        try {
            return client.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw upstreamError(exception, authenticating);
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    private AuthResponse authResponse(JsonNode body) {
        if (body == null) throw malformed();
        var user = userResponse(body.has("user") ? body.get("user") : body);
        return new AuthResponse(user, string(body, "access_token"), string(body, "refresh_token"),
            string(body, "token_type"), body.hasNonNull("expires_in") ? body.get("expires_in").asLong() : null,
            body.hasNonNull("expires_at") ? body.get("expires_at").asLong() : null);
    }

    private UserResponse userResponse(JsonNode body) {
        if (body == null || !body.hasNonNull("id")) throw malformed();
        try {
            return new UserResponse(UUID.fromString(body.get("id").asText()), string(body, "email"));
        } catch (IllegalArgumentException exception) {
            throw malformed();
        }
    }

    private static String string(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private ResponseStatusException upstreamError(RestClientResponseException exception, boolean authenticating) {
        int status = exception.getStatusCode().value();
        if (status == 429) return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many authentication requests; try again later");
        if (status >= 500) return unavailable();
        // Do not leak upstream bodies, credentials, or account existence details.
        if (status == 401 || (authenticating && (status == 400 || status == 422))) {
            return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication failed");
        }
        if (status == 403) return new ResponseStatusException(HttpStatus.FORBIDDEN, "Authentication is not permitted");
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supabase rejected the authentication request");
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Authentication service unavailable");
    }

    private ResponseStatusException malformed() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid authentication service response");
    }

    public record UserResponse(UUID id, String email) {}
    public record AuthResponse(UserResponse user, String access_token, String refresh_token,
                               String token_type, Long expires_in, Long expires_at) {}
}
