package team.boerse.tauschboerse.settings;

import java.time.Instant;
import java.time.LocalTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class SystemSettings {

    @Id
    private Long id = 1L;

    @Enumerated(EnumType.STRING)
    private SystemMode mode = SystemMode.DIRECT;

    @Enumerated(EnumType.STRING)
    private ScheduleType scheduleType;

    private Integer intervalHours;

    private LocalTime dailyTime;

    private String timezoneInfo;

    private Instant lastRunAt;

    private Instant nextRunAt;

    public SystemSettings() {
    }

}
