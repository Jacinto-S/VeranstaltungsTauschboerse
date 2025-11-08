package team.boerse.tauschboerse.webauthn;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.security.web.webauthn.api.Bytes;

@Converter(autoApply = true)
public class BytesAttributeConverter implements AttributeConverter<Bytes, String> {

    @Override
    public String convertToDatabaseColumn(Bytes attribute) {
        return attribute != null ? attribute.toBase64UrlString() : null;
    }

    @Override
    public Bytes convertToEntityAttribute(String dbData) {
        return dbData != null ? Bytes.fromBase64(dbData) : null;
    }
}
