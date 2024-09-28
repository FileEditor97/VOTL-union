package union.utils.imagegen.renders;

import net.dv8tion.jda.api.interactions.DiscordLocale;
import union.objects.ReportData;
import union.utils.ColorUtil;
import union.utils.file.lang.LocaleUtil;
import union.utils.imagegen.Fonts;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ModReportRender extends Renderer {

	DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

	private final DiscordLocale locale;
	private final LocaleUtil lu;
	private final Instant previous, now;
	private final List<ReportData> reportData;

	public ModReportRender(
		DiscordLocale locale,
		LocaleUtil lu,
		Instant previous,
		Instant now,
		List<ReportData> reportData
	) {
		this.locale = locale;
		this.lu = lu;
		this.previous = previous;
		this.now = now;
		this.reportData = reportData;
	}

	@Override
	public boolean canRender() {
		return true;
	}

	@Override
	protected BufferedImage handleRender() {
		final int WIDTH = 900;
		final int HEIGHT = 200+(reportData.size()*40); // TODO
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

		String title = getText("title_report");
		g.drawString(title, startingX, startingY);

		// Add date
		g.setFont(Fonts.Roboto.medium.deriveFont(Font.PLAIN, 22F));
		g.drawString("%s - %s".formatted(formatter.format(previous), formatter.format(now)), startingX, startingY+36);

		// Add labels
		AffineTransform affineTransform = new AffineTransform();
		affineTransform.rotate(Math.toRadians(-35), 0, 0);
		Font labelFont = Fonts.Roboto.regular
			.deriveFont(Font.BOLD, 20F)
			.deriveFont(affineTransform);
		g.setFont(labelFont);
		g.setColor(ColorUtil.decode("#c6c6c6"));

		final int labelStepX = 60;
		int x = startingX+400;
		int y = startingY+140;
		List<String> labels = List.of(
			getText("strikes"), getText("game_strikes"), getText("mutes"),
			getText("kicks"), getText("bans"), getText("roles")
		);
		for (String label : labels) {
			g.drawString(label, x, y);
			x+=labelStepX;
		}
		x+=30;
		g.drawString(getText("total"), x, y);

		// List users
		y += 40;
		g.setFont(Fonts.Roboto.regular.deriveFont(Font.PLAIN, 20F));

		for (ReportData data : reportData) {
			x = startingX;
			g.drawString("@"+data.getMember().getUser().getName(), x, y); // Name
			x += 400;
			for (String v : data.getCountValues()) {
				g.drawString(v, x, y);
				x+=labelStepX;
			}
			x += 30;
			g.drawString(data.getCountTotal(), x, y);
			y += 40;
		}

		return image;
	}

	private String getText(String path) {
		return lu.getLocalized(locale, "bot.moderation.modstats."+path);
	}


}