package team.boerse.tauschboerse;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByHsMail(String hsMail);

    Page<User> findByHsMailContainingIgnoreCase(String hsMail, Pageable pageable);

    @Query("SELECT u FROM User u JOIN u.accessToken a WHERE a = :accessToken")
    User findByAccessToken(String accessToken);
}
