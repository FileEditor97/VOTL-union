package union.utils.file.lang;

import java.util.List;

import union.objects.annotation.NotNull;
import union.objects.annotation.Nullable;
import union.utils.file.FileManager;

import net.dv8tion.jda.api.interactions.DiscordLocale;

public final class LangUtil {

	private final FileManager fileManager;
	
	public LangUtil(FileManager fileManager) {
		this.fileManager = fileManager;
	}
	
	@NotNull
	public String getString(DiscordLocale locale, String path) {
		return switch (locale) {
			case RUSSIAN -> fileManager.getString(locale.getLocale(), path);
			default -> fileManager.getString("en-GB", path);
        };
	}

	@Nullable
	public String getNullableString(DiscordLocale locale, String path) {
		return switch (locale) {
			case RUSSIAN -> fileManager.getNullableString(locale.getLocale(), path);
			default -> fileManager.getNullableString("en-GB", path);
        };
	}

	@NotNull
	public List<String> getStringList(DiscordLocale locale, String path) {
		return switch (locale) {
			case RUSSIAN -> fileManager.getStringList(locale.getLocale(), path);
			default -> fileManager.getStringList("en-GB", path);
        };
	}

}
