package team.boerse.tauschboerse.studiengang;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import team.boerse.tauschboerse.User;
import team.boerse.tauschboerse.UserUtil;

@RestController
@RequestMapping("/api/admin/notshared")
@RequiredArgsConstructor
public class NotSharedAdminController {
    private final NotSharedService service;
    private final NotSharedVeranstaltungRepository notSharedRepository;
    private final team.boerse.tauschboerse.suggestions.GroupSuggestionService suggestionService;

    private void requireAdmin() {
        User u = UserUtil.getUser();
        if (u == null || !Boolean.TRUE.equals(u.getIsAdmin())) {
            throw new RuntimeException("Access denied");
        }
    }

    public record NotSharedItem(Long id, String baseName) {
    }

    @GetMapping("/{studiengangId}")
    public ResponseEntity<List<NotSharedItem>> list(@PathVariable Long studiengangId) {
        requireAdmin();
        List<NotSharedVeranstaltung> list = notSharedRepository.findByStudiengang_Id(studiengangId);
        List<NotSharedItem> items = list.stream()
                .filter(e -> e != null && e.getId() != null)
                .map(e -> new NotSharedItem(e.getId(), e.getVeranstaltungBaseName()))
                .toList();
        return ResponseEntity.ok(items);
    }

    public record AddRequest(Long studiengangId, String baseName) {
    }

    @PostMapping
    public ResponseEntity<?> add(@RequestBody AddRequest req) {
        requireAdmin();
        if (req == null || req.studiengangId() == null || req.baseName() == null || req.baseName().isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "studiengangId/baseName required"));
        NotSharedVeranstaltung e = service.addNotShared(req.studiengangId(), req.baseName());
        try {
            suggestionService.invalidateCacheForStudiengang(req.studiengangId());
        } catch (Exception ignore) {
        }
        return ResponseEntity.ok(Map.of("id", e != null ? e.getId() : null));
    }

    @GetMapping("/{studiengangId}/available-kurzel")
    public ResponseEntity<Map<String, Object>> getAvailableKurzel(@PathVariable Long studiengangId) {
        requireAdmin();
        Map<String, Object> result = service.getKurzelAnalysis(studiengangId);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        requireAdmin();
        NotSharedVeranstaltung toDelete = notSharedRepository.findById(id).orElse(null);
        Long studiengangId = toDelete != null && toDelete.getStudiengang() != null
                ? toDelete.getStudiengang().getId()
                : null;

        service.removeNotShared(id);
        try {
            if (studiengangId != null) {
                suggestionService.invalidateCacheForStudiengang(studiengangId);
            }
        } catch (Exception ignore) {
        }
        return ResponseEntity.ok(Map.of("deleted", id));
    }
}
