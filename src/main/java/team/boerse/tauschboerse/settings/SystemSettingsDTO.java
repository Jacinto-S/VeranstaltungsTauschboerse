package team.boerse.tauschboerse.settings;

import java.time.Instant;
import java.time.LocalTime;

public record SystemSettingsDTO(
                SystemMode mode,
                ScheduleType scheduleType,
                Integer intervalHours,
                LocalTime dailyTime,
                String timezoneInfo,
                Instant lastRunAt,
                Instant nextRunAt) {
}
