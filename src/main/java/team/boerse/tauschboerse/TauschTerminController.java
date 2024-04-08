package team.boerse.tauschboerse;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.transaction.Transactional;
import team.boerse.tauschboerse.mail.MailUtils;

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

    Logger logger = LoggerFactory.getLogger(TauschTerminController.class);

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
        logger.info("User " + user.getHsMail() + " removed all offers");
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

        kalenderUser.getTermine().remove(oldTerminOfUser);
        kalenderUser.getTermine().remove(kalenderTermin);
        kalenderUser.getTermine().add(newTerminForUser);
        kalenderRepository.save(kalenderUser);

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

        String infosForFrontend = createConfirmationText(tauschPartner, newTerminForUser,
                newTerminForTauschPartner);
        String infosForTauschPartner = createConfirmationText(user, newTerminForTauschPartner,
                newTerminForUser);
        MailUtils.sendMail(user.getHsMail(), user.getPrivateMail(), "Informationen zum Tausch", infosForFrontend);

        MailUtils.sendMail(tauschPartner.getHsMail(), tauschPartner.getPrivateMail(), "Erfolgreiche Terminvermittlung",
                infosForTauschPartner);

        return ResponseEntity.ok().body(infosForFrontend);
    }

    private String createConfirmationText(User tauschPartner, KalenderTermin newTerminForUser,
            KalenderTermin newTerminForTauschPartner) {
        return "Die Tauschterminvermittlung war erfolgreich!\n\n" +
                "Tauschpartner/in: " + extractName(tauschPartner.getHsMail()) + "\n\n"
                + "Dein Termin " + newTerminForUser.getName() + " am "
                + convertKalenderTerminToString(newTerminForTauschPartner)
                + "\n"
                + "kann mit dem Termin am " + convertKalenderTerminToString(newTerminForUser)
                + " getauscht werden.\n\n"
                + "Kontaktiere deine/n Tauschpartner/in unter " +
                tauschPartner.getHsMail()
                + " und sagt gemeinsam eurer Kursleitung Bescheid, dass ihr tauschen möchtet!\n\n"
                + "Hat dir die Tauschbörse weitergeholfen? Dann empfiehl uns weiter und gib uns Feedback unter:\nhttps://tauschboerse.nkwebservices.de/#bewertungen";
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

    public static String extractName(String adresse) {
        String namensTeil = adresse.split("@")[0];
        String[] name = namensTeil.split("\\.");
        String vorname = name[0].toUpperCase().charAt(0) + name[0].substring(1);
        String nachname = name[1].toUpperCase().charAt(0) + name[1].substring(1);

        return vorname + " " + nachname;
    }

    record UserKalenderTerminDTO(String title, String subtext, String color, String start, String end, int day) {
    }

    record Angebot(UserKalenderTerminDTO angebot, UserKalenderTerminDTO[] gesucht) {
    }

    @SuppressWarnings("null")
    @Transactional
    @PostMapping("/createOffer")
    public ResponseEntity<String> createOffer(@RequestBody Angebot angebot) {
        User user = UserUtil.getUser();
        if (user == null) {
            return ResponseEntity.badRequest().body("User not logged in");
        }

        List<TauschTermin> termine = tauschTerminRepository.findTauschTerminByUserid(user.getId());
        int count = termine.size();

        for (TauschTermin termin : termine) {
            if (termin.angebot.getName().indexOf(angebot.angebot.title()) != -1) {

                tauschTerminRepository.delete(termin);
                count--;
            }
        }
        if (count >= 2) {
            return ResponseEntity.status(403).body("You can only have 2 offers at the same time");
        }

        List<KalenderTermin> gesucht = new ArrayList<>();
        for (int i = 0; i < angebot.gesucht.length; i++) {
            gesucht.add(kalenderTerminRepository.saveAndFlush(convertKalenderTerminDTO(angebot.gesucht[i])));
        }

        KalenderTermin terminangebot = kalenderTerminRepository.save(convertKalenderTerminDTO(angebot.angebot));
        TauschTermin tauschTermin = new TauschTermin(user.getId(), terminangebot, gesucht);
        logger.info("User " + user.getHsMail() + " created an offer for " + terminangebot.getName());
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
