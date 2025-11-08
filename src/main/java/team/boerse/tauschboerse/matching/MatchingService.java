package team.boerse.tauschboerse.matching;

import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;

import team.boerse.tauschboerse.Kalender;
import team.boerse.tauschboerse.KalenderRepository;
import team.boerse.tauschboerse.KalenderTermin;
import team.boerse.tauschboerse.KalenderTerminRepository;
import team.boerse.tauschboerse.KalenderTerminType;
import team.boerse.tauschboerse.TauschTermin;
import team.boerse.tauschboerse.TauschTerminRepository;
import team.boerse.tauschboerse.User;
import team.boerse.tauschboerse.UserRepository;
import team.boerse.tauschboerse.audit.AuditEventType;
import team.boerse.tauschboerse.studiengang.NotSharedService;
import team.boerse.tauschboerse.audit.AuditService;
import team.boerse.tauschboerse.settings.SystemMode;
import team.boerse.tauschboerse.settings.SystemSettings;
import team.boerse.tauschboerse.settings.SystemSettingsService;
import team.boerse.tauschboerse.mail.MailUtils;
import team.boerse.tauschboerse.matching.MatchingController.MatchingResultDTO;

@Service
@RequiredArgsConstructor
public class MatchingService {

    private final TauschTerminRepository tauschTerminRepository;
    private final SystemSettingsService systemSettingsService;
    private final AuditService auditService;
    private final KalenderRepository kalenderRepository;
    private final KalenderTerminRepository kalenderTerminRepository;
    private final UserRepository userRepository;
    private final NotSharedService notSharedService;

    private static final int DEFAULT_MAX_SEARCH_NODES = 50_000;
    private static final long TIMEOUT_MS = 500;

    private final Logger logger = LoggerFactory.getLogger(MatchingService.class);

    private final AtomicBoolean running = new AtomicBoolean(false);

    public boolean isRunning() {
        return running.get();
    }

    public boolean tryStartRun() {
        return running.compareAndSet(false, true);
    }

    public void finishRun() {
        running.set(false);
    }

    /** interner Graph-Knoten (ein Angebot) */
    static class Node {
        final long offerId;
        final long userId;
        final KalenderTermin angebot;
        final List<KalenderTermin> wishes;
        final List<Edge> out = new ArrayList<>();
        final int wishCount;
        final Long studiengangId; // vorab geladene SG-ID des Users
        final String angebotBase; // Basisname des Angebot-Termins
        final boolean notSharedForBase; // ob dieser Kurs-Basisname für die SG not_shared ist

        Node(long offerId, long userId, KalenderTermin angebot, List<KalenderTermin> wishes, Long studiengangId,
                String angebotBase, boolean notSharedForBase) {
            this.offerId = offerId;
            this.userId = userId;
            this.angebot = angebot;
            this.wishes = wishes != null ? wishes : List.of();
            this.wishCount = this.wishes.size();
            this.studiengangId = studiengangId;
            this.angebotBase = angebotBase != null ? angebotBase : baseName(angebot != null ? angebot.getName() : null);
            this.notSharedForBase = notSharedForBase;
        }

        Node(long offerId, long userId, KalenderTermin angebot, List<KalenderTermin> wishes) {
            this(offerId, userId, angebot, wishes, null,
                    baseName(angebot != null ? angebot.getName() : null), false);
        }
    }

    static class Edge {
        final Node from;
        final Node to;

        Edge(Node from, Node to) {
            this.from = from;
            this.to = to;
        }
    }

    /** Ein 2er- oder 3er-Zyklus, inklusive Kurs/Typ-Schlüssel */
    public record Cycle(String groupKey, List<Node> nodes) {
        public int size() {
            return nodes.size();
        }

        public Set<Long> userSet() {
            return nodes.stream().map(n -> n.userId).collect(Collectors.toSet());
        }
    }

    public record Graph(String groupKey, KalenderTerminType type, Node[] nodes) {
    }

    public record GraphBundle(List<Graph> graphs) {
    }

    public MatchingResultDTO calculateWhatIf() {
        long startWall = System.currentTimeMillis();
        SystemSettings settings = systemSettingsService.getOrCreateSettings();
        String tz = settings.getTimezoneInfo() != null ? settings.getTimezoneInfo() : ZoneId.systemDefault().getId();

        GraphBundle bundle = buildGraphFromOffers();

        int totalPersons = 0, totalPairs2 = 0, totalCycles3 = 0;
        boolean anyCapped = false;
        boolean anyGreedy = false;
        StringBuilder warnings = new StringBuilder();

        for (Graph g : bundle.graphs) {
            List<Cycle> cycles = new ArrayList<>();
            cycles.addAll(enumerate2Cycles(g));
            cycles.addAll(enumerate3Cycles(g));

            Solution sol = solveOptimalWithCapPerCourse(cycles, DEFAULT_MAX_SEARCH_NODES, System.currentTimeMillis());
            totalPersons += sol.persons;
            totalPairs2 += sol.pairs2Count;
            totalCycles3 += sol.cycles3Count;
            anyCapped |= sol.capped;
            anyGreedy |= "greedy".equals(sol.method);
            if (sol.warning != null) {
                if (!warnings.isEmpty())
                    warnings.append(" | ");
                warnings.append("[").append(g.groupKey).append("] ").append(sol.warning);
            }
        }

        long duration = System.currentTimeMillis() - startWall;
        MatchingResultDTO.Counts counts = new MatchingResultDTO.Counts(totalPairs2, totalCycles3, totalPersons);
        String method = anyGreedy ? "greedy" : "optimal";
        String warn = warnings.isEmpty() ? null : warnings.toString();
        return new MatchingResultDTO(Instant.now(), tz, counts, method, anyCapped, warn, duration);
    }

    @Transactional
    public MatchingResultDTO runMatching() {
        if (!tryStartRun()) {
            String msg = "Matching run rejected: already running";
            logger.warn(msg);
            auditService.logEvent(AuditEventType.WARNING, msg);
            throw new IllegalStateException(msg);
        }

        long startWall = System.currentTimeMillis();
        Instant runStartTime = Instant.now();
        try {
            SystemSettings settings = systemSettingsService.getOrCreateSettings();
            if (settings.getMode() != SystemMode.POOLED_3CYCLE) {
                auditService.logEvent(AuditEventType.WARNING,
                        "Matching run invoked while not in POOLED_3CYCLE mode");
            }

            String tz = settings.getTimezoneInfo() != null ? settings.getTimezoneInfo()
                    : ZoneId.systemDefault().getId();
            GraphBundle bundle = buildGraphFromOffers();

            List<Cycle> allChosen = new ArrayList<>();
            boolean anyCapped = false;
            boolean anyGreedy = false;
            StringBuilder warnings = new StringBuilder();

            for (Graph g : bundle.graphs) {
                List<Cycle> cycles = new ArrayList<>();
                cycles.addAll(enumerate2Cycles(g));
                cycles.addAll(enumerate3Cycles(g));

                Solution sol = solveOptimalWithCapPerCourse(cycles, DEFAULT_MAX_SEARCH_NODES,
                        System.currentTimeMillis());
                allChosen.addAll(sol.chosen);
                anyCapped |= sol.capped;
                anyGreedy |= "greedy".equals(sol.method);
                if (sol.warning != null) {
                    if (!warnings.isEmpty())
                        warnings.append(" | ");
                    warnings.append("[").append(g.groupKey).append("] ").append(sol.warning);
                }
            }

            ApplyStats applied = applyChosenCycles(allChosen);
            long duration = System.currentTimeMillis() - startWall;

            settings.setLastRunAt(runStartTime);
            Instant nextRun = calculateNextRun(settings, runStartTime);
            settings.setNextRunAt(nextRun);
            systemSettingsService.saveSettings(settings);

            logger.info("Matching run completed. lastRunAt={}, nextRunAt={}, persons={}, pairs2={}, cycles3={}",
                    runStartTime, nextRun, applied.persons, applied.pairs2, applied.cycles3);

            MatchingResultDTO.Counts counts = new MatchingResultDTO.Counts(applied.pairs2, applied.cycles3,
                    applied.persons);
            String method = anyGreedy ? "greedy" : "optimal";
            String warn = warnings.isEmpty() ? null : warnings.toString();
            return new MatchingResultDTO(runStartTime, tz, counts, method, anyCapped, warn, duration);

        } finally {
            finishRun();
        }
    }

    public Instant calculateNextRun(SystemSettings settings, Instant fromTime) {
        if (settings.getScheduleType() == null)
            return null;
        String timezoneId = settings.getTimezoneInfo();
        if (timezoneId == null || timezoneId.isEmpty())
            timezoneId = ZoneId.systemDefault().getId();
        ZoneId zone = ZoneId.of(timezoneId);

        switch (settings.getScheduleType()) {
            case INTERVAL_HOURS -> {
                Integer h = settings.getIntervalHours();
                if (h == null || h <= 0) {
                    logger.warn("INTERVAL_HOURS schedule type but intervalHours not set or invalid");
                    return null;
                }
                return fromTime.plusSeconds(h * 3600L);
            }
            case DAILY_FIXED -> {
                if (settings.getDailyTime() == null) {
                    logger.warn("DAILY_FIXED schedule type but dailyTime not set");
                    return null;
                }
                var current = java.time.ZonedDateTime.ofInstant(fromTime, zone);
                var target = current.toLocalDate().atTime(settings.getDailyTime()).atZone(zone);
                return (current.isBefore(target) ? target : target.plusDays(1)).toInstant();
            }
            default -> {
                logger.warn("Unknown schedule type: {}", settings.getScheduleType());
                return null;
            }
        }
    }

    public GraphBundle buildGraphFromOffers() {
        List<TauschTermin> offers = tauschTerminRepository.findAll();
        Set<Long> userIds = offers.stream().map(TauschTermin::getUserid).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Long> userIdToSg = new HashMap<>();
        if (!userIds.isEmpty()) {
            try {
                for (User u : userRepository.findAllById(userIds)) {
                    if (u != null) {
                        userIdToSg.put(u.getId(), u.getStudiengang() != null ? u.getStudiengang().getId() : null);
                    }
                }
            } catch (Exception ignore) {
            }
        }

        Map<String, List<TauschTermin>> byCourseAndType = new HashMap<>();
        for (TauschTermin t : offers) {
            KalenderTermin a = t.getAngebot();
            if (a == null || a.getType() == null)
                continue;
            String base = baseName(a.getName());
            Long sgId = userIdToSg.get(t.getUserid());
            String key;
            if (sgId != null && notSharedService.isNotShared(base, sgId)) {
                key = base + "|" + a.getType().name() + "|sg:" + sgId;
            } else {
                key = base + "|" + a.getType().name();
            }
            List<TauschTermin> listForKey = byCourseAndType.get(key);
            if (listForKey == null) {
                listForKey = new ArrayList<>();
                byCourseAndType.put(key, listForKey);
            }
            listForKey.add(t);
        }

        List<Graph> graphs = new ArrayList<>();
        for (Map.Entry<String, List<TauschTermin>> e : byCourseAndType.entrySet()) {
            String groupKey = e.getKey();
            List<TauschTermin> list = e.getValue();
            if (list.isEmpty())
                continue;

            Node[] nodes = new Node[list.size()];
            for (int i = 0; i < list.size(); i++) {
                TauschTermin t = list.get(i);
                KalenderTermin angebot = t.getAngebot();
                String angebotBase = baseName(angebot != null ? angebot.getName() : null);
                Long sgId = userIdToSg.get(t.getUserid());
                boolean notShared = false;
                try {
                    notShared = (sgId != null) && notSharedService.isNotShared(angebotBase, sgId);
                } catch (Exception ignore) {
                }
                nodes[i] = new Node(
                        t.getId(),
                        t.getUserid(),
                        angebot,
                        t.getGesucht() != null ? List.copyOf(t.getGesucht()) : List.of(),
                        sgId,
                        angebotBase,
                        notShared);
            }

            for (int i = 0; i < nodes.length; i++) {
                Node u = nodes[i];
                for (int j = 0; j < nodes.length; j++) {
                    if (i == j)
                        continue;
                    Node v = nodes[j];
                    if (u.userId == v.userId)
                        continue;
                    if (acceptsCourseAndSlot(u, v)) {
                        u.out.add(new Edge(u, v));
                    }
                }
            }

            KalenderTerminType type = list.get(0).getAngebot().getType();
            graphs.add(new Graph(groupKey, type, nodes));
        }
        return new GraphBundle(graphs);
    }

    /**
     * U akzeptiert V, wenn einer der Wünsche gleichen Kurs-Basisnamen + Typ + exakt
     * gleichen Slot hat
     */
    private boolean acceptsCourseAndSlot(Node u, Node v) {
        if (u == null || v == null || u.wishes == null || u.wishes.isEmpty() || v.angebot == null)
            return false;
        // Studiengang-Kompatibilität prüfen bei NOT_SHARED (ohne DB-Zugriffe)
        Long sgFrom = u.studiengangId;
        Long sgTo = v.studiengangId;
        String baseV = v.angebotBase;
        boolean nsFrom = u.notSharedForBase;
        boolean nsTo = v.notSharedForBase;
        if (!Objects.equals(u.angebotBase, baseV)) {
            nsFrom = (sgFrom != null) && notSharedService.isNotShared(baseV, sgFrom);
        }
        if ((nsFrom || nsTo) && !java.util.Objects.equals(sgFrom, sgTo)) {
            return false;
        }
        KalenderTerminType typeV = v.angebot.getType();
        for (KalenderTermin w : u.wishes) {
            if (w == null || w.getType() != typeV)
                continue;
            String baseW = baseName(w.getName());
            if (!Objects.equals(baseW, baseV))
                continue;
            if (sameSlot(w, v.angebot))
                return true;
        }
        return false;
    }

    private static String baseName(String full) {
        if (full == null)
            return "";
        String[] parts = full.split("\\(");
        return parts[0].trim();
    }

    private boolean sameSlot(KalenderTermin a, KalenderTermin b) {
        if (a == null || b == null || a.getStart() == null || b.getStart() == null || a.getEnd() == null
                || b.getEnd() == null)
            return false;
        Calendar ca = Calendar.getInstance();
        Calendar cb = Calendar.getInstance();
        ca.setTime(a.getStart());
        cb.setTime(b.getStart());
        int dayA = ca.get(Calendar.DAY_OF_WEEK), dayB = cb.get(Calendar.DAY_OF_WEEK);
        int shA = ca.get(Calendar.HOUR_OF_DAY), smA = ca.get(Calendar.MINUTE);
        int shB = cb.get(Calendar.HOUR_OF_DAY), smB = cb.get(Calendar.MINUTE);
        ca.setTime(a.getEnd());
        cb.setTime(b.getEnd());
        int ehA = ca.get(Calendar.HOUR_OF_DAY), emA = ca.get(Calendar.MINUTE);
        int ehB = cb.get(Calendar.HOUR_OF_DAY), emB = cb.get(Calendar.MINUTE);
        return dayA == dayB && shA == shB && smA == smB && ehA == ehB && emA == emB;
    }

    public List<Cycle> enumerate2Cycles(Graph g) {
        List<Cycle> res = new ArrayList<>();
        for (Node u : g.nodes) {
            for (Edge e1 : u.out) {
                Node v = e1.to;
                if (u.offerId < v.offerId) {
                    boolean back = v.out.stream().anyMatch(e -> e.to == u);
                    if (back)
                        res.add(new Cycle(g.groupKey(), List.of(u, v)));
                }
            }
        }
        return res;
    }

    public List<Cycle> enumerate3Cycles(Graph g) {
        List<Cycle> res = new ArrayList<>();
        Node[] nodes = g.nodes;
        for (Node u : nodes) {
            for (Edge e1 : u.out) {
                Node v = e1.to;
                if (u.offerId >= v.offerId)
                    continue;
                for (Edge e2 : v.out) {
                    Node w = e2.to;
                    if (v.offerId >= w.offerId)
                        continue;
                    boolean back = w.out.stream().anyMatch(e -> e.to == u);
                    if (back && u.userId != v.userId && v.userId != w.userId && u.userId != w.userId) {
                        res.add(new Cycle(g.groupKey(), List.of(u, v, w)));
                    }
                }
            }
        }
        return res;
    }

    static class Solution {
        int persons;
        int pairs2Count;
        int cycles3Count;
        boolean capped;
        String method; // "optimal" | "greedy"
        String warning;
        int wishSum;
        List<Cycle> chosen = new ArrayList<>();
    }

    static class BacktrackState {
        long visitedNodes = 0;
    }

    public Solution solveOptimalWithCapPerCourse(List<Cycle> cycles, int maxNodes, long startTimeMs) {
        cycles = new ArrayList<>(cycles);
        cycles.sort((a, b) -> {
            int bySize = Integer.compare(b.size(), a.size());
            if (bySize != 0)
                return bySize;
            int aw = a.nodes.stream().mapToInt(n -> n.wishCount).sum();
            int bw = b.nodes.stream().mapToInt(n -> n.wishCount).sum();
            return Integer.compare(aw, bw);
        });

        if (cycles.size() > 100) {
            Solution s = greedyPerCourse(cycles);
            s.capped = true;
            s.method = "greedy";
            s.warning = "Greedy fallback (cycle count > 100).";
            return s;
        }

        Solution best = new Solution();
        best.persons = 0;
        best.wishSum = Integer.MAX_VALUE;
        BacktrackState st = new BacktrackState();

        backtrackPerCourse(cycles, 0, new ArrayList<>(), new HashSet<>(), st, best, maxNodes, startTimeMs);

        if (st.visitedNodes > maxNodes || (System.currentTimeMillis() - startTimeMs) > TIMEOUT_MS) {
            Solution greedy = greedyPerCourse(cycles);
            greedy.capped = true;
            greedy.method = "greedy";
            greedy.warning = "Cap/Timeout reached - greedy fallback applied.";
            return greedy;
        }

        best.method = "optimal";
        return best;
    }

    private void backtrackPerCourse(List<Cycle> cycles, int idx, List<Cycle> chosen, Set<Long> usedUsers,
            BacktrackState st, Solution best, int maxNodes, long startTimeMs) {
        if (st.visitedNodes++ > maxNodes)
            return;
        if (idx == cycles.size() || (System.currentTimeMillis() - startTimeMs) > TIMEOUT_MS) {
            updateBest(chosen, best);
            return;
        }

        int rem = 0;
        for (int i = idx; i < cycles.size() && i < idx + 8; i++)
            rem += cycles.get(i).size();
        int currentPersons = sumPersons(chosen);
        if (currentPersons + rem < best.persons) {
            updateBest(chosen, best);
            return;
        }

        Cycle c = cycles.get(idx);
        if (isDisjointByUser(c, usedUsers)) {
            add(c, chosen, usedUsers);
            backtrackPerCourse(cycles, idx + 1, chosen, usedUsers, st, best, maxNodes, startTimeMs);
            remove(c, chosen, usedUsers);
        }
        backtrackPerCourse(cycles, idx + 1, chosen, usedUsers, st, best, maxNodes, startTimeMs);
    }

    private boolean isDisjointByUser(Cycle c, Set<Long> usedUsers) {
        for (Long u : c.userSet())
            if (usedUsers.contains(u))
                return false;
        return true;
    }

    private void add(Cycle c, List<Cycle> chosen, Set<Long> usedUsers) {
        chosen.add(c);
        usedUsers.addAll(c.userSet());
    }

    private void remove(Cycle c, List<Cycle> chosen, Set<Long> usedUsers) {
        chosen.remove(chosen.size() - 1);
        for (Long u : c.userSet())
            usedUsers.remove(u);
    }

    private int sumPersons(List<Cycle> chosen) {
        int s = 0;
        for (Cycle c : chosen)
            s += c.size();
        return s;
    }

    private void updateBest(List<Cycle> chosen, Solution best) {
        int persons = sumPersons(chosen);
        int wish = chosen.stream().mapToInt(c -> c.nodes.stream().mapToInt(n -> n.wishCount).sum()).sum();
        if (persons > best.persons || (persons == best.persons && wish < best.wishSum)) {
            best.persons = persons;
            best.pairs2Count = (int) chosen.stream().filter(c -> c.size() == 2).count();
            best.cycles3Count = (int) chosen.stream().filter(c -> c.size() == 3).count();
            best.wishSum = wish;
            best.chosen = new ArrayList<>(chosen);
        }
    }

    private Solution greedyPerCourse(List<Cycle> cycles) {
        cycles = new ArrayList<>(cycles);
        cycles.sort((a, b) -> {
            int bySize = Integer.compare(b.size(), a.size());
            if (bySize != 0)
                return bySize;
            int aw = a.nodes.stream().mapToInt(n -> n.wishCount).sum();
            int bw = b.nodes.stream().mapToInt(n -> n.wishCount).sum();
            return Integer.compare(aw, bw);
        });

        Set<Long> used = new HashSet<>();
        int persons = 0, c2 = 0, c3 = 0;
        List<Cycle> chosen = new ArrayList<>();
        for (Cycle c : cycles) {
            boolean ok = true;
            for (Long u : c.userSet()) {
                if (used.contains(u)) {
                    ok = false;
                    break;
                }
            }
            if (!ok)
                continue;
            used.addAll(c.userSet());
            persons += c.size();
            if (c.size() == 2)
                c2++;
            else if (c.size() == 3)
                c3++;
            chosen.add(c);
        }
        Solution s = new Solution();
        s.persons = persons;
        s.pairs2Count = c2;
        s.cycles3Count = c3;
        s.method = "greedy";
        s.capped = false;
        s.chosen = chosen;
        return s;
    }

    static class ApplyStats {
        int persons = 0;
        int pairs2 = 0;
        int cycles3 = 0;
    }

    private ApplyStats applyChosenCycles(List<Cycle> chosen) {
        ApplyStats stats = new ApplyStats();
        if (chosen == null || chosen.isEmpty())
            return stats;

        Map<Long, TauschTermin> offerById = tauschTerminRepository.findAll().stream()
                .collect(Collectors.toMap(TauschTermin::getId, x -> x, (a, b) -> a != null ? a : b));

        for (Cycle c : chosen) {
            try {
                boolean ok = prevalidateCycle(c, offerById);
                if (!ok) {
                    logger.warn("Skipping cycle due to prevalidation failure (groupKey={})", c.groupKey());
                    continue;
                }
                applySingleCycle(c, offerById);
                stats.persons += c.size();
                if (c.size() == 2)
                    stats.pairs2++;
                else if (c.size() == 3)
                    stats.cycles3++;
            } catch (Exception ex) {
                logger.error("Failed to apply cycle (groupKey={}): {}", c.groupKey(), ex.getMessage(), ex);
            }
        }
        return stats;
    }

    private boolean prevalidateCycle(Cycle c, Map<Long, TauschTermin> offerById) {
        int n = c.size();
        for (int i = 0; i < n; i++) {
            Node curr = c.nodes.get(i);
            Node next = c.nodes.get((i + 1) % n); // curr erhält Angebot von next
            User u = userRepository.findById(curr.userId).orElse(null);
            if (u == null) {
                logger.warn("prevalidate: user missing {}", curr.userId);
                return false;
            }
            Kalender cal = kalenderRepository.findByUserId(u.getId());
            if (cal == null) {
                logger.warn("prevalidate: calendar missing for user {}", u.getId());
                return false;
            }

            KalenderTermin incoming = next.angebot;
            KalenderTermin old = findOldTerminForUser(cal, incoming);
            if (old == null) {
                logger.warn("prevalidate: old term not found for user {} courseBase={} type={}",
                        u.getId(), baseName(incoming.getName()), incoming.getType());
                return false;
            }
            if (!offerById.containsKey(next.offerId)) {
                logger.warn("prevalidate: offer already removed offerId={}", next.offerId);
                return false;
            }
            // Zeitkollisionen mit anderen Terminen prüfen (außer dem alten)
            if (hasCalendarCollision(cal, old, incoming)) {
                logger.warn("prevalidate: time collision for user {} with incoming {}", u.getId(), incoming.getId());
                return false;
            }
            // Studiengang-Check bei NOT_SHARED: innerhalb eines Zyklus müssen alle
            // kompatibel sein
            try {
                String base = baseName(incoming.getName());
                Long sgCurr = u.getStudiengang() != null ? u.getStudiengang().getId() : null;
                TauschTermin donorOffer = offerById.get(next.offerId);
                Long sgDonor = null;
                if (donorOffer != null) {
                    User donor = userRepository.findById(donorOffer.getUserid()).orElse(null);
                    sgDonor = donor != null && donor.getStudiengang() != null ? donor.getStudiengang().getId() : null;
                }
                boolean nsCurr = sgCurr != null && notSharedService.isNotShared(base, sgCurr);
                boolean nsDonor = sgDonor != null && notSharedService.isNotShared(base, sgDonor);
                if ((nsCurr || nsDonor) && !java.util.Objects.equals(sgCurr, sgDonor)) {
                    logger.warn("prevalidate: incompatible studiengang for base={} curr={} donor={}", base, sgCurr,
                            sgDonor);
                    return false;
                }
            } catch (Exception ignore) {
            }
        }
        return true;
    }

    private void applySingleCycle(Cycle c, Map<Long, TauschTermin> offerById) {
        int n = c.size();

        List<User> users = new ArrayList<>(n);
        List<Kalender> calendars = new ArrayList<>(n);
        List<KalenderTermin> incomingOffers = new ArrayList<>(n);
        List<KalenderTermin> oldTerms = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            Node curr = c.nodes.get(i);
            Node next = c.nodes.get((i + 1) % n);
            User u = userRepository.findById(curr.userId).orElse(null);
            users.add(u);
            Kalender cal = kalenderRepository.findByUserId(curr.userId);
            calendars.add(cal);
            KalenderTermin incoming = next.angebot;
            incomingOffers.add(incoming);
            KalenderTermin old = findOldTerminForUser(cal, incoming);
            oldTerms.add(old);
        }

        List<KalenderTermin> newTerms = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            KalenderTermin incoming = incomingOffers.get(i);
            KalenderTermin newT = new KalenderTermin(
                    incoming.getStart(),
                    incoming.getEnd(),
                    incoming.getName(),
                    "",
                    "",
                    incoming.getType());
            newT = kalenderTerminRepository.save(newT);
            newTerms.add(newT);
        }

        // Kalender aktualisieren
        for (int i = 0; i < n; i++) {
            Kalender cal = calendars.get(i);
            KalenderTermin old = oldTerms.get(i);
            KalenderTermin newT = newTerms.get(i);
            // sichere Entfernung über ID
            cal.getTermine().removeIf(t -> Objects.equals(t.getId(), old != null ? old.getId() : null));
            cal.getTermine().add(newT);
            kalenderRepository.save(cal);
        }

        // Alte KalenderTermine löschen (best effort)
        for (int i = 0; i < n; i++) {
            KalenderTermin old = oldTerms.get(i);
            if (old != null) {
                try {
                    kalenderTerminRepository.delete(old);
                } catch (Exception ignore) {
                }
            }
        }

        // Angebots-Objekte löschen (löscht Angebot+Gesucht via Cascade, wenn
        // konfiguriert)
        for (int i = 0; i < n; i++) {
            Node donor = c.nodes.get(i);
            TauschTermin tt = offerById.get(donor.offerId);
            if (tt != null) {
                try {
                    tauschTerminRepository.delete(tt);
                } catch (Exception ex) {
                    // Fallback: Angebotstermin manuell löschen
                    try {
                        if (tt.getAngebot() != null)
                            kalenderTerminRepository.delete(tt.getAngebot());
                        tauschTerminRepository.delete(tt);
                    } catch (Exception ignore) {
                    }
                }
            }
        }

        // Overlap-Bereinigung der offenen Offers des Nutzers (Wunsch-Slots entfernen,
        // die nun gleich sind)
        for (int i = 0; i < n; i++) {
            cleanupOverlappingOffers(users.get(i), newTerms.get(i));
        }

        // Audit pro Person
        String variant = n == 3 ? "3er" : (n == 2 ? "2er" : (n + "er"));
        String personsList = users.stream().map(u -> u != null ? String.valueOf(u.getId()) : "?")
                .collect(Collectors.joining(","));
        String course = baseName(incomingOffers.get(0).getName());
        for (int i = 0; i < n; i++) {
            User u = users.get(i);
            KalenderTermin old = oldTerms.get(i);
            KalenderTermin newT = newTerms.get(i);
            String detail = String.format(Locale.ROOT,
                    "MATCH: variant=%s; persons=[%s]; course=%s; user=%d old->new=[%s -> %s]",
                    variant, personsList, course, u != null ? u.getId() : -1,
                    old != null ? termToStr(old) : "-",
                    newT != null ? termToStr(newT) : "-");
            auditService.logEvent(u != null ? u.getId() : null, AuditEventType.MATCH_SUCCESS, detail);
        }
        // E-Mail-Versand (2er/3er)
        try {
            sendCycleMails(users, oldTerms, newTerms, incomingOffers);
        } catch (Exception ex) {
            logger.warn("E-Mail-Versand fehlschlagen: {}", ex.getMessage());
        }
    }

    private KalenderTermin findOldTerminForUser(Kalender cal, KalenderTermin incoming) {
        if (cal == null || incoming == null)
            return null;
        String nameBase = baseName(incoming.getName());
        KalenderTerminType type = incoming.getType();
        for (KalenderTermin t : cal.getTermine()) {
            if (t == null)
                continue;
            String tBase = baseName(t.getName());
            if (Objects.equals(t.getType(), type) && Objects.equals(tBase, nameBase)) {
                return t;
            }
        }
        return null;
    }

    private boolean hasCalendarCollision(Kalender cal, KalenderTermin oldTermSameCourse, KalenderTermin incoming) {
        if (cal == null || incoming == null)
            return false;
        for (KalenderTermin t : cal.getTermine()) {
            if (t == null)
                continue;
            if (oldTermSameCourse != null && Objects.equals(t.getId(), oldTermSameCourse.getId()))
                continue;
            if (sameSlot(t, incoming))
                return true;
        }
        return false;
    }

    private void cleanupOverlappingOffers(User u, KalenderTermin newTermin) {
        if (u == null || newTermin == null)
            return;
        List<TauschTermin> tauschTermine = tauschTerminRepository.findTauschTerminByUserid(u.getId());
        List<TauschTermin> toDelete = new ArrayList<>();
        for (TauschTermin tt : tauschTermine) {
            if (tt.getGesucht() == null)
                continue;
            tt.getGesucht().removeIf(t -> sameSlotRelaxed(t, newTermin));
            if (tt.getGesucht().isEmpty())
                toDelete.add(tt);
        }
        try {
            tauschTerminRepository.saveAll(tauschTermine);
        } catch (Exception ignore) {
        }
        if (!toDelete.isEmpty()) {
            try {
                tauschTerminRepository.deleteAll(toDelete);
            } catch (Exception ignore) {
            }
            logger.info("Deleted overlapping Tauschtermine for user {}", u.getId());
        }
    }

    private boolean sameSlotRelaxed(KalenderTermin a, KalenderTermin b) {
        Date as = trimSeconds(a != null ? a.getStart() : null);
        Date bs = trimSeconds(b != null ? b.getStart() : null);
        Date ae = trimSeconds(a != null ? a.getEnd() : null);
        Date be = trimSeconds(b != null ? b.getEnd() : null);
        if (as == null || bs == null || ae == null || be == null)
            return false;
        return as.equals(bs) && ae.equals(be);
    }

    private Date trimSeconds(Date d) {
        if (d == null)
            return null;
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    private String termToStr(KalenderTermin t) {
        if (t == null)
            return "-";
        Calendar c = Calendar.getInstance();
        c.setTime(t.getStart());
        int sh = c.get(Calendar.HOUR_OF_DAY), sm = c.get(Calendar.MINUTE);
        c.setTime(t.getEnd());
        int eh = c.get(Calendar.HOUR_OF_DAY), em = c.get(Calendar.MINUTE);
        return String.format(Locale.ROOT, "%s %02d:%02d-%02d:%02d", baseName(t.getName()), sh, sm, eh, em);
    }

    private Date createDate(int dayOfWeek, int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.DAY_OF_WEEK, dayOfWeek);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    @Transactional
    public void setupTestData3CycleMatch() {
        String[] emails = { "maximilia1.musterata1@student.hs-rm.de", "maximilia2.musterata2@student.hs-rm.de",
                "maximilia3.musterata3@student.hs-rm.de" };
        String coursePrefix = "TEST";
        String[] groups = { "A", "B", "C" };

        String[] starts = { "08:15", "10:00", "11:45", "14:15", "16:00", "17:45", "19:30" };
        String[] ends = { "09:45", "11:30", "13:15", "15:45", "17:30", "19:15", "21:00" };
        int[] indices = { 4, 5, 6 }; // hintere 3 Slots

        for (String email : emails) {
            Optional<User> opt = userRepository.findByHsMail(email);
            if (opt.isPresent()) {
                User u = opt.get();
                List<Kalender> cals = kalenderRepository.findAllByUserId(u.getId());
                for (Kalender c : cals) {
                    if (c.getTermine() != null && !c.getTermine().isEmpty()) {
                        c.getTermine().clear();
                        kalenderRepository.save(c);
                    }
                }
                List<team.boerse.tauschboerse.TauschTermin> tt = tauschTerminRepository
                        .findTauschTerminByUserid(u.getId());
                if (tt != null && !tt.isEmpty()) {
                    tauschTerminRepository.deleteAll(tt);
                }
            }
        }

        for (int i = 0; i < 3; i++) {
            Optional<User> opt = userRepository.findByHsMail(emails[i]);
            User user;
            if (opt.isEmpty()) {
                user = new User(emails[i], emails[i].replace("@student.hs-rm.de", "@example.com"), false, "");
                user = userRepository.save(user);
            } else {
                user = opt.get();
            }

            Kalender cal = kalenderRepository.findByUserId(user.getId());
            if (cal == null) {
                cal = new Kalender();
                cal.setUserId(user.getId());
            } else if (cal.getTermine() != null && !cal.getTermine().isEmpty()) {
                cal.getTermine().clear();
            }

            int idx = indices[i];
            int sh = Integer.parseInt(starts[idx].split(":")[0]);
            int sm = Integer.parseInt(starts[idx].split(":")[1]);
            int eh = Integer.parseInt(ends[idx].split(":")[0]);
            int em = Integer.parseInt(ends[idx].split(":")[1]);

            Date start = createDate(Calendar.FRIDAY, sh, sm);
            Date end = createDate(Calendar.FRIDAY, eh, em);

            String name = coursePrefix + " (P-" + groups[i] + ")";
            KalenderTermin term = new KalenderTermin(start, end, name, "", "", KalenderTerminType.P);
            term = kalenderTerminRepository.save(term);
            cal.getTermine().add(term);

            kalenderRepository.save(cal);

            logger.info(
                    "Inserted test calendar term for user {} (id={}): {} {}-{}",
                    emails[i], user.getId(), name, starts[idx], ends[idx]);
        }
    }

    private void sendCycleMails(List<User> users, List<KalenderTermin> oldTerms, List<KalenderTermin> newTerms,
            List<KalenderTermin> incomingOffers) {
        if (users == null || oldTerms == null || newTerms == null || incomingOffers == null)
            return;
        int n = users.size();
        if (n < 2)
            return;
        String course = baseName(incomingOffers.get(0).getName());

        String[] names = new String[n];
        String[] hs = new String[n];
        String[] priv = new String[n];
        for (int i = 0; i < n; i++) {
            User u = users.get(i);
            hs[i] = u != null ? u.getHsMail() : null;
            priv[i] = u != null ? emptyToNull(u.getPrivateMail()) : null;
            names[i] = hs[i] != null ? extractName(hs[i]) : (u != null ? String.valueOf(u.getId()) : ("P" + i));
        }

        int designated = 0;

        String subject = buildMailtoSubject(course);
        String mailtoBody = buildMailtoBodyForCycle(course, names, oldTerms, newTerms);

        for (int i = 0; i < n; i++) {
            String mailto = null;
            if (n == 2 || (n == 3 && i == designated)) {
                java.util.List<String> ccList = new ArrayList<>();
                for (int j = 0; j < n; j++) {
                    if (j == i)
                        continue;
                    if (hs[j] != null)
                        ccList.add(hs[j]);
                }
                mailto = MailUtils.createMailtoLink("", ccList, subject, mailtoBody);
            }

            String text = composeSystemMailForUser(i, names, hs, oldTerms, newTerms, course, designated, mailto);

            String to = hs[i];
            String cc = priv[i];
            if (to != null && !to.isBlank()) {
                MailUtils.sendMail(to, cc, "Erfolgreiche Vermittlung: " + course, text);
            }
        }
    }

    private String composeSystemMailForUser(int idx, String[] names, String[] hs,
            List<KalenderTermin> oldTerms, List<KalenderTermin> newTerms, String course, int designated,
            String mailtoLink) {
        int n = names.length;
        StringBuilder sb = new StringBuilder();
        sb.append("Die Tauschterminvermittlung war erfolgreich!\n\n");
        if (n == 2) {
            int other = 1 - idx;
            sb.append("Kurs: ").append(course).append("\n");
            sb.append("Tauschübersicht:\n");
            sb.append("- ").append(names[idx]).append(": ")
                    .append(formatCourseGroupAndSlot(oldTerms.get(idx))).append(" -> ")
                    .append(formatCourseGroupAndSlot(newTerms.get(idx))).append("\n");
            sb.append("- ").append(names[other]).append(": ")
                    .append(formatCourseGroupAndSlot(oldTerms.get(other))).append(" -> ")
                    .append(formatCourseGroupAndSlot(newTerms.get(other))).append("\n\n");
            sb.append("Kontaktdaten Tauschpartner/in: ").append(hs[other] != null ? hs[other] : "-").append("\n\n");
            sb.append("Bitte informiert eure Kursleitung über den Tausch. ");
            sb.append("Unverbindliche Vorlage: <a href=\"").append(mailtoLink).append("\">Vorlage öffnen</a>\n\n");
        } else if (n == 3) {
            sb.append("Kurs: ").append(course).append(" (3er-Zirkel)\n");
            sb.append("Kurze Erklärung: In einem 3er-Zirkel erhält jede Person den Termin einer anderen Person.\n\n");
            sb.append("Die Änderungen:\n");
            for (int i = 0; i < 3; i++) {
                sb.append("- ").append(names[i]).append(": ")
                        .append(formatCourseGroupAndSlot(oldTerms.get(i))).append(" -> ")
                        .append(formatCourseGroupAndSlot(newTerms.get(i))).append("\n");
            }
            sb.append("\n");
            if (mailtoLink != null && !mailtoLink.isBlank() && idx == designated) {
                sb.append("Bitte schreibe die E-Mail an die Kursleitung und setze die beiden anderen in CC. ");
                sb.append("Unverbindliche Vorlage: <a href=\"").append(mailtoLink).append("\">Vorlage öffnen</a>\n\n");
            } else {
                sb.append("Die E-Mail an die Kursleitung übernimmt ").append(names[designated])
                        .append(". ").append(names[designated])
                        .append(" hat dafür von uns eine unverbindliche Vorlage erhalten.\n\n");
            }
            sb.append("Kontaktdaten:\n");
            for (int i = 0; i < 3; i++) {
                if (i == idx)
                    continue;
                sb.append("- ").append(names[i]).append(": ").append(hs[i] != null ? hs[i] : "-").append("\n");
            }
            sb.append("\n");
        } else {
            // k > 3
            sb.append("Kurs: ").append(course).append(" (Zirkelgröße ").append(n).append(")\n");
            sb.append("Änderungen:\n");
            for (int i = 0; i < n; i++) {
                sb.append("- ").append(names[i]).append(": ")
                        .append(formatCourseGroupAndSlot(oldTerms.get(i))).append(" -> ")
                        .append(formatCourseGroupAndSlot(newTerms.get(i))).append("\n");
            }
            sb.append("\n");
            sb.append("Unverbindliche Vorlage: <a href=\"").append(mailtoLink).append("\">Vorlage öffnen</a>\n\n");
        }
        sb.append("Hat dir die Tauschbörse geholfen? Feedback: https://tauschboerse.nkwebservices.de/#bewertungen");
        return sb.toString();
    }

    private String buildMailtoSubject(String course) {
        String base = course != null ? course : "";
        return pick(
                "Gruppentausch " + base,
                "Gruppenwechsel " + base,
                "Gruppentausch Lehrveranstaltung " + base);
    }

    private String buildMailtoBodyForCycle(String course, String[] names,
            List<KalenderTermin> oldTerms, List<KalenderTermin> newTerms) {
        StringBuilder b = new StringBuilder();
        b.append(pick("Sehr geehrte Damen und Herren,", "Hallo,", "Guten Tag,")).append("\n\n");
        b.append("wir bitten um Eintragung eines Gruppentauschs in der Lehrveranstaltung ")
                .append(course != null ? course : "").append(".\n\n");
        if (names.length == 3) {
            b.append("Es handelt sich um einen 3er-Zirkeltausch.\n\n");
        }
        b.append("Überblick der Änderungen:\n");
        for (int i = 0; i < names.length; i++) {
            b.append("- ").append(names[i]).append(": ")
                    .append(formatCourseGroupAndSlot(oldTerms.get(i))).append(" -> ")
                    .append(formatCourseGroupAndSlot(newTerms.get(i))).append("\n");
        }
        b.append("\n").append(pick(
                "Wir würden uns sehr freuen, wenn der Tausch möglich wäre.",
                "Wir hoffen auf eine positive Rückmeldung.",
                "Vielen Dank für Ihre Unterstützung!")).append("\n\n");
        b.append("Mit freundlichen Grüßen");
        return b.toString();
    }

    private String formatCourseGroupAndSlot(KalenderTermin t) {
        if (t == null)
            return "-";
        String base = baseName(t.getName());
        String grp = groupOf(t);
        String coursePart = grp.isEmpty() ? base : (base + " (Gruppe " + grp + ")");
        return coursePart + ": " + slotToStr(t);
    }

    private String groupOf(KalenderTermin t) {
        if (t == null || t.getName() == null)
            return "";
        String name = t.getName();
        int lp = name.lastIndexOf('(');
        int rp = name.lastIndexOf(')');
        if (lp >= 0 && rp > lp) {
            String inside = name.substring(lp + 1, rp);
            int dash = inside.lastIndexOf('-');
            if (dash >= 0 && dash + 1 < inside.length()) {
                return inside.substring(dash + 1).trim();
            }
        }
        return "";
    }

    private String pick(String... options) {
        if (options == null || options.length == 0)
            return "";
        int idx = (int) (Math.random() * options.length);
        return options[idx];
    }

    private String slotToStr(KalenderTermin t) {
        if (t == null)
            return "-";
        Calendar c = Calendar.getInstance();
        c.setTime(t.getStart());
        String[] days = { "Sonntag", "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag" };
        String day = days[c.get(Calendar.DAY_OF_WEEK) - 1];
        String start = String.format(Locale.ROOT, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
        c.setTime(t.getEnd());
        String end = String.format(Locale.ROOT, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
        return day + " " + start + "-" + end;
    }

    private static String extractName(String adresse) {
        if (adresse == null || !adresse.contains("@") || !adresse.contains("."))
            return "Studierende/r";
        String namensTeil = adresse.split("@")[0];
        String[] name = namensTeil.split("\\.");
        if (name.length < 2)
            return namensTeil;
        String vorname = name[0].isEmpty() ? "" : (Character.toUpperCase(name[0].charAt(0)) + name[0].substring(1));
        String nachname = name[1].isEmpty() ? "" : (Character.toUpperCase(name[1].charAt(0)) + name[1].substring(1));
        return (vorname + " " + nachname).trim();
    }

    private String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

}
