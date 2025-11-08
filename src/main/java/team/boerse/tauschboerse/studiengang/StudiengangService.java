package team.boerse.tauschboerse.studiengang;

import java.util.List;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StudiengangService {
    private final StudiengangRepository repo;
    private final NotSharedService notSharedService;

    public List<Studiengang> findAll() {
        return repo.findAll();
    }

    public Studiengang create(String name, String shortCode) {
        Studiengang s = new Studiengang();
        s.setName(name);
        s.setShortCode(shortCode);
        return repo.save(s);
    }

    public Studiengang update(Long id, String name, String shortCode) {
        Studiengang s = repo.findById(id).orElseThrow();
        if (name != null && !name.isBlank())
            s.setName(name);
        if (shortCode != null && !shortCode.isBlank())
            s.setShortCode(shortCode);
        return repo.save(s);
    }

    public void delete(Long id) {
        notSharedService.deleteByStudiengang(id);
        repo.deleteById(id);
    }
}
