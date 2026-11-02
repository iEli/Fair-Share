package fairshare_backend.households;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/households")
public class HouseholdController {

    private final SupabaseAuthClient authClient;
    private final HouseholdService householdService;
    private final HouseholdInviteService inviteService;

    public HouseholdController(SupabaseAuthClient authClient, HouseholdService householdService,
            HouseholdInviteService inviteService) {
        this.authClient = authClient;
        this.householdService = householdService;
        this.inviteService = inviteService;
    }

    @GetMapping
    public List<HouseholdResponse> list(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        UUID userId = authClient.requireUserId(authorization);
        return householdService.listForUser(userId);
    }

    @GetMapping("/{householdId}")
    public HouseholdResponse details(@PathVariable UUID householdId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        UUID userId = authClient.requireUserId(authorization);
        return householdService.getForUser(householdId, userId);
    }

    @PostMapping("/{householdId}/invites")
    @ResponseStatus(HttpStatus.CREATED)
    public HouseholdInviteResponse invite(@PathVariable UUID householdId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody CreateInviteRequest request) {
        UUID userId = authClient.requireUserId(authorization);
        return inviteService.create(householdId, userId, request.email());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HouseholdResponse create(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody(required = false) CreateHouseholdRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Household name is required");
        }
        UUID userId = authClient.requireUserId(authorization);
        return householdService.create(request.name(), userId);
    }
}
