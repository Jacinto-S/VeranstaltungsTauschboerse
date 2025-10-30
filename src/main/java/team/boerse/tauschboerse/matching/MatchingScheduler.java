package team.boerse.tauschboerse.matching;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import team.boerse.tauschboerse.settings.SystemMode;
import team.boerse.tauschboerse.settings.SystemSettings;
import team.boerse.tauschboerse.settings.SystemSettingsService;

@Component
@EnableScheduling
@RequiredArgsConstructor
public class MatchingScheduler {

    private static final Logger logger = LoggerFactory.getLogger(MatchingScheduler.class);

    private final MatchingService matchingService;
    private final SystemSettingsService systemSettingsService;

    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    public void checkAndTriggerMatching() {
        try {
            SystemSettings settings = systemSettingsService.getOrCreateSettings();

            if (settings.getMode() != SystemMode.POOLED_3CYCLE) {
                return;
            }

            if (settings.getScheduleType() == null) {
                return;
            }

            if (settings.getNextRunAt() == null) {
                Instant nextRun = matchingService.calculateNextRun(settings, Instant.now());
                settings.setNextRunAt(nextRun);
                systemSettingsService.saveSettings(settings);
                logger.info("Initialized nextRunAt to: {}", nextRun);
                return;
            }

            Instant now = Instant.now();
            if (now.isBefore(settings.getNextRunAt())) {
                return;
            }

            logger.info("Scheduled matching run is due. Attempting to start...");
            triggerScheduledRun();

        } catch (Exception ex) {
            logger.error("Error in scheduled matching check: {}", ex.getMessage(), ex);
        }
    }

    private void triggerScheduledRun() {
        try {
            logger.info("Starting scheduled matching run...");
            matchingService.runMatching();
            logger.info("Scheduled matching run completed successfully");
        } catch (IllegalStateException ise) {
            logger.warn("Scheduled run skipped: {}", ise.getMessage());
        } catch (Exception ex) {
            logger.error("Error during scheduled matching run: {}", ex.getMessage(), ex);
        }
    }
}
