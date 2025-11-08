package team.boerse.tauschboerse.matching;

import java.time.Instant;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import team.boerse.tauschboerse.User;
import team.boerse.tauschboerse.UserUtil;
import team.boerse.tauschboerse.admin.AdminController.AdminAccessDeniedException;
import team.boerse.tauschboerse.settings.SystemMode;
import team.boerse.tauschboerse.settings.SystemSettingsService;

@RestController
@RequestMapping("/api/admin/matching")
@RequiredArgsConstructor
public class MatchingController {

    private final MatchingService matchingService;
    private final SystemSettingsService systemSettingsService;

    private void requireAdmin() {
        User user = UserUtil.getUser();
        if (user == null || !Boolean.TRUE.equals(user.getIsAdmin())) {
            throw new AdminAccessDeniedException("Access denied");
        }
    }

    @GetMapping("/what-if")
    public ResponseEntity<MatchingResultDTO> whatIf() {
        requireAdmin();
        MatchingResultDTO result = matchingService.calculateWhatIf();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/run")
    public ResponseEntity<?> runMatching() {
        requireAdmin();
        var settings = systemSettingsService.getOrCreateSettings();
        if (settings.getMode() != SystemMode.POOLED_3CYCLE) {
            return ResponseEntity.status(409).build();
        }

        MatchingResultDTO result = matchingService.runMatching();
        settings = systemSettingsService.getOrCreateSettings();

        record RunResponse(
                Instant runAt,
                long durationMs,
                MatchingResultDTO.Counts counts,
                String method,
                boolean capped,
                String warning,
                Instant lastRunAt,
                Instant nextRunAt) {
        }
        var resp = new RunResponse(
                result.generatedAt(),
                result.computationTimeMs(),
                result.counts(),
                result.method(),
                result.capped(),
                result.warning(),
                settings.getLastRunAt(),
                settings.getNextRunAt());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/setup-test-3cycle")
    public ResponseEntity<String> setupTest3CycleMatch() {
        requireAdmin();
        try {
            matchingService.setupTestData3CycleMatch();
            return ResponseEntity.ok("Test-Daten für 3er-Zirkeltausch erfolgreich angelegt");
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Fehler beim Erstellen der Test-Daten: " + ex.getMessage());
        }
    }

    public record MatchingResultDTO(
            Instant generatedAt,
            String timezoneInfo,
            Counts counts,
            String method,
            boolean capped,
            String warning,
            long computationTimeMs) {

        public static record Counts(int pairs2, int cycles3, int persons) {
        }
    }

}
