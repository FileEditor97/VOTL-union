package union.utils.imagegen;

import union.utils.ColorUtil;

import java.awt.*;

public class BackgroundUserColors {

	private Color backgroundColor = ColorUtil.decode("#373746");
	private Color mainTextColor = ColorUtil.decode("#e2e2e2", .95f);
	private Color secondaryTextColor = ColorUtil.decode("#a6a6a6", .95f);
	private Color experienceTextColor = null;
	private Color experienceBackgroundColor = ColorUtil.decode("#26263b", .6f);
	private Color experienceForegroundColor = ColorUtil.decode("#686baa", .8f);
	private Color experienceSeparatorColor = ColorUtil.decode("#8c90e2", .8f);

	public Color getBackgroundColor() {
		return backgroundColor;
	}

	public void setBackgroundColor(Color color) {
		if (color != null) this.backgroundColor = color;
	}

	public Color getMainTextColor() {
		return mainTextColor;
	}

	public void setMainTextColor(Color color) {
		if (color != null) this.mainTextColor = color;
	}

	public Color getSecondaryTextColor() {
		return secondaryTextColor;
	}

	public void setSecondaryTextColor(Color color) {
		if (color != null) this.secondaryTextColor = color;
	}

	public Color getExperienceTextColor() {
		return experienceTextColor==null?mainTextColor:experienceTextColor;
	}

	public void setExperienceTextColor(Color color) {
		if (color != null) this.experienceTextColor = color;
	}

	public Color getExperienceBackgroundColor() {
		return experienceBackgroundColor;
	}

	public void setExperienceBackgroundColor(Color color) {
		if (color != null) this.experienceBackgroundColor = color;
	}

	public Color getExperienceForegroundColor() {
		return experienceForegroundColor;
	}

	public void setExperienceForegroundColor(Color color) {
		if (color != null) this.experienceForegroundColor = color;
	}

	public Color getExperienceSeparatorColor() {
		return experienceSeparatorColor;
	}

	public void setExperienceSeparatorColor(Color color) {
		if (color != null) this.experienceSeparatorColor = color;
	}

	public Color getCardColor() {
		return new Color(backgroundColor.getRed(), backgroundColor.getGreen(), backgroundColor.getBlue(), 140).darker();
	}

	public Color getShadowColor() {
		return new Color(backgroundColor.getRed(), backgroundColor.getGreen(), backgroundColor.getBlue(),200).darker();
	}

}
