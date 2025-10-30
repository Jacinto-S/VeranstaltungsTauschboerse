package team.boerse.tauschboerse.feedback;

import java.util.Date;

import org.springframework.data.annotation.CreatedDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;
import team.boerse.tauschboerse.User;

@Setter
@Getter
@Entity
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    long id;
    @CreatedDate
    Date createDate;

    long creator;
    int rating = 5;

    boolean isPublic;

    @Column(columnDefinition = "TEXT", length = 10000)
    String feedback;

    public Feedback() {
    }

    public Feedback(long creator, boolean isPublic, String feedback, int rating) {
        this.creator = creator;
        this.isPublic = isPublic;
        this.feedback = feedback;
        this.createDate = new Date();
        this.rating = rating;
    }

    public Feedback(User creator, boolean isPublic, String feedback, int rating) {
        this.creator = creator.getId();
        this.isPublic = isPublic;
        this.feedback = feedback;
        this.createDate = new Date();
        this.rating = rating;
    }

}
