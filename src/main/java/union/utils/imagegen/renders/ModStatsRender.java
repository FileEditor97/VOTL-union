package union.utils.imagegen.renders;

import net.dv8tion.jda.api.interactions.DiscordLocale;
import union.App;
import union.objects.CaseType;
import union.utils.ColorUtil;
import union.utils.file.lang.LocaleUtil;
import union.utils.imagegen.Fonts;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class ModStatsRender extends Renderer {

	private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	private final DiscordLocale locale;
	private final LocaleUtil lu;
	private final Map<Integer, Integer> countTotal, count30, count7;
	private final int roleTotal, role30, role7;
	private final String username;
	private final OffsetDateTime timeCreated;

	public ModStatsRender(
		DiscordLocale locale,
		String username,
		Map<Integer, Integer> countTotal,
		Map<Integer, Integer> count30,
		Map<Integer, Integer> count7,
		int roleTotal,
		int role30,
		int role7
	) {
		this.locale = locale;
		this.lu = App.getInstance().getLocaleUtil();
		this.username = username;
		this.countTotal = countTotal;
		this.count30 = count30;
		this.count7 = count7;
		this.roleTotal = roleTotal;
		this.role30 = role30;
		this.role7 = role7;
		this.timeCreated = OffsetDateTime.now();
	}

	@Override
	public boolean canRender() {
		return true;
	}

	@Override
	protected BufferedImage handleRender() {
		final int WIDTH = 600;
		final int HEIGHT = 400;
		// Create image
		final int startingX = 20;
		final int startingY = 40;
		BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);

		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		// Fill background
		final Color backgroundColor = ColorUtil.decode("#2f3136");
		g.setColor(backgroundColor);
		g.fillRect(0, 0, WIDTH, HEIGHT);

		// Add title
		final Color mainTextColor = ColorUtil.decode("#e5e5e9");
		g.setFont(Fonts.Roboto.medium.deriveFont(Font.BOLD, 26F));
		g.setColor(mainTextColor);

		String title = getText("title");
		g.drawString(title, startingX, startingY);

		// Add user
		g.setFont(Fonts.Roboto.medium.deriveFont(Font.PLAIN, 22F));
		g.drawString("> @%s - %s".formatted(username, timeCreated.format(formatter)), startingX, startingY+36);

		// Add text
		g.setFont(Fonts.Roboto.regular.deriveFont(Font.PLAIN, 20F));
		g.setColor(ColorUtil.decode("#c6c6c6"));

		String[][] data = generateTableText();

		FontMetrics fontMetrics = g.getFontMetrics();
		final int maxLabelX = fontMetrics.stringWidth(data[2][0])+10;
		final int maxHeaderX = fontMetrics.stringWidth(data[0][2])+16;
		final int nextRowStep = fontMetrics.getHeight()+6;

		int y = startingY+80;
		for (int row=0;row<9;row++) {
			int x = startingX;
			g.drawString(data[row][0], x, y);
			x += maxLabelX;
			for (int col=1;col<4;col++) {
				g.drawString(data[row][col], x, y);
				x += maxHeaderX;
			}
			y += nextRowStep;
		}

		return image;
	}

	private String getText(String path) {
		return lu.getLocalized(locale, "bot.moderation.modstats."+path);
	}

	private String[][] generateTableText() {
		String[][] data = new String[9][4];

		int total7 = 0;
		int total30 = 0;
		int totalAll = 0;
		// Set top headers
		data[0][0] = "#";
		data[0][1] = getText("seven");
		data[0][2] = getText("thirty");
		data[0][3] = getText("all");
		// Strikes row
		data[1][0] = getText("strikes");
		int v = countStrikes(count7); total7+=v;
		data[1][1] = String.valueOf(v);
		v = countStrikes(count30); total30+=v;
		data[1][2] = String.valueOf(v);
		v = countStrikes(countTotal); totalAll+=v;
		data[1][3] = String.valueOf(v);
		// Fill rows
		java.util.List<CaseType> types = java.util.List.of(CaseType.GAME_STRIKE, CaseType.MUTE, CaseType.KICK, CaseType.BAN);
		for (int i=0;i<types.size();i++) {
			CaseType type = types.get(i);
			int row = i+2;
			data[row][0] = lu.getLocalized(locale, type.getPath());
			v = getCount(count7, type); total7+=v;
			data[row][1] = String.valueOf(v);
			v = getCount(count30, type); total30+=v;
			data[row][2] = String.valueOf(v);
			v = getCount(countTotal, type); totalAll+=v;
			data[row][3] = String.valueOf(v);
		}
		// Total row
		data[6][0] = "-"+getText("total")+"-";
		data[6][1] = String.valueOf(total7);
		data[6][2] = String.valueOf(total30);
		data[6][3] = String.valueOf(totalAll);
		// Empty row
		for (int n=0;n<4;n++) {
			data[7][n] = "";
		}
		// Roles row
		data[8][0] = getText("roles");
		data[8][1] = String.valueOf(role7);
		data[8][2] = String.valueOf(role30);
		data[8][3] = String.valueOf(roleTotal);

		return data;
	}

	private int countStrikes(Map<Integer, Integer> data) {
		return getCountInt(data, CaseType.STRIKE_1)+getCountInt(data, CaseType.STRIKE_2)+getCountInt(data, CaseType.STRIKE_3);
	}

	private int getCount(Map<Integer, Integer> data, CaseType type) {
		return data.getOrDefault(type.getType(), 0);
	}

	private int getCountInt(Map<Integer, Integer> data, CaseType type) {
		return data.getOrDefault(type.getType(), 0);
	}

}
