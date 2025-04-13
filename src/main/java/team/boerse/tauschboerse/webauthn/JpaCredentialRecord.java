package team.boerse.tauschboerse.webauthn;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import team.boerse.tauschboerse.webauthn.JpaUserCredentialRepository.SerializableCredentialRecordImpl;
import jakarta.persistence.Converter;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

@Entity
@Table(name = "credential_records")
public class JpaCredentialRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    // Primärschlüssel: credentialId vom Typ Bytes, in der DB als
    // Base64Url-kodierter String mit Länge 3072
    @Id
    @Column(name = "credential_id", nullable = false, length = 3072)
    @Convert(converter = BytesAttributeConverter.class)
    private Bytes credentialId;

    @Column(name = "user_id", nullable = false, length = 3072)
    @Convert(converter = BytesAttributeConverter.class)
    private Bytes userId;

    // Hier wird der gesamte CredentialRecord als serialisierter Payload gespeichert
    @Column(name = "payload", nullable = false, columnDefinition = "BLOB")
    private byte[] payload;

    protected JpaCredentialRecord() {
    }

    public Bytes getCredentialId() {
        return credentialId;
    }

    public void setCredentialId(Bytes credentialId) {
        this.credentialId = credentialId;
    }

    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    /**
     * Erzeugt eine JpaCredentialRecord-Instanz aus einem CredentialRecord.
     * Hierbei wird das übergebene Objekt per Java Serialization in den Payload
     * konvertiert.
     */
    public static JpaCredentialRecord from(SerializableCredentialRecordImpl record) {
        JpaCredentialRecord entity = new JpaCredentialRecord();
        entity.setCredentialId(record.getCredentialId());
        entity.setUserId(record.getUserEntityUserId());
        entity.setPayload(serialize(record));
        return entity;
    }

    public void setUserId(Bytes userId) {
        this.userId = userId;
    }

    /**
     * Liefert das CredentialRecord, indem der Payload deserialisiert wird.
     */
    public CredentialRecord toCredentialRecord() {
        return deserialize(this.payload);
    }

    private static byte[] serialize(SerializableCredentialRecordImpl record) {

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(record);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Fehler bei der Serialisierung des CredentialRecord", e);
        }
    }

    private static CredentialRecord deserialize(byte[] data) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
                ObjectInputStream ois = new ObjectInputStream(bais)) {
            SerializableCredentialRecordImpl record = (SerializableCredentialRecordImpl) ois.readObject();
            return record.toCredentialRecord();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Fehler bei der Deserialisierung des CredentialRecord", e);
        }
    }
}
