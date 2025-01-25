package union.utils.exception;

import org.jetbrains.annotations.NotNull;

public class FormatterException extends Exception {
	@NotNull
	private final String path;
	
	public FormatterException(@NotNull String path) {
		super();
		this.path = path;
	}

	@NotNull
	public String getPath() {
		return path;
	}
}
