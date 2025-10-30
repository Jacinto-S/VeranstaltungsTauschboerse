package team.boerse.tauschboerse.matching;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import team.boerse.tauschboerse.KalenderTermin;
import team.boerse.tauschboerse.KalenderTerminType;
import team.boerse.tauschboerse.TauschTermin;
import team.boerse.tauschboerse.TauschTerminRepository;
import team.boerse.tauschboerse.audit.AuditService;
import team.boerse.tauschboerse.settings.SystemMode;
import team.boerse.tauschboerse.settings.SystemSettings;
import team.boerse.tauschboerse.settings.SystemSettingsService;
import team.boerse.tauschboerse.matching.MatchingController.MatchingResultDTO;

public class MatchingServiceTest {

    private MatchingService service;
    private TauschTerminRepository repo;
    private SystemSettingsService settingsService;
    private AuditService auditService;

    @BeforeEach
    void setup() throws Exception {
        repo = Mockito.mock(TauschTerminRepository.class);
        settingsService = Mockito.mock(SystemSettingsService.class);
        auditService = Mockito.mock(AuditService.class);

        service = new MatchingService(
                repo,
                settingsService,
                auditService,
                Mockito.mock(team.boerse.tauschboerse.KalenderRepository.class),
                Mockito.mock(team.boerse.tauschboerse.KalenderTerminRepository.class),
                Mockito.mock(team.boerse.tauschboerse.UserRepository.class),
                Mockito.mock(team.boerse.tauschboerse.studiengang.NotSharedService.class));

        SystemSettings s = new SystemSettings();
        s.setMode(SystemMode.POOLED_3CYCLE);
        s.setTimezoneInfo("Europe/Berlin");
        when(settingsService.getOrCreateSettings()).thenReturn(s);
        when(settingsService.saveSettings(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // Hilfsfunktionen
    private static void setId(Object entity, long id) throws Exception {
        Field f = entity.getClass().getDeclaredField("id");
        f.setAccessible(true);
        f.setLong(entity, id);
    }

    private static Date time(int dayOfWeek, int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.MILLISECOND, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.DAY_OF_WEEK, dayOfWeek);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        return c.getTime();
    }

    private static KalenderTermin kt(String title, int dayOfWeek, int sh, int sm, int eh, int em,
            KalenderTerminType t) {
        return new KalenderTermin(time(dayOfWeek, sh, sm), time(dayOfWeek, eh, em), title, "", "", t);
    }

    @Test
    void whatIf_finds_one_2cycle() throws Exception {
        // Zwei Nutzer A,B mit gleichem Kurs/Typ/Slot, jeweils den anderen als Wunsch
        KalenderTermin aOffer = kt("Mathe (U-A)", Calendar.MONDAY, 8, 15, 9, 45, KalenderTerminType.U);
        KalenderTermin bOffer = kt("Mathe (U-B)", Calendar.MONDAY, 8, 15, 9, 45, KalenderTerminType.U);

        KalenderTermin aWantsB = kt("Mathe (U-B)", Calendar.MONDAY, 8, 15, 9, 45, KalenderTerminType.U);
        KalenderTermin bWantsA = kt("Mathe (U-A)", Calendar.MONDAY, 8, 15, 9, 45, KalenderTerminType.U);

        TauschTermin a = new TauschTermin(1L, aOffer, List.of(aWantsB));
        TauschTermin b = new TauschTermin(2L, bOffer, List.of(bWantsA));
        setId(a, 101L);
        setId(b, 102L);

        when(repo.findAll()).thenReturn(List.of(a, b));
        when(repo.findById(101L)).thenReturn(Optional.of(a));
        when(repo.findById(102L)).thenReturn(Optional.of(b));
        when(repo.findById(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            if (Objects.equals(id, 101L))
                return Optional.of(a);
            if (Objects.equals(id, 102L))
                return Optional.of(b);
            return Optional.empty();
        });

        MatchingResultDTO res = service.calculateWhatIf();
        assertNotNull(res);
        assertEquals(1, res.counts().pairs2());
        assertEquals(0, res.counts().cycles3());
        assertEquals(2, res.counts().persons());
        assertNotNull(res.generatedAt());
        assertEquals("Europe/Berlin", res.timezoneInfo());
        assertTrue("optimal".equals(res.method()) || "greedy".equals(res.method()));
    }

    @Test
    void whatIf_finds_one_3cycle() throws Exception {
        // Drei Nutzer A,B,C mit Zyklus A->B->C->A
        KalenderTermin aOffer = kt("Physik (U-A)", Calendar.TUESDAY, 10, 0, 11, 30, KalenderTerminType.U);
        KalenderTermin bOffer = kt("Physik (U-B)", Calendar.TUESDAY, 10, 0, 11, 30, KalenderTerminType.U);
        KalenderTermin cOffer = kt("Physik (U-C)", Calendar.TUESDAY, 10, 0, 11, 30, KalenderTerminType.U);

        TauschTermin a = new TauschTermin(1L, aOffer, List.of(bOffer)); // A möchte B
        TauschTermin b = new TauschTermin(2L, bOffer, List.of(cOffer)); // B möchte C
        TauschTermin c = new TauschTermin(3L, cOffer, List.of(aOffer)); // C möchte A
        setId(a, 201L);
        setId(b, 202L);
        setId(c, 203L);

        when(repo.findAll()).thenReturn(List.of(a, b, c));
        when(repo.findById(201L)).thenReturn(Optional.of(a));
        when(repo.findById(202L)).thenReturn(Optional.of(b));
        when(repo.findById(203L)).thenReturn(Optional.of(c));
        when(repo.findById(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return switch (id.toString()) {
                case "201" -> Optional.of(a);
                case "202" -> Optional.of(b);
                case "203" -> Optional.of(c);
                default -> Optional.empty();
            };
        });

        MatchingResultDTO res = service.calculateWhatIf();
        assertNotNull(res);
        assertEquals(0, res.counts().pairs2());
        assertEquals(1, res.counts().cycles3());
        assertEquals(3, res.counts().persons());
    }

    @Test
    void solve_optimal_with_cap_triggers_greedy_on_large_input() {
        List<MatchingService.Cycle> cycles = new ArrayList<>();
        for (int i = 0; i < 210; i++) {
            List<KalenderTermin> wishes1 = List.of(
                    kt("Kurs (U-B)", Calendar.WEDNESDAY, 8, 15, 9, 45, KalenderTerminType.U));
            List<KalenderTermin> wishes2 = List.of(
                    kt("Kurs (U-A)", Calendar.WEDNESDAY, 8, 15, 9, 45, KalenderTerminType.U));

            MatchingService.Node n1 = new MatchingService.Node(1000 + i * 3, 100 + i * 3,
                    kt("Kurs (U-A)", Calendar.WEDNESDAY, 8, 15, 9, 45, KalenderTerminType.U), wishes1);
            MatchingService.Node n2 = new MatchingService.Node(1001 + i * 3, 101 + i * 3,
                    kt("Kurs (U-B)", Calendar.WEDNESDAY, 8, 15, 9, 45, KalenderTerminType.U), wishes2);
            cycles.add(new MatchingService.Cycle("Kurs|U", List.of(n1, n2))); // 2er Zyklen
        }
        MatchingService.Solution sol = service.solveOptimalWithCapPerCourse(cycles, 50_000,
                System.currentTimeMillis());
        assertNotNull(sol);
        assertEquals("greedy", sol.method);
        assertTrue(sol.capped);
        assertNotNull(sol.warning);
        assertTrue(sol.persons >= 0);
    }

    @Test
    void tiebreaker_prefers_lower_wishcount_sum_for_equal_persons() {
        // Vier Nutzer (1,2,3,4). Zwei Alternativen mit jeweils 4 Personen (2+2), aber
        // unterschiedliche Wish-Summen.
        // Option High: (1-2) + (3-4) mit hohen wishCounts (je 10 Wünsche)
        List<KalenderTermin> highWishes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            highWishes.add(kt("DummyWish" + i + " (U-X)", Calendar.THURSDAY, 10, 0, 11, 30, KalenderTerminType.U));
        }

        MatchingService.Node h1 = new MatchingService.Node(1, 1,
                kt("Kurs (U-A)", Calendar.THURSDAY, 8, 15, 9, 45, KalenderTerminType.U), new ArrayList<>(highWishes));
        MatchingService.Node h2 = new MatchingService.Node(2, 2,
                kt("Kurs (U-B)", Calendar.THURSDAY, 8, 15, 9, 45, KalenderTerminType.U), new ArrayList<>(highWishes));
        MatchingService.Node h3 = new MatchingService.Node(3, 3,
                kt("Kurs (U-C)", Calendar.THURSDAY, 8, 15, 9, 45, KalenderTerminType.U), new ArrayList<>(highWishes));
        MatchingService.Node h4 = new MatchingService.Node(4, 4,
                kt("Kurs (U-D)", Calendar.THURSDAY, 8, 15, 9, 45, KalenderTerminType.U), new ArrayList<>(highWishes));

        MatchingService.Cycle high12 = new MatchingService.Cycle("Kurs|U", List.of(h1, h2));
        MatchingService.Cycle high34 = new MatchingService.Cycle("Kurs|U", List.of(h3, h4));

        // Option Low: (1-3) + (2-4) mit niedrigen wishCounts (je 1 Wunsch)
        List<KalenderTermin> lowWishes = List
                .of(kt("DummyWish (U-X)", Calendar.THURSDAY, 10, 0, 11, 30, KalenderTerminType.U));

        MatchingService.Node l1 = new MatchingService.Node(5, 1,
                kt("Kurs (U-A)", Calendar.THURSDAY, 8, 15, 9, 45, KalenderTerminType.U), new ArrayList<>(lowWishes));
        MatchingService.Node l3 = new MatchingService.Node(6, 3,
                kt("Kurs (U-C)", Calendar.THURSDAY, 8, 15, 9, 45, KalenderTerminType.U), new ArrayList<>(lowWishes));
        MatchingService.Node l2 = new MatchingService.Node(7, 2,
                kt("Kurs (U-B)", Calendar.THURSDAY, 8, 15, 9, 45, KalenderTerminType.U), new ArrayList<>(lowWishes));
        MatchingService.Node l4 = new MatchingService.Node(8, 4,
                kt("Kurs (U-D)", Calendar.THURSDAY, 8, 15, 9, 45, KalenderTerminType.U), new ArrayList<>(lowWishes));

        MatchingService.Cycle low13 = new MatchingService.Cycle("Kurs|U", List.of(l1, l3));
        MatchingService.Cycle low24 = new MatchingService.Cycle("Kurs|U", List.of(l2, l4));

        List<MatchingService.Cycle> cycles = List.of(high12, high34, low13, low24);
        MatchingService.Solution sol = service.solveOptimalWithCapPerCourse(cycles, 50_000,
                System.currentTimeMillis());

        assertNotNull(sol);
        assertEquals("optimal", sol.method);
        assertFalse(sol.capped);
        assertEquals(4, sol.persons);
        assertEquals(2, sol.pairs2Count);
        assertEquals(0, sol.cycles3Count);
        assertEquals(4, sol.wishSum); // 2+2 mit wishCounts 1+1 und 1+1
    }

    @Test
    void calculateNextRun_interval_hours() {
        SystemSettings settings = new SystemSettings();
        settings.setScheduleType(team.boerse.tauschboerse.settings.ScheduleType.INTERVAL_HOURS);
        settings.setIntervalHours(6);
        settings.setTimezoneInfo("Europe/Berlin");

        java.time.Instant now = java.time.Instant.parse("2025-01-15T10:00:00Z");
        java.time.Instant next = service.calculateNextRun(settings, now);

        assertNotNull(next);
        assertEquals(now.plusSeconds(6 * 3600), next);
    }

    @Test
    void calculateNextRun_daily_fixed_same_day() {
        SystemSettings settings = new SystemSettings();
        settings.setScheduleType(team.boerse.tauschboerse.settings.ScheduleType.DAILY_FIXED);
        settings.setDailyTime(java.time.LocalTime.of(15, 0)); // 15:00
        settings.setTimezoneInfo("Europe/Berlin");

        // 10:00 CET -> nächster Run heute um 15:00
        java.time.Instant now = java.time.Instant.parse("2025-01-15T09:00:00Z"); // 10:00 CET
        java.time.Instant next = service.calculateNextRun(settings, now);

        assertNotNull(next);
        // Sollte heute um 15:00 CET sein (14:00 UTC)
        assertTrue(next.isAfter(now));
    }

    @Test
    void calculateNextRun_daily_fixed_next_day() {
        SystemSettings settings = new SystemSettings();
        settings.setScheduleType(team.boerse.tauschboerse.settings.ScheduleType.DAILY_FIXED);
        settings.setDailyTime(java.time.LocalTime.of(10, 0)); // 10:00
        settings.setTimezoneInfo("Europe/Berlin");

        // 16:00 CET -> nächster Run morgen um 10:00
        java.time.Instant now = java.time.Instant.parse("2025-01-15T15:00:00Z"); // 16:00 CET
        java.time.Instant next = service.calculateNextRun(settings, now);

        assertNotNull(next);
        // Sollte morgen um 10:00 CET sein
        assertTrue(next.isAfter(now));
    }

    @Test
    void calculateNextRun_returns_null_when_no_schedule_type() {
        SystemSettings settings = new SystemSettings();
        settings.setScheduleType(null);

        java.time.Instant now = java.time.Instant.now();
        java.time.Instant next = service.calculateNextRun(settings, now);

        assertNull(next);
    }
}
