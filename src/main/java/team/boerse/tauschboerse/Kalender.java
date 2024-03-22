package team.boerse.tauschboerse;

import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;

import java.util.ArrayList;

@Entity
public class Kalender {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    long userId;

    @OneToMany(cascade = CascadeType.ALL)
    List<KalenderTermin> termine = new ArrayList<>();

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
}
