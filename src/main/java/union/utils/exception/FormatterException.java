package union.utils.exception;

import javax.annotation.Nonnull;

public class FormatterException extends Exception {
	
	@Nonnull
	private String path;
	
	public FormatterException(@Nonnull String path) {
		super();
		this.path = path;
	}

	@Nonnull
	public String getPath() {
		return path;
	}
}
