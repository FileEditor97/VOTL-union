package union.utils.exception;

import union.objects.annotation.NotNull;

public class FormatterException extends Exception {
	
	@NotNull
	private String path;
	
	public FormatterException(@NotNull String path) {
		super();
		this.path = path;
	}

	@NotNull
	public String getPath() {
		return path;
	}
}
