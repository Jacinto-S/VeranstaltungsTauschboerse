package team.boerse.tauschboerse.mail;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
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

    public static String createMailtoLink(String to, java.util.List<String> cc, String subject, String body) {
        StringBuilder mailto = new StringBuilder("mailto:");
        try {
            if (to != null && !to.isBlank()) {
                mailto.append(URLEncoder.encode(to, StandardCharsets.UTF_8.name())
                        .replace("+", "%20"));
            }
            List<String> params = new java.util.ArrayList<>();
            if (subject != null && !subject.isBlank()) {
                params.add("subject="
                        + URLEncoder.encode(subject, StandardCharsets.UTF_8.name())
                                .replace("+", "%20"));
            }
            if (cc != null && !cc.isEmpty()) {
                String ccJoined = cc.stream()
                        .filter(s -> s != null && !s.isBlank())
                        .collect(java.util.stream.Collectors.joining(","));
                if (!ccJoined.isBlank()) {
                    params.add("cc="
                            + URLEncoder.encode(ccJoined, StandardCharsets.UTF_8.name())
                                    .replace("+", "%20"));
                }
            }
            if (body != null && !body.isBlank()) {
                String normalized = body.replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\r\n");
                params.add("body=" + URLEncoder
                        .encode(normalized, StandardCharsets.UTF_8.name()).replace("+", "%20"));
            }
            if (!params.isEmpty()) {
                mailto.append("?").append(String.join("&", params));
            }
        } catch (java.io.UnsupportedEncodingException e) {// Should never happen
        }
        return mailto.toString();
    }

    public static void sendMail(String to, String cc, String subject, String text) {
        logger.info(String.format("Sending mail to %s with subject %s and text %s", to, subject, text));
        mails.add(new Mail(to, cc, subject, text));

    }

    private static ArrayList<Mail> mails = new ArrayList<>();
    private static boolean isSending = false;
    private static long lastCheck = 0;

    @Scheduled(fixedDelay = 100)
    public static void sendMails() {
        if (mails.isEmpty() || isSending || (System.currentTimeMillis() - lastCheck < 100))
            return;
        Mail mail = mails.remove(0);
        isSending = true;
        try {
            sendEmail(mail.to(), mail.cc(), mail.subject(), mail.text());
        } catch (Exception e) {
            e.printStackTrace();
        }
        isSending = false;
        lastCheck = System.currentTimeMillis();
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
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(smtpUsername));

            if (cc != null && !cc.isEmpty()) {
                message.setRecipients(Message.RecipientType.CC, InternetAddress.parse(cc));
            }
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient));
            message.setHeader("Content-Type", "text/html; charset=UTF-8");
            message.setSubject(subject);
            text = text.replaceAll("\\n", "<br>");
            message.setText(text, "utf-8", "html");

            Transport.send(message);
            logger.info(String.format("E-Mail an %s gesendet", recipient));
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }

}
