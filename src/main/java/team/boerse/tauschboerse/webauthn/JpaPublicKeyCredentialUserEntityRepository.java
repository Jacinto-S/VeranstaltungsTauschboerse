package team.boerse.tauschboerse.webauthn;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;

@Repository
@Transactional // Stellt sicher, dass alle Methoden transaktional ausgeführt werden
public class JpaPublicKeyCredentialUserEntityRepository implements PublicKeyCredentialUserEntityRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public PublicKeyCredentialUserEntity findById(Bytes id) {
        // Wir gehen davon aus, dass die ID in der Datenbank als Base64Url-kodierter
        // String vorliegt.

        PublicKeyCredentialUserEntity ent = entityManager.find(JpaPublicKeyCredentialUserEntity.class, id);
        return ent != null ? ent : null;
    }

    @Override
    public PublicKeyCredentialUserEntity findByUsername(String username) {
        TypedQuery<JpaPublicKeyCredentialUserEntity> query = entityManager.createQuery(
                "SELECT u FROM JpaPublicKeyCredentialUserEntity u WHERE u.name = :username",
                JpaPublicKeyCredentialUserEntity.class);
        query.setParameter("username", username);
        return query.getResultStream().findFirst().orElse(null);
    }

    @Override
    public void save(PublicKeyCredentialUserEntity userEntity) {
        System.out.println("Saving user entity: " + userEntity.getId().toBase64UrlString().length());

        JpaPublicKeyCredentialUserEntity entity = new JpaPublicKeyCredentialUserEntity(userEntity.getId(),
                userEntity.getName(), userEntity.getDisplayName());
        entityManager.merge(entity);
    }

    @Override
    public void delete(Bytes id) {
        PublicKeyCredentialUserEntity entity = findById(id);
        if (entity != null) {
            entityManager.remove(entity);
        }
    }
}
