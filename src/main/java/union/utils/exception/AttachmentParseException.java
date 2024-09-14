package union.utils.exception;

import union.objects.annotation.NotNull;
import union.objects.annotation.Nullable;

public class AttachmentParseException extends Exception {
	@NotNull
	private final String path;

	public AttachmentParseException(@NotNull String path, @Nullable String message) {
		super(message);
		this.path = path;
	}

	@NotNull
	public String getPath() {
		return path;
	}
}
