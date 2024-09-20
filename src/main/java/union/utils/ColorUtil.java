package union.utils;

import java.awt.*;

public class ColorUtil {

	/**
	 * @param hex - hex string
	 * @param alpha - value from 0(transparent) to 100(opaque)
	 * @return Color
	 */
	public static Color decode(String hex, int alpha) {
		int i = Integer.decode(hex);
		if (alpha > 255 || alpha < 0) alpha = 255;
		return new Color((i >> 16) & 0xFF, (i >> 8) & 0xFF, i & 0xFF, alpha);
	}

	public static Color decode(String hex) {
		int i = Integer.decode(hex);
		return new Color((i >> 16) & 0xFF, (i >> 8) & 0xFF, i & 0xFF);
	}


}
