package team.boerse.tauschboerse;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class CounterService {

    private final MeterRegistry meterRegistry;
    private final Map<String, Counter> counters = new HashMap<>();

    public CounterService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    // ================================
    // Counter-Methoden
    // ================================

    /**
     * Holt oder erstellt einen Counter für ein gegebenes Event.
     *
     * @param eventName Der Name des Events.
     * @return Der Counter für das Event.
     */
    public Counter getCounter(String eventName) {
        return counters.computeIfAbsent(eventName, this::createCounter);
    }

    /**
     * Erstellt einen neuen Counter mit dem angegebenen Eventnamen.
     *
     * @param eventName Der Name des Events.
     * @return Ein neuer Counter.
     */
    private Counter createCounter(String eventName) {
        return Counter.builder("TB_" + eventName)
                .description("Counter for event: " + eventName)
                .register(meterRegistry);
    }

    /**
     * Erhöht den Counter für das angegebene Event.
     *
     * @param eventName Der Name des Events.
     */
    public void incrementCounter(String eventName) {
        getCounter(eventName).increment();
    }

}
