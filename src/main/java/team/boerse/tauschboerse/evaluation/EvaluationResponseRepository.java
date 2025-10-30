package team.boerse.tauschboerse.evaluation;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface EvaluationResponseRepository extends JpaRepository<EvaluationResponse, Long> {

    Optional<EvaluationResponse> findByUserIdAndSemesterId(Long userId, String semesterId);

    List<EvaluationResponse> findBySemesterId(String semesterId);

    List<EvaluationResponse> findBySemesterIdAndSubmittedTrue(String semesterId);

    @Query("SELECT DISTINCT r.semesterId FROM EvaluationResponse r ORDER BY r.semesterId DESC")
    List<String> findDistinctSemesterIds();

}
