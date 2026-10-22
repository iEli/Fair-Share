package fairshare_backend.households;

import java.util.Locale;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateInviteRequest(@NotBlank @Email @Size(max = 254) String email) {
    public CreateInviteRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
