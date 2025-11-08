package team.boerse.tauschboerse;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import team.boerse.tauschboerse.audit.AuditEventType;
import team.boerse.tauschboerse.audit.AuditService;
import team.boerse.tauschboerse.audit.SemesterUtil;
import team.boerse.tauschboerse.mail.MailUtils;
import team.boerse.tauschboerse.metrics.UserMetricsService;
import team.boerse.tauschboerse.settings.SystemMode;
import team.boerse.tauschboerse.settings.SystemSettings;
import team.boerse.tauschboerse.settings.SystemSettingsService;

@RestController
@RequiredArgsConstructor
public class TauschTerminController {

    private final KalenderRepository kalenderRepository;
    private final TauschTerminRepository tauschTerminRepository;
    private final KalenderTerminRepository kalenderTerminRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final UserMetricsService userMetricsService;
    private final SystemSettingsService systemSettingsService;
    private final team.boerse.tauschboerse.studiengang.NotSharedService notSharedService;
    private final Logger logger = LoggerFactory.getLogger(TauschTerminController.class);

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
        try {
            auditService.logEvent(user.getId(), AuditEventType.OFFER_DELETE,
                    "Removed all offers");
            userMetricsService.recordActivity(user.getId());
        } catch (Exception ex) {
            logger.warn("Failed to record audit/metrics for removeMyOffers", ex);
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

        // Prüfe System-Modus: Im POOLED_3CYCLE ist Direktannahme nicht erlaubt
        SystemSettings settings = systemSettingsService.getOrCreateSettings();
        if (settings.getMode() == SystemMode.POOLED_3CYCLE) {
            return ResponseEntity.status(409)
                    .body("Direktannahmen sind im Pooled-Modus deaktiviert. Ihre Anfrage wird in der nächsten Runde geprüft.");
        }

        KalenderTermin kalenderTermin = kalenderTerminRepository.findById(selectedTermin).orElse(null);
        if (kalenderTermin == null) {
            return ResponseEntity.badRequest().body("KalenderTermin not found");
        }

        TauschTermin tauschTermin = null;
        try {
            tauschTermin = tauschTerminRepository.findWithLockByAngebot(kalenderTermin);
        } catch (Exception lockEx) {
            logger.warn("Lock contention for offer {}: {}", selectedTermin, lockEx.getMessage());
            return ResponseEntity.status(409)
                    .body("Dieses Angebot wird gerade von jemand anderem bearbeitet. Bitte versuchen Sie es erneut.");
        }
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

        try {
            String base = kalenderTermin.getName() != null ? kalenderTermin.getName().split("\\(")[0].trim() : "";
            Long mySg = user.getStudiengang() != null ? user.getStudiengang().getId() : null;
            Long partnerSg = tauschPartner.getStudiengang() != null ? tauschPartner.getStudiengang().getId() : null;
            boolean nsMine = mySg != null && notSharedService.isNotShared(base, mySg);
            boolean nsPartner = partnerSg != null && notSharedService.isNotShared(base, partnerSg);
            if ((nsMine || nsPartner) && !java.util.Objects.equals(mySg, partnerSg)) {
                return ResponseEntity.status(409).body(
                        "Dieser Tausch ist nicht möglich, da ihr unterschiedliche Studiengänge habt.");
            }
        } catch (Exception ignore) {
        }

        TauschTermin sanity = tauschTerminRepository.findTauschTerminByAngebot(kalenderTermin);
        if (sanity == null || sanity.getId() != tauschTermin.getId()) {
            return ResponseEntity.status(409).body("Das Angebot ist nicht mehr verfügbar.");
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
        clearOverlappingTauschtermine(user, tauschPartner, newTerminForUser, newTerminForTauschPartner);

        String infosForFrontend = createConfirmationText(null, tauschPartner, newTerminForUser,
                newTerminForTauschPartner);
        String infosForTauschPartner = createConfirmationText(tauschPartner, user, newTerminForTauschPartner,
                newTerminForUser);
        MailUtils.sendMail(user.getHsMail(), user.getPrivateMail(), "Informationen zum Tausch", infosForFrontend);

        MailUtils.sendMail(tauschPartner.getHsMail(), tauschPartner.getPrivateMail(), "Erfolgreiche Terminvermittlung",
                infosForTauschPartner);

        try {
            auditService.logEvent(user.getId(), AuditEventType.MATCH_SUCCESS,
                    "Accepted offer with user " + tauschPartner.getId());
            userMetricsService.recordOfferAcceptance(user.getId());
            userMetricsService.recordSuccessfulMatch(user.getId());
            userMetricsService.recordActivity(user.getId());
            userMetricsService.getOrCreateMetrics(tauschPartner.getId(),
                    SemesterUtil.getSemesterForDate(new java.util.Date()));
            userMetricsService.recordSuccessfulMatch(tauschPartner.getId());
        } catch (Exception ex) {
            logger.warn("Failed to record audit/metrics for acceptOffer", ex);
        }

        return ResponseEntity.ok().body(infosForFrontend);
    }

    private String createConfirmationText(User user, User tauschPartner, KalenderTermin newTerminForUser,
            KalenderTermin newTerminForTauschPartner) {
        String[] floskeln = {
                "Wir beide würden uns sehr freuen, wenn der Tausch möglich wäre.",
                "Wir hoffen auf eine positive Rückmeldung.",
                "Vielen Dank für Ihre Unterstützung!",
        };
        String[] anrede = {
                "Sehr geehrte Damen und Herren,",
                "Hallo,",
                "Guten Tag,"
        };

        String[] titles = {
                "Gruppentausch " + newTerminForUser.getName().split(" ")[0],
                "Gruppenwechsel " + newTerminForUser.getName().split(" ")[0],
                "Gruppentausch Lehrveranstaltung " + newTerminForUser.getName().split(" ")[0],
        };

        String zufaelligerTitle = titles[(int) (Math.random() * titles.length)];
        String zufaelligeAnrede = anrede[(int) (Math.random() * anrede.length)];
        String zufaelligeFloskel = floskeln[(int) (Math.random() * floskeln.length)];

        String userName = user == null ? "" : extractName(user.getHsMail());
        String tauschPartnerName = extractName(tauschPartner.getHsMail());

        String body = "";
        if (user != null) {
            body = zufaelligeAnrede + "\n\n" +
                    tauschPartnerName + " und ich würden gerne die Gruppe tauschen, sofern dies möglich ist.\n\n"
                    +
                    tauschPartnerName + ":\n" + convertKalenderTerminToString(newTerminForUser)
                    + (" (" + getGroupName(newTerminForUser)) + ") => " +
                    convertKalenderTerminToString(newTerminForTauschPartner)
                    + (" (" + getGroupName(newTerminForTauschPartner)) + ")\n\n"
                    +
                    userName + ":\n" + convertKalenderTerminToString(newTerminForTauschPartner)
                    + (" (" + getGroupName(newTerminForTauschPartner)) + ") => " +
                    convertKalenderTerminToString(newTerminForUser)
                    + (" (" + getGroupName(newTerminForUser)) + ")\n\n" +
                    zufaelligeFloskel + "\n\n" +
                    "Mit freundlichen Grüßen\n" + userName;
        }

        String tauschPartnerEmail = tauschPartner.getHsMail();

        return "Die Tauschterminvermittlung war erfolgreich!\n\n" +
                "Tauschpartner/in: " + tauschPartnerName + "\n\n" +
                "Dein Termin " + newTerminForUser.getName() + " am " +
                convertKalenderTerminToString(newTerminForTauschPartner) + "\n" +
                "kann mit dem Termin am " + convertKalenderTerminToString(newTerminForUser) +
                " getauscht werden.\n\n" +
                "Kontaktiere deine/n Tauschpartner/in unter " + tauschPartnerEmail +
                " und sagt gemeinsam eurer Kursleitung Bescheid, dass ihr tauschen möchtet!\n\n"
                + (user != null
                        ? "Du kannst deinen Dozenten entweder selbst anschreiben oder unsere unverbindliche Vorlage verwenden:\n <a href=\""
                                + createMailtoLink(null, tauschPartnerEmail, zufaelligerTitle, body)
                                + "\">Vorlage verwenden</a>\n\n"

                        : "\n\n")
                + "Hat dir die Tauschbörse weitergeholfen? Dann empfiehl uns weiter und gib uns Feedback unter:\n"
                + (user != null
                        ? "<a href='https://tauschboerse.nkwebservices.de/#bewertungen'>https://tauschboerse.nkwebservices.de/#bewertungen</a>"
                        : "https://tauschboerse.nkwebservices.de/#bewertungen");
    }

    public String getGroupName(KalenderTermin termin) {
        String name = termin.getName();
        Pattern pattern = Pattern.compile("\\((\\w+)-([A-Z])\\)");
        Matcher matcher = pattern.matcher(name);
        if (matcher.find()) {
            return matcher.group(2);
        }
        return "";
    }

    public void clearOverlappingTauschtermine(User u, User tauschpartner, KalenderTermin termin,
            KalenderTermin terminTauschpartner) {
        List<TauschTermin> tauschTermine = tauschTerminRepository.findTauschTerminByUserid(u.getId());
        List<TauschTermin> tauschTermineTauschpartner = tauschTerminRepository
                .findTauschTerminByUserid(tauschpartner.getId());
        List<TauschTermin> tauschTermineToDelete = new ArrayList<>();

        for (TauschTermin tauschTermin : tauschTermine) {
            tauschTermin.getGesucht().removeIf(t -> compareKalenderTermin(t, termin) && termin.getId() != t.getId());
            if (tauschTermin.getGesucht().isEmpty()) {
                tauschTermineToDelete.add(tauschTermin);
            }
        }
        for (TauschTermin tauschTermin : tauschTermineTauschpartner) {
            tauschTermin.getGesucht().removeIf(
                    t -> compareKalenderTermin(t, terminTauschpartner) && terminTauschpartner.getId() != t.getId());
            if (tauschTermin.getGesucht().isEmpty()) {
                tauschTermineToDelete.add(tauschTermin);
            }
        }
        tauschTerminRepository.saveAll(tauschTermine);
        tauschTerminRepository.saveAll(tauschTermineTauschpartner);
        tauschTerminRepository.deleteAll(tauschTermineToDelete);
        if (!tauschTermineToDelete.isEmpty()) {
            logger.info("Deleted overlapping Tauschtermine");
        }

    }

    public boolean compareKalenderTermin(KalenderTermin termin1, KalenderTermin termin2) {
        Date termin1StartDate = removeSecondsAndMillis(termin1.getStart());
        Date termin2StartDate = removeSecondsAndMillis(termin2.getStart());
        Date termin1EndDate = removeSecondsAndMillis(termin1.getEnd());
        Date termin2EndDate = removeSecondsAndMillis(termin2.getEnd());

        return termin1StartDate.equals(termin2StartDate) && termin1EndDate.equals(termin2EndDate);
    }

    private Date removeSecondsAndMillis(Date date) {
        if (date == null) {
            return null;
        }
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
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

    public static String createMailtoLink(String to, String cc, String subject, String body) {
        List<String> ccList = new ArrayList<>();
        if (cc != null && !cc.isBlank())
            ccList.add(cc);
        return MailUtils.createMailtoLink(to, ccList, subject, body);
    }

    record UserKalenderTerminDTO(String title, String subtext, String color, String start, String end, int day) {
    }

    record Angebot(UserKalenderTerminDTO angebot, UserKalenderTerminDTO[] gesucht) {
    }

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
        if (count >= 5) {
            logger.info("User " + user.getHsMail() + " tried to create more than 5 offers");
            return ResponseEntity.status(403).body("You can only have 5 offers at the same time");
        }

        List<KalenderTermin> gesucht = new ArrayList<>();
        for (int i = 0; i < angebot.gesucht.length; i++) {
            gesucht.add(kalenderTerminRepository.saveAndFlush(convertKalenderTerminDTO(angebot.gesucht[i])));
        }

        KalenderTermin terminangebot = kalenderTerminRepository.save(convertKalenderTerminDTO(angebot.angebot));
        TauschTermin tauschTermin = new TauschTermin(user.getId(), terminangebot, gesucht);
        logger.info("User " + user.getHsMail() + " created an offer for " + terminangebot.getName());
        tauschTerminRepository.save(tauschTermin);
        try {
            auditService.logEvent(user.getId(), AuditEventType.OFFER_CREATE,
                    "Created offer(s): " + terminangebot.getName() + " with "
                            + gesucht.size() + " Offers");
            userMetricsService.getOrCreateMetrics(user.getId(),
                    SemesterUtil.getSemesterForDate(new java.util.Date()));
            userMetricsService.recordOfferCreation(user.getId(), angebot.gesucht.length);
            userMetricsService.recordActivity(user.getId());
        } catch (Exception ex) {
            logger.warn("Failed to record audit/metrics for createOffer", ex);
        }
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
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.set(Calendar.HOUR_OF_DAY, (int) startHour);
        calendar.set(Calendar.MINUTE, (int) startMinute);
        Date startDate = calendar.getTime();
        calendar.set(Calendar.DAY_OF_WEEK, dto.day() + 2);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.set(Calendar.HOUR_OF_DAY, (int) endHour);
        calendar.set(Calendar.MINUTE, (int) endMinute);
        Date endDate = calendar.getTime();

        return new KalenderTermin(startDate, endDate, dto.title(), "", "",
                KalenderController.getKalenderType(dto.title()));
    }

}
