package team.boerse.tauschboerse.studiengang;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import team.boerse.tauschboerse.User;
import team.boerse.tauschboerse.UserUtil;

@RestController
@RequestMapping("/api/admin/studiengaenge")
@RequiredArgsConstructor
public class StudiengangAdminController {
    private final StudiengangService service;
    private final team.boerse.tauschboerse.suggestions.GroupSuggestionService suggestionService;

    private void requireAdmin() {
        User u = UserUtil.getUser();
        if (u == null || !Boolean.TRUE.equals(u.getIsAdmin())) {
            throw new RuntimeException("Access denied");
        }
    }

    @GetMapping
    public List<Studiengang> list() {
        requireAdmin();
        return service.findAll();
    }

    public record UpsertRequest(String name, String shortCode) {
    }

    @PostMapping
    public ResponseEntity<Studiengang> create(@RequestBody UpsertRequest req) {
        requireAdmin();
        if (req == null || req.name() == null || req.name().isBlank() || req.shortCode() == null
                || req.shortCode().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Studiengang s = service.create(req.name().trim(), req.shortCode().trim());
        try {
            suggestionService.rebuildCache();
        } catch (Exception ignore) {
        }
        return ResponseEntity.ok(s);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Studiengang> update(@PathVariable Long id, @RequestBody UpsertRequest req) {
        requireAdmin();
        Studiengang s = service.update(id, req != null ? req.name() : null, req != null ? req.shortCode() : null);
        try {
            suggestionService.rebuildCache();
        } catch (Exception ignore) {
        }
        return ResponseEntity.ok(s);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        requireAdmin();
        service.delete(id);
        try {
            suggestionService.rebuildCache();
        } catch (Exception ignore) {
        }
        return ResponseEntity.ok(Map.of("deleted", id));
    }
}
