package union.utils.imagegen;

import org.json.JSONObject;
import union.utils.ColorUtil;

import java.awt.*;

import static union.utils.CastUtil.requireNonNull;

public class UserBackgroundLoader {

	private final JSONObject colorData;
	private final UserBackground background;

	UserBackgroundLoader(JSONObject jsonObject) {
		int id = requireNonNull(jsonObject.getInt("id"));

		String name = requireNonNull(jsonObject.getString("name"));
		String backgroundImage = jsonObject.isNull("image") ? null : jsonObject.getString("image");

		this.colorData = jsonObject.getJSONObject("colors");

		background = new UserBackground(id, name, backgroundImage, getColors());
	}

	private BackgroundUserColors getColors() {
		BackgroundUserColors colors = new BackgroundUserColors();

		colors.setBackgroundColor(loadColorFromString("background"));
		colors.setMainTextColor(loadColorFromString("mainText"));
		colors.setSecondaryTextColor(loadColorFromString("secondaryText"));

		colors.setExperienceTextColor(loadColorFromString("experienceText"));
		colors.setExperienceBackgroundColor(loadColorFromString("experienceBackground"));
		colors.setExperienceForegroundColor(loadColorFromString("experienceForeground"));
		colors.setExperienceSeparatorColor(loadColorFromString("experienceSeparator"));

		return colors;
	}

	// Formats
	//  #ffffff - without alpha
	//  #ffffffa5 - with alpha a5
	private Color loadColorFromString(String key) {
		if (!colorData.has(key))
			return null;
		String input = colorData.getString(key);
		if (input.startsWith("#") && input.length() > 7) {
			int alpha = Integer.parseInt(input.substring(7,9), 16);
			return ColorUtil.decodeHex(input.substring(0,7), alpha);
		}
		return ColorUtil.decode(input);
	}

	public UserBackground getUserBackground() {
		return background;
	}

	@Override
	public String toString() {
		return background.getName();
	}
}
