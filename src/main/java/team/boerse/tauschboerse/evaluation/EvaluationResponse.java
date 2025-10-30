package team.boerse.tauschboerse.evaluation;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "evaluation_response", indexes = {
        @Index(name = "idx_user_semester", columnList = "user_id, semester_id", unique = true),
        @Index(name = "idx_semester", columnList = "semester_id")
})
public class EvaluationResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "semester_id", nullable = false)
    private String semesterId;

    @Column(nullable = false)
    private Boolean submitted = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    // A. Allgemeine Nutzung
    @Column(name = "semester_count", length = 10)
    private String semesterCount;

    @Column(name = "hear_about", length = 500)
    private String hearAbout; // JSON array stored as string

    @Column(name = "found_outside", length = 50)
    private String foundOutside;

    @Column(name = "deleted_offers", length = 50)
    private String deletedOffers;

    // B. Benutzerfreundlichkeit
    @Column(name = "ease_of_use")
    private Integer easeOfUse;

    @Column(name = "intuitive_functions")
    private Integer intuitiveFunctions;

    @Column(name = "login_reliability")
    private Integer loginReliability;

    @Column(name = "email_satisfaction", length = 20)
    private String emailSatisfaction; // "1"-"5" or "not_used"

    // C. Rundentausch
    @Column(name = "round_exchange_understanding")
    private Integer roundExchangeUnderstanding;

    @Column(name = "round_exchange_advantage")
    private Integer roundExchangeAdvantage;

    @Column(name = "trade_off_preference", length = 50)
    private String tradeOffPreference;

    // D. Gesamtzufriedenheit
    @Column(name = "overall_satisfaction")
    private Integer overallSatisfaction;

    @Column(name = "would_recommend", length = 10)
    private String wouldRecommend;

    @Column(name = "would_use_again", length = 50)
    private String wouldUseAgain;

    // E. Offenes Feedback
    @Column(name = "frustrations", columnDefinition = "TEXT", length = 1500)
    private String frustrations;

    @Column(name = "suggestions", columnDefinition = "TEXT", length = 1500)
    private String suggestions;

    public EvaluationResponse() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public EvaluationResponse(Long userId, String semesterId) {
        this();
        this.userId = userId;
        this.semesterId = semesterId;
    }

    public void submit() {
        this.submitted = true;
        this.submittedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public boolean isComplete() {
        return semesterCount != null
                && hearAbout != null && !hearAbout.isEmpty()
                && foundOutside != null
                && deletedOffers != null
                && easeOfUse != null
                && intuitiveFunctions != null
                && loginReliability != null
                && emailSatisfaction != null
                && roundExchangeUnderstanding != null
                && roundExchangeAdvantage != null
                && tradeOffPreference != null
                && overallSatisfaction != null
                && wouldRecommend != null
                && wouldUseAgain != null;
    }
}
