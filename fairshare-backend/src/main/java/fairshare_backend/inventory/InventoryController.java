package fairshare_backend.inventory;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import fairshare_backend.households.SupabaseAuthClient;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/households/{householdId}/inventory")
public class InventoryController {

    private final SupabaseAuthClient authClient;
    private final InventoryService inventoryService;

    public InventoryController(SupabaseAuthClient authClient, InventoryService inventoryService) {
        this.authClient = authClient;
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public List<InventoryItemResponse> list(@PathVariable UUID householdId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        UUID userId = authClient.requireUserId(authorization);
        return inventoryService.list(householdId, userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryItemResponse create(@PathVariable UUID householdId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody CreateInventoryItemRequest request) {
        UUID userId = authClient.requireUserId(authorization);
        return inventoryService.create(householdId, userId, request);
    }

    @PatchMapping("/{itemId}/quantity")
    public InventoryItemResponse adjust(@PathVariable UUID householdId, @PathVariable UUID itemId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody AdjustInventoryRequest request) {
        UUID userId = authClient.requireUserId(authorization);
        return inventoryService.adjust(householdId, itemId, userId, request.delta());
    }
}
