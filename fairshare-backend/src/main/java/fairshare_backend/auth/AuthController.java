package fairshare_backend.auth;

import java.util.Locale;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    AuthService.AuthResponse signup(@Valid @RequestBody SignupRequest body) {
        return auth.signup(body.email(), body.password(), body.firstName(), body.lastName());
    }

    @PostMapping("/login")
    AuthService.AuthResponse login(@Valid @RequestBody Credentials body) {
        return auth.login(body.email(), body.password());
    }

    @PostMapping("/refresh")
    AuthService.AuthResponse refresh(@Valid @RequestBody RefreshRequest body) {
        return auth.refresh(body.refresh_token());
    }

    @GetMapping("/me")
    AuthService.UserResponse me(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return auth.user(accessToken(authorization));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        auth.logout(accessToken(authorization));
    }

    private String accessToken(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                || authorization.substring(7).isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Supabase access token required");
        }
        return authorization.substring(7);
    }

    // Supabase owns password policy, including validation of existing users' passwords.
    public record Credentials(@NotBlank @Email @Size(max = 254) String email,
                              @NotBlank @Size(max = 4096) String password) {
        public Credentials { email = email == null ? null : email.strip().toLowerCase(Locale.ROOT); }
    }
    public record SignupRequest(@NotBlank @Email @Size(max = 254) String email,
                                @NotBlank @Size(max = 4096) String password,
                                @Size(max = 100) String firstName, @Size(max = 100) String lastName) {
        public SignupRequest {
            email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
            firstName = firstName == null ? "" : firstName.strip();
            lastName = lastName == null ? "" : lastName.strip();
        }
    }
    public record RefreshRequest(@NotBlank @Size(max = 8192) String refresh_token) {}
}
