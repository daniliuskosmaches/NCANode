package kz.ncanode.dto.request;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Jacksonized
@Data
@Builder
public class SignerRequest {
    private String key;

    private String password;

    private String keyAlias;

    private String referenceUri;
}
