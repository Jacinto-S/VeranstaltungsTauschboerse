package team.boerse.tauschboerse.captcha;

import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import team.boerse.tauschboerse.captcha.Altcha.ChallengeOptions;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;

@RestController
public class CaptchaController {

    private String hmacKey;
    private final SecureRandom secureRandom = new SecureRandom();
    private ArrayList<String> forbiddenPayloads = new ArrayList<>();

    public CaptchaController() {
        generateNewHmacKey();
    }

    @Scheduled(fixedRate = 36000000) // 10 Stunden
    public void generateNewHmacKey() {
        byte[] key = new byte[32]; // 256 bits
        secureRandom.nextBytes(key);
        this.hmacKey = Base64.getEncoder().encodeToString(key);
        forbiddenPayloads.clear();
    }

    @GetMapping("/challenge")
    public ResponseEntity<Altcha.Challenge> createChallenge() {
        try {
            ChallengeOptions options = new ChallengeOptions()
                    .setMaxNumber(150_000L) // 0.1 Million => Standard Challenge
                    .setHmacKey(hmacKey)
                    .setExpiresInSeconds(1800);

            Altcha.Challenge challenge = Altcha.createChallenge(options);
            return ResponseEntity.ok(challenge);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<Boolean> verifySolution(@RequestBody String payload) {

        try {
            if (payload == null) {
                return ResponseEntity.badRequest().build();
            }
            if (forbiddenPayloads.contains(payload)) {
                return ResponseEntity.badRequest().build();
            }
            if (payload.length() > 500) {
                return ResponseEntity.badRequest().build();
            }
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(payload);
            String base64Payload = jsonNode.get("payload").asText();
            base64Payload = base64Payload.trim();

            if (forbiddenPayloads.contains(base64Payload)) {
                return ResponseEntity.badRequest().build();
            }
            forbiddenPayloads.add(base64Payload);

            boolean isValid = Altcha.verifySolution(base64Payload, hmacKey, false);

            if (isValid == false) {
                return ResponseEntity.badRequest().build();
            }
            return ResponseEntity.ok(isValid);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    public boolean checkSolution(String payload) {
        return verifySolution(payload).getStatusCode().is2xxSuccessful();
    }

}