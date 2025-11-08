package team.boerse.tauschboerse.settings;

import java.time.LocalTime;

public record SystemSettingsUpdateDTO(
        SystemMode mode,
        ScheduleType scheduleType,
        Integer intervalHours,
        LocalTime dailyTime) {
}
