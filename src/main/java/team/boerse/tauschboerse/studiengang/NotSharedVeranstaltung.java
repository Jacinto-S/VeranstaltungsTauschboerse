package team.boerse.tauschboerse.studiengang;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(indexes = {
        @Index(name = "idx_notshared_stg", columnList = "studiengang_id"),
        @Index(name = "idx_notshared_unique", columnList = "studiengang_id, veranstaltungBaseName", unique = true)
})
public class NotSharedVeranstaltung {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "studiengang_id")
    private Studiengang studiengang;

    @Column(nullable = false)
    private String veranstaltungBaseName;
}
