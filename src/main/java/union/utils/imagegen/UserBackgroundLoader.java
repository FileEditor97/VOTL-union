package union.utils.imagegen;

import org.json.JSONObject;
import union.utils.ColorUtil;

import java.awt.*;
import java.io.FileNotFoundException;

import static union.utils.CastUtil.requireNonNull;

public class UserBackgroundLoader {

	private final JSONObject colorData;
	private final UserBackground background;

	UserBackgroundLoader(JSONObject jsonObject) throws FileNotFoundException {
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

		if (colorData.has("backgroundCover")) {
			colors.setBackgroundCoverColor(loadColorFromString("backgroundCover"));
		}

		return colors;
	}

	// Formats
	//  #ffffff - without alpha
	//  #ffffff05 - with alpha 5%
	private Color loadColorFromString(String key) {
		if (!colorData.has(key))
			return null;
		String input = colorData.getString(key);
		if (input.startsWith("#") && input.length() > 7) {
			int alpha = Integer.parseInt(input.substring(7,9), 16);
			String v = input.substring(0,7);
			return ColorUtil.decode(input.substring(0,7), alpha);
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
