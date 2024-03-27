package team.boerse.tauschboerse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class CleanupManager {

    @Autowired
    private KalenderRepository kalenderRepository;

    @Autowired
    private TauschTerminRepository tauschTerminRepository;

    Logger logger = LoggerFactory.getLogger(CleanupManager.class);

    @Scheduled(fixedDelay = 1000 * 60 * 60 * 24)
    public void cleanup() {
        long currentTime = System.currentTimeMillis();

        // Delete all entries older than 90 days
        for (Kalender kalender : kalenderRepository.findAll()) {
            if (kalender.getCreateDate() == null)
                continue;
            if (kalender.getCreateDate().getTime() < currentTime - 1000 * 60 * 60 * 24 * 90) {
                logger.info(
                        "Deleting kalender entry from " + kalender.getUserId() + " because it is older than 90 days");
                kalenderRepository.delete(kalender);
            }
        }

        // Delete all entries older than 21 days
        for (TauschTermin termin : tauschTerminRepository.findAll()) {
            if (termin.getCreatedDate() == null)
                continue;
            if (termin.getCreatedDate().getTime() < currentTime - 1000 * 60 * 60 * 24 * 21) {
                logger.info(
                        "Deleting tauschTermin entry from " + termin.getUserId() + " because it is older than 21 days");
                tauschTerminRepository.delete(termin);
            }
        }
    }

}
