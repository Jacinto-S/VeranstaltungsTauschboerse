package team.boerse.tauschboerse.webauthn;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.security.web.webauthn.api.CredentialRecord;

import team.boerse.tauschboerse.User;
import team.boerse.tauschboerse.UserUtil;
import team.boerse.tauschboerse.audit.AuditService;
import team.boerse.tauschboerse.audit.AuditEventType;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/webauthn/credentials")
@RequiredArgsConstructor
public class WebAuthnCredentialController {

    private static final int MAX_LABEL_LENGTH = 120;

    private final JpaUserCredentialRepository credentialRepository;
    private final JpaPublicKeyCredentialUserEntityRepository userEntityRepository;

    private final AuditService auditService;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<CredentialDto>> listCredentials() {
        User user = UserUtil.getUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        var userEntity = userEntityRepository.findByUsername(user.getHsMail());
        if (userEntity == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<CredentialRecord> credentials = credentialRepository.findByUserId(userEntity.getId());
        List<CredentialDto> body = credentials.stream()
                .map(this::toDto)
                .sorted(Comparator.comparing(CredentialDto::created, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        return ResponseEntity.ok(body);
    }

    @DeleteMapping("/{credentialId}")
    @Transactional
    public ResponseEntity<Void> deleteCredential(@PathVariable String credentialId) {
        User user = UserUtil.getUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        var userEntity = userEntityRepository.findByUsername(user.getHsMail());
        if (userEntity == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        List<CredentialRecord> credentials = credentialRepository.findByUserId(userEntity.getId());
        Optional<CredentialRecord> target = findCredential(credentialId, credentials);
        if (target.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        credentialRepository.delete(target.get().getCredentialId());

        try {
            if (auditService != null) {
                String credentialLabel = target.get().getLabel() != null ? target.get().getLabel() : "Unnamed";
                auditService.logEvent(user.getId(), AuditEventType.PASSKEY_REVOKED,
                        "Passkey deleted: " + credentialLabel);
            }
        } catch (Exception ex) {
        }

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{credentialId}")
    @Transactional
    public ResponseEntity<CredentialDto> updateCredentialLabel(@PathVariable String credentialId,
            @RequestBody(required = false) UpdateLabelRequest request) {
        User user = UserUtil.getUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        var userEntity = userEntityRepository.findByUsername(user.getHsMail());
        if (userEntity == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        List<CredentialRecord> credentials = credentialRepository.findByUserId(userEntity.getId());
        Optional<CredentialRecord> target = findCredential(credentialId, credentials);
        if (target.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        String normalizedLabel = normalizeLabel(request != null ? request.label() : null);
        CredentialRecord record = target.get();
        if (Objects.equals(record.getLabel(), normalizedLabel)) {
            return ResponseEntity.ok(toDto(record));
        }

        JpaUserCredentialRepository.SerializableCredentialRecordImpl serializableRecord;
        if (record instanceof JpaUserCredentialRepository.SerializableCredentialRecordImpl existing) {
            serializableRecord = existing;
        } else {
            serializableRecord = JpaUserCredentialRepository.SerializableCredentialRecordImpl.from(record);
        }

        serializableRecord.setLabel(normalizedLabel);
        credentialRepository.save(serializableRecord);

        return ResponseEntity.ok(toDto(serializableRecord));
    }

    private Optional<CredentialRecord> findCredential(String credentialId, List<CredentialRecord> credentials) {
        if (credentialId == null || credentials == null) {
            return Optional.empty();
        }
        return credentials.stream()
                .filter(record -> record.getCredentialId() != null
                        && credentialId.equals(record.getCredentialId().toBase64UrlString()))
                .findFirst();
    }

    private CredentialDto toDto(CredentialRecord record) {
        List<String> transports = record.getTransports() == null
                ? Collections.emptyList()
                : record.getTransports().stream()
                        .map(Object::toString)
                        .sorted()
                        .collect(Collectors.toList());

        return new CredentialDto(
                record.getCredentialId().toBase64UrlString(),
                record.getLabel(),
                record.getCreated(),
                record.getLastUsed(),
                record.getSignatureCount(),
                record.isBackupEligible(),
                record.isBackupState(),
                transports);
    }

    private String normalizeLabel(String label) {
        if (label == null) {
            return null;
        }
        String trimmed = label.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String sanitized = trimmed.replaceAll("\\p{Cntrl}", "");
        if (sanitized.isEmpty()) {
            return null;
        }
        sanitized = sanitized.replaceAll("\\s+", " ").trim();
        if (sanitized.isEmpty()) {
            return null;
        }
        if (sanitized.length() > MAX_LABEL_LENGTH) {
            sanitized = sanitized.substring(0, MAX_LABEL_LENGTH);
        }
        return sanitized;
    }

    public record UpdateLabelRequest(String label) {
    }

    public record CredentialDto(
            String credentialId,
            String label,
            Instant created,
            Instant lastUsed,
            long signatureCount,
            boolean backupEligible,
            boolean backupState,
            List<String> transports) {
    }
}
