package team.boerse.tauschboerse.settings;

import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SystemSettingsService {

    private final SystemSettingsRepository repository;

    @Transactional
    public SystemSettings getOrCreateSettings() {
        return repository.findById(1L).orElseGet(() -> {
            SystemSettings settings = new SystemSettings();
            settings.setId(1L);
            settings.setMode(SystemMode.DIRECT);
            settings.setTimezoneInfo(ZoneId.systemDefault().getId());
            return repository.save(settings);
        });
    }

    @Transactional
    public SystemSettings updateSettings(SystemSettingsUpdateDTO dto) {
        if (dto.mode() == SystemMode.POOLED_3CYCLE) {
            if (dto.scheduleType() == null) {
                throw new IllegalArgumentException("scheduleType must be set for POOLED_3CYCLE mode");
            }
            if (dto.scheduleType() == ScheduleType.INTERVAL_HOURS && dto.intervalHours() == null) {
                throw new IllegalArgumentException("intervalHours must be set for INTERVAL_HOURS schedule type");
            }
            if (dto.scheduleType() == ScheduleType.DAILY_FIXED && dto.dailyTime() == null) {
                throw new IllegalArgumentException("dailyTime must be set for DAILY_FIXED schedule type");
            }
        }

        SystemSettings settings = getOrCreateSettings();
        settings.setMode(dto.mode());
        settings.setScheduleType(dto.scheduleType());
        settings.setIntervalHours(dto.intervalHours());
        settings.setDailyTime(dto.dailyTime());
        settings.setTimezoneInfo(ZoneId.systemDefault().getId());

        return repository.save(settings);
    }

    @Transactional
    public SystemSettings saveSettings(SystemSettings settings) {
        return repository.save(settings);
    }

    public SystemSettingsDTO toDTO(SystemSettings settings) {
        return new SystemSettingsDTO(
                settings.getMode(),
                settings.getScheduleType(),
                settings.getIntervalHours(),
                settings.getDailyTime(),
                settings.getTimezoneInfo(),
                settings.getLastRunAt(),
                settings.getNextRunAt());
    }
}
