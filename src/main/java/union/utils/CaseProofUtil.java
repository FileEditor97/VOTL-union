package union.utils;

import net.dv8tion.jda.api.entities.Message;
import union.base.command.SlashCommandEvent;
import union.utils.exception.AttachmentParseException;

public class CaseProofUtil {
	private static final int MAX_FILE_SIZE = 4; // MiB
	private static final double BYTES_TO_MIB = 1_048_576; // Bytes = 1 MiB

	public static ProofData getData(SlashCommandEvent event) throws AttachmentParseException {
		// Get image
		Message.Attachment proof = event.optAttachment("proof");
		if (proof != null) {
			// Check if is png or jpeg
			String contentType = proof.getContentType();
			if (contentType == null || (!contentType.equals("image/jpeg") && !contentType.equals("image/png"))) {
				throw new AttachmentParseException("errors.proof_type", "Received: "+contentType);
			}
			// Check if size is allowed
			if (proof.getSize() > MAX_FILE_SIZE*BYTES_TO_MIB) {
				throw new AttachmentParseException("errors.proof_size", "Received: %.2fMiB / %dMiB".formatted(proof.getSize()/BYTES_TO_MIB, MAX_FILE_SIZE));
			}
			return new ProofData(proof);
		} else {
			return null;
		}
	}

	public static class ProofData {
		public final String proxyUrl, extension;
		public String fileName;

		public ProofData(Message.Attachment proof) {
			this.proxyUrl = proof.getProxyUrl();
			this.extension = proof.getFileExtension();
		}

		/**
		 * @param caseRowId Case row ID
		 * @return fileName like proof_CASEID.png
		 */
		public String setFileName(int caseRowId) {
			this.fileName = "proof_%s.%s".formatted(caseRowId, extension);
			return fileName;
		}
	}
}
