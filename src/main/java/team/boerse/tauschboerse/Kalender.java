package team.boerse.tauschboerse;

import java.util.List;

import org.springframework.data.annotation.CreatedDate;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;

import java.util.ArrayList;
import java.util.Date;

@Entity
public class Kalender {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    long userId;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    List<KalenderTermin> termine = new ArrayList<>();

    @CreatedDate
    Date createDate;

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public List<KalenderTermin> getTermine() {
        return termine;
    }

    public void setTermine(List<KalenderTermin> termine) {
        this.termine = termine;
    }

    public Date getCreateDate() {
        return createDate;
    }
}
