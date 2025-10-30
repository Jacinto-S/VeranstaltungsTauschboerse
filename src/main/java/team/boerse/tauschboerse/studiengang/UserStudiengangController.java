package team.boerse.tauschboerse.studiengang;

import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import team.boerse.tauschboerse.*;
import team.boerse.tauschboerse.audit.AuditEventType;
import team.boerse.tauschboerse.audit.AuditService;

@RestController
@RequestMapping("/api/user/studiengang")
@RequiredArgsConstructor
public class UserStudiengangController {
    private final UserRepository userRepository;
    private final StudiengangRepository studiengangRepository;
    private final TauschTerminRepository tauschTerminRepository;
    private final team.boerse.tauschboerse.suggestions.GroupSuggestionService groupSuggestionService;
    private final AuditService auditService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getMyStudiengang() {
        User u = UserUtil.getUser();
        if (u == null)
            return ResponseEntity.status(401).build();
        Long id = u.getStudiengang() != null ? u.getStudiengang().getId() : null;
        String name = u.getStudiengang() != null ? u.getStudiengang().getName() : null;
        String code = u.getStudiengang() != null ? u.getStudiengang().getShortCode() : null;
        java.util.Map<String, Object> out = new java.util.HashMap<>();
        out.put("id", id);
        out.put("name", name);
        out.put("shortCode", code);
        return ResponseEntity.ok(out);
    }

    public record SetRequest(Long studiengangId) {
    }

    @PutMapping
    public ResponseEntity<?> setMyStudiengang(@RequestBody SetRequest req) {
        User u = UserUtil.getUser();
        if (u == null)
            return ResponseEntity.status(401).build();
        if (req == null)
            return ResponseEntity.badRequest().body(Map.of("error", "missing body"));
        Studiengang s = req.studiengangId() != null ? studiengangRepository.findById(req.studiengangId()).orElse(null)
                : null;
        u.setStudiengang(s);
        userRepository.save(u);

        String studiengangName = s != null ? s.getName() : "null";
        auditService.logEvent(AuditEventType.STUDIENGANG_SET, "Studiengang gesetzt: " + studiengangName);

        List<TauschTermin> offers = tauschTerminRepository.findTauschTerminByUserid(u.getId());
        if (!offers.isEmpty()) {
            tauschTerminRepository.deleteAll(offers);
        }

        try {
            groupSuggestionService.rebuildCache();
        } catch (Exception ignore) {
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/list")
    public ResponseEntity<List<Map<String, Object>>> listAllStudiengaenge() {
        List<Studiengang> all = studiengangRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Studiengang s : all) {
            if (s == null)
                continue;
            result.add(Map.of(
                    "id", s.getId(),
                    "name", s.getName(),
                    "shortCode", s.getShortCode()));
        }
        return ResponseEntity.ok(result);
    }
}
