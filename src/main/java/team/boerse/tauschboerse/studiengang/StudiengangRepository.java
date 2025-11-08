package team.boerse.tauschboerse.studiengang;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudiengangRepository extends JpaRepository<Studiengang, Long> {
    Optional<Studiengang> findByShortCodeIgnoreCase(String shortCode);

    Optional<Studiengang> findByNameIgnoreCase(String name);
}
