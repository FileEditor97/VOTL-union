package union.utils.imagegen;

import union.objects.constants.Constants;

import java.util.Objects;

public class UserBackground {

	private final int id;
	private final String name;
	private final String backgroundGraphicName;
	private final BackgroundUserColors userColors;

	UserBackground(int id, String name, String backgroundGraphicName, BackgroundUserColors userColor) {
		this.id = id;
		this.name = name;
		this.backgroundGraphicName = backgroundGraphicName;
		this.userColors = userColor;
	}

	public int getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getBackgroundFile() {
		return backgroundGraphicName;
	}

	public String getBackgroundPath() {
		return Constants.DATA_PATH+"backgrounds"+Constants.SEPAR+backgroundGraphicName;
	}

	public BackgroundUserColors getColors() {
		return userColors;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof UserBackground background)) {
			return false;
		}

		return getId() == background.getId()
			|| getName().equals(background.getName());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getId(), getName());
	}
}
