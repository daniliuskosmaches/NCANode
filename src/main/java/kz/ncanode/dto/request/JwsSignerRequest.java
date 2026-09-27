package kz.ncanode.dto.request;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import jakarta.validation.constraints.NotEmpty;

/**
 * Один подписант для JWS.
 */
@Jacksonized
@Data
@Builder
public class JwsSignerRequest {

    /** Алгоритм подписи (GG2015, GG2004, ES256/384/512, RS256/384/512). */
    @NotEmpty
    private String alg;

    private String key;

    private String password;

    private String keyAlias;
}
