package team.boerse.tauschboerse;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByHsMail(String hsMail);

    @Query("SELECT u FROM User u JOIN u.accessToken a WHERE a = :accessToken")
    User findByAccessToken(String accessToken);
}
