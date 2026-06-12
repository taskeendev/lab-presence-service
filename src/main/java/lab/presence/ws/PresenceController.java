package lab.presence.ws;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/presence")
public class PresenceController {

    private final PresenceRegistry registry;

    public PresenceController(PresenceRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<PresenceRegistry.Snapshot> presence() {
        return registry.snapshot();
    }
}
