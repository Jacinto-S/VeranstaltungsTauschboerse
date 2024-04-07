package team.boerse.tauschboerse;

import java.util.List;

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

    @Scheduled(fixedDelay = 1000 * 60 * 60 * 24, initialDelay = 5000)
    public void cleanup() {
        long currentTime = System.currentTimeMillis();

        // Delete all entries older than 90 days
        List<Kalender> kalenderList = kalenderRepository.findAll();
        for (Kalender kalender : kalenderList) {
            if (kalender.getCreateDate() == null)
                continue;
            if (kalender.getCreateDate().getTime() < currentTime - 1000 * 60 * 60 * 24 * 90) {
                logger.info(String.format("Deleting kalender entry from %s because it is older than 90 days",
                        kalender.getUserId()));
                kalenderRepository.delete(kalender);
            }
        }

        // Delete all entries older than 21 days
        List<TauschTermin> terminList = tauschTerminRepository.findAll();

        for (TauschTermin termin : terminList) {
            if (termin.getCreatedDate() == null)
                continue;
            if (termin.getCreatedDate().getTime() < currentTime - 1000 * 60 * 60 * 24 * 21) {
                logger.info(String.format("Deleting tauschTermin entry from %s because it is older than 21 days",
                        termin.getUserId()));
                tauschTerminRepository.delete(termin);
            }
        }
    }

}
