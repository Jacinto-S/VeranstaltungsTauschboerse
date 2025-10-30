package team.boerse.tauschboerse.suggestions;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import team.boerse.tauschboerse.KalenderRepository;
import team.boerse.tauschboerse.KalenderTerminType;
import team.boerse.tauschboerse.studiengang.NotSharedVeranstaltung;
import team.boerse.tauschboerse.studiengang.NotSharedVeranstaltungRepository;

@Service
@RequiredArgsConstructor
public class GroupSuggestionService {

    private final KalenderRepository kalenderRepository;
    private final NotSharedVeranstaltungRepository notSharedRepository;

    private final Logger logger = LoggerFactory.getLogger(GroupSuggestionService.class);

    /**
     * key variants:
     * - shared: baseName|type
     * - not-shared: baseName|type|sg:<id>
     */
    private volatile Map<String, List<SuggestedSlot>> cache = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> notSharedCache = new ConcurrentHashMap<>();

    public record SlotKey(int dayIndex, String start, String end) {
    }

    @Getter
    public static class SuggestedSlot {
        private final int dayIndex; // 0..4 (Mo..Fr)
        private final String start; // HH:mm
        private final String end; // HH:mm
        private final String group; // z.B. "A"; kann leer sein
        private final int support; // Anzahl eindeutiger Nutzer

        public SuggestedSlot(int dayIndex, String start, String end, String group, int support) {
            this.dayIndex = dayIndex;
            this.start = start;
            this.end = end;
            this.group = group == null ? "" : group;
            this.support = support;
        }
    }

    private static final String[] STD_STARTS = { "08:15", "10:00", "11:45", "14:15", "16:00", "17:45", "19:30" };
    private static final String[] STD_ENDS = { "09:45", "11:30", "13:15", "15:45", "17:30", "19:15", "21:00" };
    private static final Set<String> STD_SLOTS = buildStdSlots();

    private static Set<String> buildStdSlots() {
        Set<String> s = new HashSet<>();
        for (int i = 0; i < STD_STARTS.length; i++) {
            s.add(STD_STARTS[i] + "|" + STD_ENDS[i]);
        }
        return s;
    }

    public void rebuildCache() {
        long t0 = System.currentTimeMillis();

        notSharedCache.clear();

        Map<String, List<SuggestedSlot>> newCache = new HashMap<>();

        // Schlanke Projektion direkt aus der DB
        List<team.boerse.tauschboerse.suggestions.SuggestionTermRow> rows = new ArrayList<>();
        try {
            rows = kalenderRepository.findAllTermsForSuggestions();
        } catch (Exception ex) {
            logger.warn("Failed to load projection for suggestions: {}", ex.getMessage());
        }
        Map<String, Map<SlotKey, Map<String, Set<Long>>>> counts = new HashMap<>();

        Calendar c = Calendar.getInstance();
        for (team.boerse.tauschboerse.suggestions.SuggestionTermRow r : rows) {
            if (r == null || r.getType() == null)
                continue;
            if (r.getType() == KalenderTerminType.V)
                continue; // Vorlesungen nicht relevant
            if (r.getStart() == null || r.getEnd() == null)
                continue;

            String base = baseName(r.getName());
            if (base.isEmpty())
                continue;

            c.setTime(r.getStart());
            int dayIndex = c.get(Calendar.DAY_OF_WEEK) - 2; // Mo=0..Fr=4
            if (dayIndex < 0 || dayIndex > 4)
                continue; // nur Mo-Fr

            String start = String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
            c.setTime(r.getEnd());
            String end = String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));

            if (!STD_SLOTS.contains(start + "|" + end))
                continue;
            Long sgId = r.getStudiengangId();
            boolean notShared = (sgId != null) && isNotShared(base, sgId);
            String courseKey = notShared ? (base + "|" + r.getType().name() + "|sg:" + sgId)
                    : (base + "|" + r.getType().name());
            String group = groupOf(r.getName());
            SlotKey sk = new SlotKey(dayIndex, start, end);

            Map<SlotKey, Map<String, Set<Long>>> slotMap = counts.get(courseKey);
            if (slotMap == null) {
                slotMap = new HashMap<>();
                counts.put(courseKey, slotMap);
            }
            Map<String, Set<Long>> groupMap = slotMap.get(sk);
            if (groupMap == null) {
                groupMap = new HashMap<>();
                slotMap.put(sk, groupMap);
            }
            Set<Long> users = groupMap.get(group);
            if (users == null) {
                users = new HashSet<>();
                groupMap.put(group, users);
            }
            users.add(r.getUserId());
        }

        for (Map.Entry<String, Map<SlotKey, Map<String, Set<Long>>>> e : counts.entrySet()) {
            String courseKey = e.getKey();
            Map<SlotKey, Map<String, Set<Long>>> slotMap = e.getValue();
            List<SuggestedSlot> list = new ArrayList<>();

            for (Map.Entry<SlotKey, Map<String, Set<Long>>> se : slotMap.entrySet()) {
                SlotKey sk = se.getKey();
                Map<String, Set<Long>> groupMap = se.getValue();

                String bestGroup = "";
                int bestCount = 0;
                for (Map.Entry<String, Set<Long>> ge : groupMap.entrySet()) {
                    int support = ge.getValue() != null ? ge.getValue().size() : 0;
                    if (support > bestCount) {
                        bestCount = support;
                        bestGroup = ge.getKey() == null ? "" : ge.getKey();
                    }
                }

                boolean groupKnown = bestGroup != null && !bestGroup.isBlank();
                int threshold = groupKnown ? 1 : 2; // unsichere Fälle brauchen 2 Bestätigungen
                if (bestCount >= threshold) {
                    list.add(new SuggestedSlot(sk.dayIndex(), sk.start(), sk.end(), bestGroup, bestCount));
                }
            }

            Map<String, List<SuggestedSlot>> byGroup = new HashMap<>();
            for (SuggestedSlot s : list) {
                String g = s.getGroup() == null ? "" : s.getGroup().trim();
                byGroup.putIfAbsent(g, new ArrayList<>());
                byGroup.get(g).add(s);
            }

            List<SuggestedSlot> filtered = new ArrayList<>();
            for (Map.Entry<String, List<SuggestedSlot>> ge : byGroup.entrySet()) {
                String group = ge.getKey();
                List<SuggestedSlot> slots = ge.getValue();
                if (group.isEmpty()) {
                    filtered.addAll(slots);
                    continue;
                }
                if (slots.size() == 1) {
                    filtered.addAll(slots);
                } else {
                    int max = slots.stream().mapToInt(SuggestedSlot::getSupport).max().orElse(0);
                    List<SuggestedSlot> top = new ArrayList<>();
                    for (SuggestedSlot s : slots)
                        if (s.getSupport() == max)
                            top.add(s);
                    if (top.size() == 1 && max >= 2) {
                        filtered.add(top.get(0));
                    } else {
                        logger.info(
                                "Suggestion suppressed due to ambiguity: courseKey={} group={} candidates={} maxSupport={}",
                                courseKey, group, slots.size(), max);
                    }
                }
            }

            filtered.sort(Comparator.comparingInt(SuggestedSlot::getDayIndex)
                    .thenComparing(SuggestedSlot::getStart));
            newCache.put(courseKey, filtered);
        }

        cache = newCache;
        logger.info("Rebuilt group suggestions: {} courses in {} ms", cache.size(), (System.currentTimeMillis() - t0));
    }

    public List<SuggestedSlot> getSuggestionsForCourse(String courseBase, KalenderTerminType type) {
        if (courseBase == null || type == null)
            return List.of();
        if (cache.isEmpty()) {
            try {
                rebuildCache();
            } catch (Exception ex) {
                logger.warn("Failed to rebuild suggestions on-demand: {}", ex.getMessage());
            }
        }
        String key = courseBase.trim() + "|" + type.name();
        List<SuggestedSlot> list = cache.get(key);
        return list != null ? list : List.of();
    }

    public List<SuggestedSlot> getSuggestionsForCourse(String courseBase, KalenderTerminType type, Long studiengangId) {
        if (courseBase == null || type == null)
            return List.of();
        if (cache.isEmpty()) {
            try {
                rebuildCache();
            } catch (Exception ex) {
                logger.warn("Failed to rebuild suggestions on-demand: {}", ex.getMessage());
            }
        }
        String base = courseBase.trim();
        if (studiengangId != null && isNotShared(base, studiengangId)) {
            String key = base + "|" + type.name() + "|sg:" + studiengangId;
            List<SuggestedSlot> list = cache.get(key);
            return list != null ? list : List.of();
        }
        return getSuggestionsForCourse(base, type);
    }

    private boolean isNotShared(String baseName, Long studiengangId) {
        if (baseName == null || studiengangId == null)
            return false;
        Set<String> set = notSharedCache.computeIfAbsent(studiengangId, this::loadNotSharedSet);
        return set.contains(baseName.trim().toLowerCase(Locale.ROOT));
    }

    private Set<String> loadNotSharedSet(Long studiengangId) {
        List<NotSharedVeranstaltung> list = notSharedRepository.findByStudiengang_Id(studiengangId);
        Set<String> s = new HashSet<>();
        for (NotSharedVeranstaltung n : list) {
            if (n.getVeranstaltungBaseName() != null) {
                s.add(n.getVeranstaltungBaseName().trim().toLowerCase(Locale.ROOT));
            }
        }
        return s;
    }

    public void invalidateCacheForStudiengang(Long studiengangId) {
        if (studiengangId != null) {
            notSharedCache.remove(studiengangId);
        }
        cache.clear();
        try {
            rebuildCache();
        } catch (Exception ex) {
            logger.warn("Failed to rebuild suggestions after invalidation: {}", ex.getMessage());
        }
    }

    private static String baseName(String full) {
        if (full == null)
            return "";
        String[] parts = full.split("\\(");
        return parts[0].trim();
    }

    private static String groupOf(String name) {
        if (name == null)
            return "";
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
}
