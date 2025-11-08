package team.boerse.tauschboerse;

import java.util.Date;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import lombok.Getter;

@Getter
@Entity
@Table(indexes = {
        @Index(name = "idx_tausch_user", columnList = "userid"),
        @Index(name = "idx_tausch_angebot", columnList = "angebot_id"),
        @Index(name = "idx_tausch_created", columnList = "createdDate")
})
public class TauschTermin {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    long id;

    long userid;
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    KalenderTermin angebot;
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    List<KalenderTermin> gesucht;

    @CreatedDate
    Date createdDate;

    public TauschTermin(long userid, KalenderTermin angebot, List<KalenderTermin> gesucht) {
        this.userid = userid;
        this.angebot = angebot;
        this.gesucht = gesucht;
        this.createdDate = new Date();
    }

    public TauschTermin() {

    }

}
