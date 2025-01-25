package union.utils.imagegen.renders;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import union.App;
import union.objects.CmdAccessLevel;
import union.utils.database.managers.UnionPlayerManager.PlayerInfo;
import union.utils.file.lang.LocaleUtil;
import union.utils.imagegen.Fonts;
import union.utils.imagegen.PlayerRank;
import union.utils.imagegen.UserBackground;
import union.utils.imagegen.UserRankColor;
import union.utils.message.MessageUtil;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class UserProfileRender extends Renderer {
	private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final String globalName, userName, avatarUrl;
	private final OffsetDateTime timeCreated, timeJoined;
	private UserRankColor rankColor;

	private List<PlayerInfo> playerData;
	private final List<String> badges = new ArrayList<>();

	private DiscordLocale locale;
	private UserBackground background;

	public UserProfileRender(@NotNull Member member) {
		this.globalName = member.getEffectiveName();
		this.userName = member.getUser().getName();
		this.avatarUrl = member.getEffectiveAvatarUrl();
		this.timeCreated = member.getUser().getTimeCreated();
		this.timeJoined = member.getTimeJoined();

		this.background = null;
		this.rankColor = UserRankColor.gray;
	}

	public UserProfileRender setBackground(@Nullable UserBackground background) {
		this.background = background;
		return this;
	}

	public UserProfileRender setLocale(@NotNull DiscordLocale locale) {
		this.locale = locale;
		return this;
	}

	public UserProfileRender setPlayerData(@NotNull List<PlayerInfo> playerData) {
		this.playerData = playerData;
		return this;
	}

	public UserProfileRender setAccessLevel(@NotNull CmdAccessLevel accessLevel) {
		rankColor = switch (accessLevel) {
			case DEV -> UserRankColor.green;
			case OWNER, OPERATOR, ADMIN -> UserRankColor.red;
			case MOD, HELPER -> UserRankColor.blue;
			default -> UserRankColor.gray;
		};
		if (accessLevel == CmdAccessLevel.MOD)
			badges.add("\uD83D\uDEE1️");
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
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

		createAvatar(g, ImageIO.read(connection.getInputStream()));

		createUserInfo(g);

		createPlayerData(g);

		createBadges(g);

		createAdditional(g);

		return backgroundImage;
	}

	private BufferedImage loadAndBuildBackground() throws IOException {
		int WIDTH = 900;
		int HEIGHT = 360;
		BufferedImage backgroundImage = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);

		// Create graphics for background
		Graphics2D g = backgroundImage.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

		// Round
		RoundRectangle2D roundRectangle = new RoundRectangle2D.Float(0, 0, WIDTH, HEIGHT, 40, 40);
		g.setClip(roundRectangle);

		if (background.getBackgroundFile() != null) {
			Image scaledInstance = ImageIO.read(new File(background.getBackgroundPath()))
				.getScaledInstance(WIDTH, HEIGHT, Image.SCALE_SMOOTH);
			g.drawImage(scaledInstance, 0, 0, null);
		} else {
			g.setColor(background.getColors().getBackgroundColor());
			g.fillRect(0, 0, WIDTH, HEIGHT);
		}

		// Draw the border
		RoundRectangle2D borderRectangle = new RoundRectangle2D.Float(3, 3, WIDTH -7, HEIGHT -7, 36, 36);
		g.setColor(new Color(0, 0, 0, 128)); // Semi-transparent black
		g.setStroke(new BasicStroke(10));
		g.draw(borderRectangle);

		g.dispose();

		return backgroundImage;
	}

	private void createAvatar(Graphics2D g, BufferedImage image) {
		int x = 25;
		int y = 20;
		int size = 140;
		// Draw avatar shadow
		g.setColor(background.getColors().getCardColor());
		g.drawOval(x, y, size, size);
		// Draws the avatar image on top of the background.
		g.drawImage(resizedCircle(image, size-8), x+4, y+4, null);
		// Draw circle
		g.setColor(rankColor.getColor());
		g.setStroke(new BasicStroke(4));
		g.drawOval(x+3, y+3, size-6, size-6);
	}

	private void createUserInfo(Graphics2D g) {
		int x = 180;
		int y = 50;

		g.setFont(Fonts.Montserrat.bold.deriveFont(Font.PLAIN, 30F));
		String text = globalName==null ? "USER" : globalName;

		g.setColor(background.getColors().getShadowColor());
		g.drawString(text, x+2, y+2);
		g.setColor(background.getColors().getMainTextColor());
		g.drawString(text, x, y);

		y += 30;
		g.setFont(Fonts.Montserrat.medium.deriveFont(Font.PLAIN, 20F));
		text = "@"+MessageUtil.limitString(userName, 18);

		g.setColor(background.getColors().getShadowColor());
		g.drawString(text, x+2, y+2);
		g.setColor(background.getColors().getSecondaryTextColor());
		g.drawString(text, x, y);

		y += 15;
		String formattedTime = "     "+timeCreated.format(formatter);
		g.setColor(background.getColors().getCardColor());
		FontMetrics fontMetrics = g.getFontMetrics();
		g.fillRoundRect(x, y,
			fontMetrics.stringWidth(formattedTime)+22, fontMetrics.getHeight()+6,
			30, 30);

		g.setColor(background.getColors().getShadowColor());
		g.drawString(formattedTime, x+12, y+25);
		g.setColor(background.getColors().getMainTextColor());
		g.drawString(formattedTime, x+10, y+23);

		y += 35;

		formattedTime = "     "+timeJoined.format(formatter);
		g.setColor(background.getColors().getCardColor());
		fontMetrics = g.getFontMetrics();
		g.fillRoundRect(x, y,
			fontMetrics.stringWidth(formattedTime)+22, fontMetrics.getHeight()+6,
			30, 30);

		g.setColor(background.getColors().getShadowColor());
		g.drawString(formattedTime, x+12, y+25);
		g.setColor(background.getColors().getMainTextColor());
		g.drawString(formattedTime, x+10, y+23);

		// Draw emojis
		g.setFont(Fonts.NotoEmoji.monochrome.deriveFont(Font.PLAIN, 14F));
		g.setColor(background.getColors().getShadowColor());
		g.drawString("\uD83D\uDCC5", x+12, y-13);
		g.drawString("\uD83C\uDFE0", x+12, y+22);
		g.setColor(background.getColors().getMainTextColor());
		g.drawString("\uD83D\uDCC5", x+10, y-15);
		g.drawString("\uD83C\uDFE0", x+10, y+20);
	}

	private void createPlayerData(Graphics2D g) {
		int startX = 410;
		int startY = 60;
		Font plain = Fonts.Montserrat.medium.deriveFont(Font.PLAIN, 17F);
		Font bold = Fonts.Montserrat.bold.deriveFont(Font.PLAIN, 17F);
		g.setFont(plain);
		FontMetrics fontMetrics = g.getFontMetrics();
		LocaleUtil lu = App.getInstance().getLocaleUtil();

		List<GridData> slots = gridPattern(playerData.size());
		for (int i=0; i<slots.size(); i++) {
			GridData slot = slots.get(i);
			PlayerInfo info = playerData.get(i);
			int x = startX+(240*slot.getColumn());
			int y = startY+(100*slot.getRow());
			int width = slot.isFullRow() ? 470 : 230;
			int height = slot.isDoubleRow() ? 120 : 85;
			// Card
			g.setPaint(new GradientPaint(
				x-5, y+height+40, info.getServerInfo().getColor(140),
				x+height-20, y+30, background.getColors().getCardColor(),
				false
			));
			g.fillRoundRect(x+2, y+2, width-2, height-2, 10, 10);
			// Server title
			x += 10; y += 30;
			g.setFont(bold);
			g.setColor(background.getColors().getMainTextColor());
			g.drawString(info.getServerInfo().getTitle(), x, y);
			// Rank
			y += 40;
			g.setFont(plain);
			g.drawString(lu.getLocalized(locale, "imagegen.profile.rank").formatted(info.getRank()), x, y);
			// Playtime
			String text = (slot.isFullRow() ?
				lu.getLocalized(locale, "imagegen.profile.time_full") :
				lu.getLocalized(locale, "imagegen.profile.time_short")).formatted(info.getPlayTime());
			if (slot.isDoubleRow()) y += 30;
			else x += width-20-fontMetrics.stringWidth(text);
			g.drawString(text, x, y);
		}
	}

	private void createBadges(Graphics2D g) {
		List<String> rankBadges = PlayerRank.getEmojiFromPlayerData(playerData);
		if (rankBadges.isEmpty() && badges.isEmpty()) return;

		int x = 40;
		int y = 210;
		g.setFont(Fonts.NotoEmoji.monochrome.deriveFont(Font.PLAIN, 32F));
		// Card
		g.setColor(background.getColors().getCardColor());
		FontMetrics fontMetrics = g.getFontMetrics();
		g.fillRoundRect(
			x-10, y-36,
			(rankBadges.size()+badges.size())*50+10,
			fontMetrics.getHeight()+14,
			30, 30
		);

		g.setColor(background.getColors().getMainTextColor());

		// Ranks
		for (String emoji : rankBadges) {
			g.setColor(background.getColors().getShadowColor());
			g.drawString(emoji, x+2, y+2);
			g.setColor(background.getColors().getMainTextColor());
			g.drawString(emoji, x, y);
			x += 50;
		}
		// Discord badges
		for (String emoji : badges) {
			g.setColor(background.getColors().getShadowColor());
			g.drawString(emoji, x+2, y+2);
			g.setColor(background.getColors().getMainTextColor());
			g.drawString(emoji, x, y);
			//x += 50;
		}
	}

	private void createAdditional(Graphics2D g) {
		int x = 20;
		int y = 340;

		g.setFont(Fonts.Montserrat.medium.deriveFont(Font.PLAIN, 20F));
		String text = "BETA";

		g.setColor(background.getColors().getShadowColor());
		g.drawString(text, x+2, y+2);
		g.setColor(background.getColors().getSecondaryTextColor());
		g.drawString(text, x, y);
	}

	private List<GridData> gridPattern(int count) {
		return switch (count) {
			case 1 -> List.of(
				new GridData(0, 0, true, true)
			);
			case 2 -> List.of(
				new GridData(0, 0, true),
				new GridData(1, 0, true)
			);
			case 3 -> List.of(
				new GridData(0, 0, true),
				new GridData(1, 0, true),
				new GridData(2, 0, true)
			);
			case 4 -> List.of(
				new GridData(0, 0, false),
				new GridData(0, 1, false),
				new GridData(1, 0, true),
				new GridData(2, 0, true)
			);
			case 5 -> List.of(
				new GridData(0, 0, false),
				new GridData(0, 1, false),
				new GridData(1, 0, false),
				new GridData(1, 1, false),
				new GridData(2, 0, true)
			);
			case 6 -> List.of(
				new GridData(0, 0, false),
				new GridData(0, 1, false),
				new GridData(1, 0, false),
				new GridData(1, 1, false),
				new GridData(2, 0, false),
				new GridData(2, 1, false)
			);
			default -> List.of();
		};
	}

	private static class GridData {
		private final int row, column;
		private final boolean fullRow, doubleRow;

		public GridData(int row, int column, boolean fullRow) {
			this.row = row;
			this.column = column;
			this.fullRow = fullRow;
			this.doubleRow = false;
		}

		public GridData(int row, int column, boolean fullRow, boolean doubleRow) {
			this.row = row;
			this.column = column;
			this.fullRow = fullRow;
			this.doubleRow = doubleRow;
		}

		public int getRow() {
			return row;
		}

		public int getColumn() {
			return column;
		}

		public boolean isFullRow() {
			return fullRow;
		}

		public boolean isDoubleRow() {
			return doubleRow;
		}
	}
}
