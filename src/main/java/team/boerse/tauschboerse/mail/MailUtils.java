package team.boerse.tauschboerse.mail;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
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

    record Mail(String to, String subject, String text) {
    }

    public static void sendMail(String to, String subject, String text) {
        System.out.println("Sending mail to " + to + " with subject " + subject + " and text " + text);
        mails.add(new Mail(to, subject, text));
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
            sendEmail(mail.to(), mail.subject(), mail.text());
        } catch (Exception e) {
            e.printStackTrace();
        }
        isSending = false;
    }

    private static void sendEmail(String recipient, String subject, String text) {
        // SMTP-Server-Einstellungen
        String smtpHost = "mail.nkwebservices.de"; // Setze hier den Hostnamen deines SMTP-Servers ein
        int smtpPort = 587; // Port für TLS/STARTTLS
        String smtpUsername = "tauschboerse@nkwebservices.de"; // Dein SMTP-Benutzername
        String smtpPassword = "";
        try {
            smtpPassword = new String(Files.readAllBytes(new File("C:\\boerse\\tauschboerse\\mailpw.txt").toPath()))
                    .trim();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // E-Mail-Eigenschaften und -Session
        Properties props = new Properties();
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", String.valueOf(smtpPort));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.ssl.trust", smtpHost);
        final String finalSmtpPassword = smtpPassword;
        Session session = Session.getInstance(props, new jakarta.mail.Authenticator() {
            protected jakarta.mail.PasswordAuthentication getPasswordAuthentication() {
                return new jakarta.mail.PasswordAuthentication(smtpUsername, finalSmtpPassword);
            }
        });

        try {
            // Erstelle eine E-Mail-Nachricht
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(smtpUsername));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient));
            message.setSubject(subject);
            message.setText(text);

            // Sende die E-Mail
            Transport.send(message);

            System.out.println("E-Mail erfolgreich gesendet!");
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }

}
