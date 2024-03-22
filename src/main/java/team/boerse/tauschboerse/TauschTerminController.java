package team.boerse.tauschboerse;

import java.util.List;
import java.util.ArrayList;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.transaction.Transactional;
import team.boerse.tauschboerse.mail.MailUtils;

import java.util.Calendar;

@RestController
public class TauschTerminController {

    @Autowired
    private KalenderRepository kalenderRepository;

    @Autowired
    private TauschTerminRepository tauschTerminRepository;
    @Autowired
    private KalenderTerminRepository kalenderTerminRepository;

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/removeMyOffers")
    public ResponseEntity<String> removeMyOffers() {
        User user = UserUtil.getUser();
        if (user == null) {
            return ResponseEntity.badRequest().body("User not logged in");
        }
        List<TauschTermin> termine = tauschTerminRepository.findTauschTerminByUserid(user.getId());
        for (TauschTermin termin : termine) {
            tauschTerminRepository.delete(termin);
        }
        return ResponseEntity.ok().build();
    }

    @Transactional
    @GetMapping("/acceptOffer")
    public ResponseEntity<String> acceptOffer(@RequestParam long selectedTermin) {
        User user = UserUtil.getUser();
        if (user == null) {
            return ResponseEntity.badRequest().body("User not logged in");
        }
        KalenderTermin kalenderTermin = kalenderTerminRepository.findById(selectedTermin).orElse(null);
        if (kalenderTermin == null) {
            return ResponseEntity.badRequest().body("KalenderTermin not found");
        }
        TauschTermin tauschTermin = tauschTerminRepository.findTauschTerminByAngebot(kalenderTermin);
        if (tauschTermin == null) {
            return ResponseEntity.badRequest().body("TauschTermin not found");
        }

        if (tauschTermin.userid == user.getId()) {
            return ResponseEntity.badRequest().body("User not allowed to accept this offer");
        }

        User tauschPartner = userRepository.findById(tauschTermin.userid).orElse(null);
        if (tauschPartner == null) {
            return ResponseEntity.badRequest().body("TauschPartner not found");
        }

        // Erstelle neue Termine
        KalenderTermin newTerminForUser = new KalenderTermin(kalenderTermin.getStart(), kalenderTermin.getEnd(),
                kalenderTermin.getName(), "", "",
                KalenderController.getKalenderType(kalenderTermin.getName()));
        newTerminForUser = kalenderTerminRepository.save(newTerminForUser);
        Kalender kalenderUser = kalenderRepository.findByUserId(user.getId());

        KalenderTermin oldTerminOfUser = null;
        String kalenderTerminName = kalenderTermin.getName().split("\\(")[0];
        for (KalenderTermin termin : kalenderUser.getTermine()) {
            String terminname = termin.getName().split("\\(")[0];
            if (termin.getType() == KalenderController.getKalenderType(kalenderTermin.getName())
                    && terminname.equals(kalenderTerminName)) {
                oldTerminOfUser = termin;
                break;
            }
        }
        if (oldTerminOfUser == null) {
            return ResponseEntity.badRequest().body("Old Termin not found");
        }

        KalenderTermin newTerminForTauschPartner = new KalenderTermin(oldTerminOfUser.getStart(),
                oldTerminOfUser.getEnd(), oldTerminOfUser.getName(), "", "",
                KalenderController.getKalenderType(oldTerminOfUser.getName()));
        newTerminForTauschPartner = kalenderTerminRepository.save(newTerminForTauschPartner);

        // Entferne die alten Termine aus den Kalendern der Benutzer
        if (kalenderUser != null) {
            kalenderUser.getTermine().remove(oldTerminOfUser);
            kalenderUser.getTermine().remove(kalenderTermin);
            kalenderUser.getTermine().add(newTerminForUser);
            kalenderRepository.save(kalenderUser);
        }

        Kalender kalenderTauschPartner = kalenderRepository.findByUserId(tauschPartner.getId());
        if (kalenderTauschPartner != null) {
            kalenderTauschPartner.getTermine().remove(kalenderTermin);
            kalenderTauschPartner.getTermine().remove(oldTerminOfUser);
            kalenderTauschPartner.getTermine().add(newTerminForTauschPartner);
            KalenderTermin oldTerminOfTauschPartner = null;
            for (KalenderTermin termin : kalenderTauschPartner.getTermine()) {
                String terminname = termin.getName().split("\\(")[0];
                if (termin.getType() == KalenderController.getKalenderType(kalenderTermin.getName())
                        && terminname.equals(kalenderTerminName)) {
                    oldTerminOfTauschPartner = termin;
                    break;
                }
            }
            if (oldTerminOfTauschPartner == null) {
                return ResponseEntity.badRequest().body("Old Termin of TauschPartner not found");
            }
            kalenderTauschPartner.getTermine().remove(oldTerminOfTauschPartner);

            kalenderRepository.save(kalenderTauschPartner);
        }

        // Lösche die alten Termine und den TauschTermin
        kalenderTerminRepository.delete(oldTerminOfUser);
        kalenderTerminRepository.delete(kalenderTermin);
        tauschTerminRepository.delete(tauschTermin);
        // TODO Sende Mail an beide Benutzer

        String infosForFrontend = createConfirmationText(user, tauschPartner, newTerminForUser,
                newTerminForTauschPartner);
        String infosForTauschPartner = createConfirmationText(tauschPartner, user, newTerminForTauschPartner,
                newTerminForUser);
        MailUtils.sendMail(user.getHsMail(), "Informationen zum Tausch", infosForFrontend);

        MailUtils.sendMail(tauschPartner.getHsMail(), "Du hast ein Match!", infosForTauschPartner);

        return ResponseEntity.ok().body(infosForFrontend);
    }

    private String createConfirmationText(User user, User tauschPartner, KalenderTermin newTerminForUser,
            KalenderTermin newTerminForTauschPartner) {
        return "Dein Termin wurde erfolgreich getauscht!\n\n" +
                "Tauschpartner: " + extractName(tauschPartner.getHsMail()) + "\n\n"
                + "Der Termin " + newTerminForUser.getName() + " von "
                + convertKalenderTerminToString(newTerminForTauschPartner)
                + "\n\n"
                + "wurde nach " + convertKalenderTerminToString(newTerminForUser)
                + " verschoben.\n\n"
                + "Du kannst deinen Tauschpartner unter " +
                tauschPartner.getHsMail() + " erreichen.";
    }

    public String convertKalenderTerminToString(KalenderTermin termin) {

        Calendar c = Calendar.getInstance();
        c.setTime(termin.getStart());

        String[] days = new String[] { "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag" };
        String part1 = days[c.get(Calendar.DAY_OF_WEEK) - 2] + " von "
                + String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY),
                        c.get(Calendar.MINUTE));
        c.setTime(termin.getEnd());
        return part1 + " bis " + String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY),
                c.get(Calendar.MINUTE));

    }

    private String extractName(String adresse) {
        String namensTeil = adresse.split("@")[0];
        String[] name = namensTeil.split("\\.");
        String vorname = name[0].toUpperCase().charAt(0) + name[0].substring(1);
        String nachname = name[1].toUpperCase().charAt(0) + name[1].substring(1);

        return vorname + " " + nachname;
    }

    record UserKalenderTerminDTO(String title, String subtext, String color, String start, String end, int day) {
    };

    record Angebot(UserKalenderTerminDTO angebot, UserKalenderTerminDTO[] gesucht) {
    }

    @PostMapping("/createOffer")
    public ResponseEntity<String> createOffer(@RequestBody Angebot angebot) {
        User user = UserUtil.getUser();
        if (user == null) {
            return ResponseEntity.badRequest().body("User not logged in");
        }

        List<TauschTermin> termine = tauschTerminRepository.findTauschTerminByUserid(user.getId());

        for (TauschTermin termin : termine) {
            if (termin.angebot.getName().indexOf(angebot.angebot.title()) != -1) {

                tauschTerminRepository.delete(termin);
            }
        }

        KalenderTermin[] gesucht = new KalenderTermin[angebot.gesucht.length];
        for (int i = 0; i < angebot.gesucht.length; i++) {
            gesucht[i] = kalenderTerminRepository.save(convertKalenderTerminDTO(angebot.gesucht[i]));
        }
        KalenderTermin terminangebot = kalenderTerminRepository.save(convertKalenderTerminDTO(angebot.angebot));
        TauschTermin tauschTermin = new TauschTermin(user.getId(), terminangebot, gesucht);

        tauschTerminRepository.save(tauschTermin);
        return ResponseEntity.ok().build();
    }

    private static KalenderTermin convertKalenderTerminDTO(UserKalenderTerminDTO dto) {
        String start = dto.start();
        String end = dto.end();
        long startHour = Long.parseLong(start.split(":")[0]);
        long startMinute = Long.parseLong(start.split(":")[1]);
        long endHour = Long.parseLong(end.split(":")[0]);
        long endMinute = Long.parseLong(end.split(":")[1]);

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_WEEK, dto.day() + 2);

        calendar.set(Calendar.HOUR_OF_DAY, (int) startHour);
        calendar.set(Calendar.MINUTE, (int) startMinute);
        Date startDate = calendar.getTime();
        calendar.set(Calendar.DAY_OF_WEEK, dto.day() + 2);
        calendar.set(Calendar.HOUR_OF_DAY, (int) endHour);
        calendar.set(Calendar.MINUTE, (int) endMinute);
        Date endDate = calendar.getTime();

        return new KalenderTermin(startDate, endDate, dto.title(), "", "",
                KalenderController.getKalenderType(dto.title()));
    }

}
