package union.objects.constants;

import java.io.File;
import java.nio.file.Paths;

public final class Constants {
	private Constants() {
		throw new IllegalStateException("Utility class");
	}

	public static final String SEPAR = File.separator;

	public static final String DATA_PATH = Paths.get("." + SEPAR + "data") + SEPAR;

	public static final String SUCCESS = "\u2611\uFE0F";
	public static final String WARNING = "\u26A0\uFE0F";
	public static final String FAILURE = "\u274C";
	public static final String NONE    = "\u25AA\uFE0F";

	public static final Integer COLOR_DEFAULT = 0x112E51;
	public static final Integer COLOR_SUCCESS = 0x266E35;
	public static final Integer COLOR_FAILURE = 0xB31E22;
	public static final Integer COLOR_WARNING = 0xFDB81E;

	public static final String DEVELOPER_TAG = "@fileeditor";
	public static final String DEVELOPER_ID = "369062521719488524";

	public static final int DEFAULT_CACHE_SIZE = 100; 

	public static final String VERIFY_VIDEO_GUIDE = "https://youtu.be/QNB0kBaAhTY";
}
