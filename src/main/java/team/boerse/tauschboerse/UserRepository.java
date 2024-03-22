package team.boerse.tauschboerse;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByHsMail(String hsMail);

    User findByAccessToken(String accessToken);
}
