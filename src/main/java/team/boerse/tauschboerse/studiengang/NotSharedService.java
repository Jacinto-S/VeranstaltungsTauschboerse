package team.boerse.tauschboerse.studiengang;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import team.boerse.tauschboerse.KalenderTerminRepository;

@Service
@RequiredArgsConstructor
public class NotSharedService {
    private final NotSharedVeranstaltungRepository notSharedRepo;
    private final StudiengangRepository studiengangRepository;
    private final KalenderTerminRepository kalenderTerminRepository;

    private final Map<Long, Set<String>> cache = new ConcurrentHashMap<>();

    public boolean isNotShared(String baseName, Long studiengangId) {
        if (baseName == null || studiengangId == null)
            return false;
        Set<String> set = cache.computeIfAbsent(studiengangId, this::loadSet);
        String key = normalizeKurzel(baseName);
        return set.contains(key);
    }

    private Set<String> loadSet(Long studiengangId) {
        List<NotSharedVeranstaltung> list = notSharedRepo.findByStudiengang_Id(studiengangId);
        Set<String> s = new HashSet<>();
        for (NotSharedVeranstaltung n : list) {
            if (n.getVeranstaltungBaseName() != null) {
                s.add(normalizeKurzel(n.getVeranstaltungBaseName()));
            }
        }
        return s;
    }

    private String normalizeKurzel(String nameOrKurzel) {
        String v = nameOrKurzel == null ? "" : nameOrKurzel.trim().toLowerCase(Locale.ROOT);
        int sp = v.indexOf(' ');
        return sp > 0 ? v.substring(0, sp) : v;
    }

    public List<String> getNotSharedForStudiengang(Long studiengangId) {
        List<NotSharedVeranstaltung> list = notSharedRepo.findByStudiengang_Id(studiengangId);
        List<String> res = new ArrayList<>();
        for (NotSharedVeranstaltung n : list) {
            res.add(n.getVeranstaltungBaseName());
        }
        return res;
    }

    public List<String> getAvailableKurzelForStudiengang(Long studiengangId) {
        if (studiengangId == null) {
            return new ArrayList<>();
        }
        List<NotSharedVeranstaltung> list = notSharedRepo.findByStudiengang_Id(studiengangId);
        return list.stream()
                .map(NotSharedVeranstaltung::getVeranstaltungBaseName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    public NotSharedVeranstaltung addNotShared(Long studiengangId, String baseName) {
        if (studiengangId == null || baseName == null || baseName.isBlank())
            throw new IllegalArgumentException("studiengangId/baseName required");
        if (notSharedRepo.existsByStudiengang_IdAndVeranstaltungBaseNameIgnoreCase(studiengangId, baseName))
            return null;
        Studiengang sg = studiengangRepository.findById(studiengangId).orElseThrow();
        NotSharedVeranstaltung e = new NotSharedVeranstaltung();
        e.setStudiengang(sg);
        e.setVeranstaltungBaseName(baseName.trim());
        e = notSharedRepo.save(e);
        cache.remove(studiengangId);
        return e;
    }

    public void removeNotShared(Long id) {
        NotSharedVeranstaltung e = notSharedRepo.findById(id).orElse(null);
        if (e != null) {
            Long sid = e.getStudiengang() != null ? e.getStudiengang().getId() : null;
            notSharedRepo.deleteById(id);
            if (sid != null)
                cache.remove(sid);
        }
    }

    public void deleteByStudiengang(Long studiengangId) {
        notSharedRepo.deleteByStudiengang_Id(studiengangId);
        cache.remove(studiengangId);
    }

    public Map<String, Object> getKurzelAnalysis(Long studiengangId) {
        Map<String, Object> result = new HashMap<>();
        List<String> allKurzel = kalenderTerminRepository.getAllDistinctKurzelByStudiengang(studiengangId);

        List<String> notSharedKurzel = getAvailableKurzelForStudiengang(studiengangId);

        result.put("allKurzel", allKurzel != null ? allKurzel : new ArrayList<>());
        result.put("notSharedKurzel", notSharedKurzel != null ? notSharedKurzel : new ArrayList<>());

        return result;
    }

}
