package team.boerse.tauschboerse.webauthn;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;

@Entity
@Table(name = "public_key_credential_user_entity")
public class JpaPublicKeyCredentialUserEntity implements PublicKeyCredentialUserEntity {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id", nullable = false, length = 3072)
    @Convert(converter = BytesAttributeConverter.class)
    private Bytes id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    protected JpaPublicKeyCredentialUserEntity() {
    }

    public JpaPublicKeyCredentialUserEntity(Bytes id, String name, String displayName) {
        this.id = id;
        this.name = name;
        this.displayName = displayName;
    }

    @Override
    public Bytes getId() {
        return this.id;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getDisplayName() {
        return this.displayName;
    }

    // Setter können je nach Bedarf hinzugefügt werden

    public void setId(Bytes id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
