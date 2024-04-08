package team.boerse.tauschboerse.mail;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

@Component
@EnableScheduling
public class MailUtils {

    record Mail(String to, String cc, String subject, String text) {
    }

    private static Logger logger = LoggerFactory.getLogger(MailUtils.class);

    public static void sendMail(String to, String cc, String subject, String text) {
        logger.info(String.format("Sending mail to %s with subject %s and text %s", to, subject, text));
        mails.add(new Mail(to, cc, subject, text));
    }

    private static ArrayList<Mail> mails = new ArrayList<>();
    private static boolean isSending = false;

    @Scheduled(fixedDelay = 1000)
    public static void sendMails() {
        if (mails.isEmpty() || isSending)
            return;
        Mail mail = mails.remove(0);
        isSending = true;
        try {
            sendEmail(mail.to(), mail.cc(), mail.subject(), mail.text());
        } catch (Exception e) {
            e.printStackTrace();
        }
        isSending = false;
    }

    private static void sendEmail(String recipient, String cc, String subject, String text) {

        for (int i = 0; i < 100; i++) {
            String hsMail = "maximilia" + i + ".musterata" + i + "@student.hs-rm.de";

            if (recipient.equalsIgnoreCase(hsMail)) {
                return;
            }
        }
        String smtpHost = "mail.nkwebservices.de";
        int smtpPort = 587;
        String smtpUsername = "tauschboerse@nkwebservices.de";
        String smtpPassword = "";
        try {
            smtpPassword = new String(Files.readAllBytes(new File("C:\\boerse\\tauschboerse\\mailpw.txt").toPath()))
                    .trim();
        } catch (Exception e) {
            smtpPassword = System.getenv("MAIL_PASSWORD");
            if (smtpPassword == null) {
                // Dev-Mode?
                return;
            }
        }

        Properties props = new Properties();
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", String.valueOf(smtpPort));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.ssl.trust", smtpHost);
        final String finalSmtpPassword = smtpPassword;
        Session session = Session.getInstance(props, new jakarta.mail.Authenticator() {
            @Override
            protected jakarta.mail.PasswordAuthentication getPasswordAuthentication() {
                return new jakarta.mail.PasswordAuthentication(smtpUsername, finalSmtpPassword);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(smtpUsername));

            if (cc != null && !cc.isEmpty()) {
                message.setRecipients(Message.RecipientType.CC, InternetAddress.parse(cc));
            }
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient));
            message.setHeader("Content-Type", "text/plain; charset=UTF-8");
            message.setSubject(subject);
            message.setText(text);
            Transport.send(message);
            logger.info(String.format("E-Mail an %s gesendet", recipient));
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }

}
