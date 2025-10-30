package team.boerse.tauschboerse.suggestions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import java.util.Timer;
import java.util.TimerTask;

@Component
@RequiredArgsConstructor
public class SuggestionScheduler {

    private static final Logger logger = LoggerFactory.getLogger(SuggestionScheduler.class);

    private final GroupSuggestionService suggestionService;

    @PostConstruct
    public void runOnceAtStartup() {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    suggestionService.rebuildCache();
                    logger.info("Suggestion cache rebuilt on startup");
                } catch (Exception ex) {
                    logger.warn("Startup suggestions rebuild failed: {}", ex.getMessage());
                }
            }
        }, 1000); // 1 Sekunde Verzögerung
    }
}
