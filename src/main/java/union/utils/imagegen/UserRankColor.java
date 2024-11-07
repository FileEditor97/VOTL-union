package union.utils.imagegen;

import java.awt.*;

public enum UserRankColor {
	red(new Color(195, 44, 44)),
	green(new Color(38, 189, 38)),
	blue(new Color(24, 63, 177)),
	gray(new Color(50, 50, 50));

	private final Color color;

	UserRankColor(Color color) {
		this.color = color;
	}

	public Color getColor() {
		return color;
	}
}
