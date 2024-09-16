package union.utils.imagegen;

import union.utils.exception.FailedToLoadResourceException;

import java.awt.*;
import java.io.IOException;

public class Fonts {

	public static final Font regular, light, medium, bold, extraBold;


	static {
		regular = loadFont("Montserrat-Regular.ttf");
		light = loadFont("Montserrat-Light.ttf");
		medium = loadFont("Montserrat-Medium.ttf");
		bold = loadFont("Montserrat-Bold.ttf");
		extraBold = loadFont("Montserrat-ExtraBold.ttf");
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
