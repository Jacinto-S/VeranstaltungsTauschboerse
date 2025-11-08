package team.boerse.tauschboerse.webauthn; // Paket anpassen

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.webauthn.api.AuthenticatorTransport;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCose;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import team.boerse.tauschboerse.User;
import team.boerse.tauschboerse.UserRepository;
import team.boerse.tauschboerse.audit.AuditEventType;
import team.boerse.tauschboerse.audit.AuditService;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@Repository
@Transactional
@RequiredArgsConstructor
public class JpaUserCredentialRepository implements UserCredentialRepository {

    private static final Logger logger = LoggerFactory.getLogger(JpaUserCredentialRepository.class);

    @PersistenceContext
    private EntityManager entityManager;

    private final AuditService auditService;

    private final UserRepository userRepository;

    private final JpaPublicKeyCredentialUserEntityRepository userEntityRepository;

    @Override
    public void save(CredentialRecord credentialRecord) {
        SerializableCredentialRecordImpl serializableRecord = SerializableCredentialRecordImpl.from(credentialRecord);
        JpaCredentialRecord entity = JpaCredentialRecord.from(serializableRecord);
        JpaCredentialRecord existing = entityManager.find(JpaCredentialRecord.class,
                credentialRecord.getCredentialId());
        boolean isNewCredential = existing == null;

        if (isNewCredential) {
            entityManager.persist(entity);
        } else {
            entityManager.merge(entity);
        }

        if (isNewCredential) {
            try {
                if (auditService != null && userRepository != null && userEntityRepository != null) {
                    Bytes userIdBytes = credentialRecord.getUserEntityUserId();
                    PublicKeyCredentialUserEntity userEntity = userEntityRepository.findById(userIdBytes);
                    if (userEntity != null) {
                        User user = userRepository.findByHsMail(userEntity.getName()).orElse(null);
                        if (user != null) {
                            String credentialLabel = credentialRecord.getLabel() != null ? credentialRecord.getLabel()
                                    : "Unnamed";
                            auditService.logEvent(user.getId(), AuditEventType.PASSKEY_CREATED,
                                    "Passkey created: " + credentialLabel);
                            user.setUsesPasskeys(true);
                            userRepository.save(user);
                        }
                    }
                }
            } catch (Exception ex) {
                logger.warn("Failed to write audit log for PASSKEY_CREATED", ex);
            }
        }
    }

    @Override
    public void delete(Bytes credentialId) {
        JpaCredentialRecord entity = entityManager.find(JpaCredentialRecord.class, credentialId);
        if (entity != null) {
            entityManager.remove(entity);
        }
    }

    @Override
    public CredentialRecord findByCredentialId(Bytes credentialId) {
        JpaCredentialRecord entity = entityManager.find(JpaCredentialRecord.class, credentialId);
        return entity != null ? entity.toCredentialRecord() : null;
    }

    @Override
    public List<CredentialRecord> findByUserId(Bytes userId) {
        TypedQuery<JpaCredentialRecord> query = entityManager.createQuery(
                "SELECT j FROM JpaCredentialRecord j WHERE j.userId = :userId", JpaCredentialRecord.class);
        query.setParameter("userId", userId);
        List<JpaCredentialRecord> results = query.getResultList();
        return results.stream()
                .map(JpaCredentialRecord::toCredentialRecord)
                .collect(Collectors.toList());
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class SerializableCredentialRecordImpl implements CredentialRecord, Serializable {

        private static final long serialVersionUID = 1L;

        private PublicKeyCredentialType credentialType;
        private Bytes credentialId;
        private SerializablePublicKeyCose publicKey;
        private long signatureCount;
        private boolean uvInitialized;
        private Set<AuthenticatorTransport> transports;
        private boolean backupEligible;
        private boolean backupState;
        private Bytes userEntityUserId;
        private Bytes attestationObject;
        private Bytes attestationClientDataJSON;
        private String label;
        private Instant lastUsed;
        private Instant created;

        public static SerializableCredentialRecordImpl from(CredentialRecord record) {
            return SerializableCredentialRecordImpl.builder()
                    .credentialType(record.getCredentialType())
                    .credentialId(record.getCredentialId())
                    .publicKey(new SerializablePublicKeyCose(record.getPublicKey().getBytes()))
                    .signatureCount(record.getSignatureCount())
                    .uvInitialized(record.isUvInitialized())
                    .transports(
                            record.getTransports() != null ? new HashSet<>(record.getTransports()) : new HashSet<>())
                    .backupEligible(record.isBackupEligible())
                    .backupState(record.isBackupState())
                    .userEntityUserId(record.getUserEntityUserId())
                    .attestationObject(record.getAttestationObject())
                    .attestationClientDataJSON(record.getAttestationClientDataJSON())
                    .label(record.getLabel())
                    .lastUsed(record.getLastUsed())
                    .created(record.getCreated())
                    .build();
        }

        public CredentialRecord toCredentialRecord() {
            return this;
        }

        @Override
        public Set<AuthenticatorTransport> getTransports() {
            return Collections.unmodifiableSet(this.transports);
        }
    }

    @Data
    @AllArgsConstructor
    public static class SerializablePublicKeyCose implements PublicKeyCose, Serializable {
        private static final long serialVersionUID = 1L;
        private byte[] bytes;

        @Override
        public byte[] getBytes() {
            return this.bytes;
        }
    }
}