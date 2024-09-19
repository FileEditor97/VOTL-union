package union.utils.imagegen.renders;

import net.dv8tion.jda.api.entities.User;
import union.objects.annotation.NotNull;
import union.objects.annotation.Nullable;
import union.utils.imagegen.Fonts;
import union.utils.imagegen.UserBackground;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public class UserBackgroundRender extends Renderer {

	private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final int startingX = 145;
	private final int startingY = 35;

	private final String globalName, userName, avatarUrl;
	private final OffsetDateTime timeCreated;

	private UserBackground background;

	public UserBackgroundRender(@NotNull User user) {
		this(user.getGlobalName(), user.getName(), user.getEffectiveAvatarUrl(), user.getTimeCreated());
	}

	public UserBackgroundRender(String globalName, String username, String avatarUrl,
								OffsetDateTime timeCreated) {
		this.globalName = globalName;
		this.userName = username;
		this.avatarUrl = avatarUrl;
		this.timeCreated = timeCreated;
		this.background = null;
	}

	public UserBackgroundRender setBackground(@Nullable UserBackground background) {
		this.background = background;
		return this;
	}

	@Override
	public boolean canRender() {
		return background != null;
	}

	@Override
	protected BufferedImage handleRender() throws IOException {
		URL url = new URL(avatarUrl);
		URLConnection connection = url.openConnection();
		connection.setRequestProperty("user-Agent", "VOTL-Discord-Bot");

		BufferedImage backgroundImage = loadAndBuildBackground();

		// Creates our graphics and prepares it for use.
		Graphics2D g = backgroundImage.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		if (background.getColors().getBackgroundCoverColor() != null) {
			g.setColor(background.getColors().getBackgroundCoverColor());
			g.fillRect(17, 8, 566, 184);
		}

		// Draws the avatar image on top of the background.
		g.drawImage(resize(ImageIO.read(connection.getInputStream()), 95, 95), 25, 15, null);

		createUserGraphics(g);

		return backgroundImage;
	}

	private BufferedImage loadAndBuildBackground() throws IOException {
		if (background.getBackgroundFile() != null) {
			return resize(
				ImageIO.read(new File(background.getBackgroundPath())),
				200, 600
			);
		}

		BufferedImage backgroundImage = new BufferedImage(600, 200, BufferedImage.TYPE_INT_ARGB);

		Graphics2D g = backgroundImage.createGraphics();
		g.setColor(background.getColors().getBackgroundColor());
		g.fillRect(0, 0, 600, 200);

		return backgroundImage;
	}

	private void createUserGraphics(Graphics2D g) {
		g.setFont(Fonts.bold.deriveFont(Font.PLAIN, 26F));
		g.setColor(background.getColors().getMainTextColor());

		String name = globalName==null?"USER":globalName;
		g.drawString(name, startingX + 5, startingY);

		FontMetrics fontMetrics = g.getFontMetrics();

		g.setFont(Fonts.medium.deriveFont(Font.PLAIN, 17));
		g.setColor(background.getColors().getSecondaryTextColor());

		g.drawString("@" + userName, startingX + 5, startingY + 26);

		String formattedTime = timeCreated.format(formatter);
		g.setColor(background.getColors().getCardColor());
		g.fillRoundRect(startingX + 5, startingY + 35,
			fontMetrics.stringWidth(formattedTime)-20, fontMetrics.getHeight(),
			30, 30);

		g.setColor(background.getColors().getMainTextColor());
		g.drawString(timeCreated.format(formatter), startingX + 20, startingY + 57);
	}
}
