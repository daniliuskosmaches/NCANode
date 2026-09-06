package kz.ncanode.dto.request;

import kz.ncanode.dto.tsp.TsaPolicy;
import lombok.Data;

import java.util.List;

@Data
public class CmsCreateRequest {
    private String cms;
    private String data;

    private List<SignerRequest> signers;

    private boolean withTsp = false;

    private TsaPolicy tsaPolicy;

    private boolean detached = false;
}
