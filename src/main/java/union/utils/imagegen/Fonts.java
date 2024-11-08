package union.utils.imagegen;

import union.utils.exception.FailedToLoadResourceException;

import java.awt.*;
import java.io.IOException;

public class Fonts {

	public static class Montserrat {
		public static final Font light, regular, medium, bold, extraBold;

		static {
			light = loadFont("Montserrat-Light.ttf");
			regular = loadFont("Montserrat-Regular.ttf");
			medium = loadFont("Montserrat-Medium.ttf");
			bold = loadFont("Montserrat-Bold.ttf");
			extraBold = loadFont("Montserrat-ExtraBold.ttf");
		}
	}

	public static class Roboto {
		public static final Font light, regular, medium;

		static {
			light = loadFont("RobotoMono-Light.ttf");
			regular = loadFont("RobotoMono-Regular.ttf");
			medium = loadFont("RobotoMono-Medium.ttf");
		}
	}

	public static class NotoEmoji {
		public static final Font monochrome;

		static {
			monochrome = loadFont("NotoEmoji.ttf");
		}
	}

	private static Font loadFont(String resourceName) {
		try {
			return Font.createFont(
				Font.TRUETYPE_FONT,
				Fonts.class.getClassLoader()
					.getResourceAsStream("fonts/" + resourceName)
			);
		} catch (FontFormatException | IOException e) {
			throw new FailedToLoadResourceException(String.format("Failed to load the font resource %s",
				resourceName
			), e);
		}
	}
}
