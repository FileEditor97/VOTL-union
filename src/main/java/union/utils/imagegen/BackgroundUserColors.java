package union.utils.imagegen;

import union.utils.ColorUtil;

import java.awt.*;

public class BackgroundUserColors {

	private Color backgroundColor = ColorUtil.decode("#373746");
	private Color backgroundCoverColor = null;
	private Color mainTextColor = ColorUtil.decode("#e2e2e2", 0xd9);
	private Color secondaryTextColor = ColorUtil.decode("#a6a6a6", 0xd9);

	public Color getBackgroundColor() {
		return backgroundColor;
	}

	public void setBackgroundColor(Color color) {
		this.backgroundColor = color;
	}

	public Color getBackgroundCoverColor() {
		return backgroundCoverColor;
	}

	public void setBackgroundCoverColor(Color color) {
		this.backgroundCoverColor = color;
	}

	public Color getMainTextColor() {
		return mainTextColor;
	}

	public void setMainTextColor(Color color) {
		this.mainTextColor = color;
	}

	public Color getSecondaryTextColor() {
		return secondaryTextColor;
	}

	public void setSecondaryTextColor(Color color) {
		this.secondaryTextColor = color;
	}

	public Color getCardColor() {
		return new Color(backgroundColor.getRed(), backgroundColor.getGreen(), backgroundColor.getBlue(), 160).darker();
	}

}
