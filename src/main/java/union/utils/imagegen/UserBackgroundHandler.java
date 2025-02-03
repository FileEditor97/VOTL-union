package union.utils.imagegen;

import ch.qos.logback.classic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;
import union.App;
import union.objects.constants.Constants;
import union.utils.file.ResourceLoaderUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class UserBackgroundHandler {

	private static UserBackgroundHandler instance;
	private final Logger log = (Logger) LoggerFactory.getLogger(UserBackgroundHandler.class);

	private final List<UserBackground> backgrounds = new ArrayList<>();
	private final List<Integer> usedIds = new ArrayList<>();
	private final List<String> usedNames = new ArrayList<>();
	private final File backgroundsDirectory;

	private UserBackgroundHandler() {
		backgroundsDirectory = new File(Constants.DATA_PATH+"backgrounds");

		if (!backgroundsDirectory.exists()) {
			boolean ignored = backgroundsDirectory.mkdirs();
		}

		copyBackgrounds();
	}

	public static UserBackgroundHandler getInstance() {
		if (instance == null) {
			instance = new UserBackgroundHandler();
		}
		return instance;
	}

	@Nullable
	public UserBackground fromName(@NotNull String name) {
		for (UserBackground background : backgrounds) {
			if (background.getName().equalsIgnoreCase(name)) {
				return background;
			}
		}
		return null;
	}

	@Nullable
	public UserBackground fromId(int backgroundId) {
		for (UserBackground background : backgrounds) {
			if (background.getId() == backgroundId) {
				return background;
			}
		}
		return null;
	}

	public void start() {
		try {
			List<UserBackground> userBackgrounds = getIndexFile();
			backgrounds.addAll(userBackgrounds);
		} catch (IOException e) {
			log.error("Failed to load backgrounds: {}", e.getMessage(), e);
			System.exit(101);
		}
	}

	private void copyBackgrounds() {
		try {
			List<String> resourceFiles = ResourceLoaderUtil.getFiles(UserBackgroundHandler.class, "backgrounds");
			for (String fileName : resourceFiles) {
				if (!isValidBackgroundImage(fileName)) continue;
				log.debug("Copying background image file: backgrounds/{}", fileName);
				InputStream inputStream = App.class.getClassLoader().getResourceAsStream("backgrounds/"+fileName);
				Files.copy(inputStream, Paths.get(backgroundsDirectory+Constants.SEPAR+fileName), StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			log.error("Failed to copy over user backgrounds files: {}", e.getMessage(), e);
		}
	}

	private boolean isValidBackgroundImage(String fileName) {
		return fileName.endsWith(".png") || fileName.endsWith(".jpg");
	}

	private List<UserBackground> getIndexFile() throws IOException {
		List<UserBackground> localBackgrounds = new ArrayList<>();

		JSONObject jsonData = App.getInstance().getFileManager().getJsonObject("backgrounds");
		JSONArray jsonArray = jsonData.getJSONArray("themes");

		for (Object o : jsonArray) {
			JSONObject jsonObject = (JSONObject) o;

			UserBackgroundLoader userBackgroundLoader = new UserBackgroundLoader(jsonObject);
			UserBackground background = userBackgroundLoader.getUserBackground();

			BackgroundValidation validation = validateUserBackground(background);
			if (!validation.passed) {
				log.warn("The user background failed the {} validation check, refusing to load", validation.name());

				continue;
			}

			usedIds.add(background.getId());
			usedNames.add(background.getName());
			localBackgrounds.add(background);
			log.debug("Loaded background: {}", background.getName());
		}

		return localBackgrounds;
	}

	private BackgroundValidation validateUserBackground(UserBackground background) {
		if (background.getName() == null || background.getName().isEmpty()) {
			return BackgroundValidation.INVALID_NAME;
		}

		if (usedNames.contains(background.getName())) {
			return BackgroundValidation.NAME_ALREADY_USED;
		}

		if (usedIds.contains(background.getId())) {
			return BackgroundValidation.ID_ALREADY_USED;
		}

		return BackgroundValidation.PASSED;
	}

	enum BackgroundValidation {
		INVALID_NAME,
		NAME_ALREADY_USED,
		ID_ALREADY_USED,
		PASSED(true);

		private final boolean passed;

		BackgroundValidation() {
			this(false);
		}

		BackgroundValidation(boolean passed) {
			this.passed = passed;
		}
	}

}
