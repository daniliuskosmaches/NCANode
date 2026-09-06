package kz.ncanode.dto.request;

import kz.ncanode.dto.tsp.TsaPolicy;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.util.List;

@Data
public class PdfSignRequest {

	@NotEmpty
	private String pdf;

	private List<PdfSigner> signers;

	private boolean withTsp = false;

	private TsaPolicy tsaPolicy;

	@Data
	public static class PdfSigner {
		private String reason;
		private String location;
		private String contactInfo;

		private SignerRequest signer;
	}
}
