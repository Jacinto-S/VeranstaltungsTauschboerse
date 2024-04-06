package team.boerse.tauschboerse.feedback;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    Page<Feedback> findAllByIsPublicTrueOrderByCreateDateDesc(Pageable pageable);

    Page<Feedback> findAllByOrderByCreateDateDesc(Pageable pageable);

}
