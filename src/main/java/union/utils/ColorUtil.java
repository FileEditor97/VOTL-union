package union.utils;

import java.awt.*;

@SuppressWarnings("JavaExistingMethodCanBeUsed")
public class ColorUtil {

	/**
	 * @param hex - hex string
	 * @param alpha - value from 0(transparent) to 255(opaque)
	 * @return Color
	 */
	public static Color decodeHex(String hex, int alpha) {
		int i = Integer.decode(hex);
		if (alpha > 255 || alpha < 0) alpha = 255;
		return new Color((i >> 16) & 0xFF, (i >> 8) & 0xFF, i & 0xFF, alpha);
	}

	/**
	 * @param i - integer color value
	 * @param alpha - value from 0(transparent) to 1(opaque)
	 * @return Color
	 */
	public static Color decode(int i, float alpha) {
		int alphaInt = (alpha > 1 || alpha < 0) ? 255 : Math.round(alpha * 255f);
		return new Color((i >> 16) & 0xFF, (i >> 8) & 0xFF, i & 0xFF, alphaInt);
	}

	/**
	 * @param hex - hex string
	 * @param alpha - value from 0(transparent) to 1(opaque)
	 * @return Color
	 */
	public static Color decode(String hex, float alpha) {
		int i = Integer.decode(hex);
		int alphaInt = (alpha > 1 || alpha < 0) ? 255 : Math.round(alpha * 255f);
		return new Color((i >> 16) & 0xFF, (i >> 8) & 0xFF, i & 0xFF, alphaInt);
	}

	public static Color decode(String hex) {
		return Color.decode(hex);
	}


}
