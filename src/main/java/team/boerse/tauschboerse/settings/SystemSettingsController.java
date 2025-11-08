package team.boerse.tauschboerse.settings;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import team.boerse.tauschboerse.User;
import team.boerse.tauschboerse.UserUtil;
import team.boerse.tauschboerse.admin.AdminController.AdminAccessDeniedException;

@RestController
@RequiredArgsConstructor
public class SystemSettingsController {

    private final SystemSettingsService settingsService;

    private void requireAdmin() {
        User user = UserUtil.getUser();
        if (user == null || !Boolean.TRUE.equals(user.getIsAdmin())) {
            throw new AdminAccessDeniedException("Access denied");
        }
    }

    @GetMapping("/api/admin/settings")
    public ResponseEntity<SystemSettingsDTO> getSettings() {
        requireAdmin();
        SystemSettings settings = settingsService.getOrCreateSettings();
        return ResponseEntity.ok(settingsService.toDTO(settings));
    }

    @PostMapping("/api/admin/settings")
    public ResponseEntity<SystemSettingsDTO> updateSettings(@RequestBody SystemSettingsUpdateDTO dto) {
        requireAdmin();
        try {
            SystemSettings updated = settingsService.updateSettings(dto);
            return ResponseEntity.ok(settingsService.toDTO(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/api/settings/mode")
    public ResponseEntity<SystemModeDTO> getMode() {
        SystemSettings settings = settingsService.getOrCreateSettings();
        return ResponseEntity.ok(new SystemModeDTO(settings.getMode()));
    }
}
