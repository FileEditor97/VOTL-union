package union.utils.database.managers;

import static union.utils.CastUtil.getOrDefault;
import static union.utils.CastUtil.requireNonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

import net.dv8tion.jda.api.EmbedBuilder;

public class TicketPanelManager extends LiteDBBase {

	public TicketPanelManager(ConnectionUtil cu) {
		super(cu, "ticketPanel");
	}

	public int createPanel(String guildId, String title, String description, String image, String footer) {
		List<String> keys = new ArrayList<>(5);
		List<String> values = new ArrayList<>(5);
		keys.add("guildId");
		values.add(guildId);
		keys.add("title");
		values.add(quote(title));
		if (description != null) {
			keys.add("description");
			values.add(replaceNewline(description));
		}
		if (image != null) {
			keys.add("image");
			values.add(quote(image));
		}
		if (footer != null) {
			keys.add("footer");
			values.add(replaceNewline(footer));
		}
		return executeWithRow("INSERT INTO %s(%s) VALUES (%s)".formatted(table, String.join(", ", keys), String.join(", ", values)));
	}

	public void delete(Integer panelId) {
		execute("DELETE FROM %s WHERE (panelId=%d)".formatted(table, panelId));
	}

	public void deleteAll(String guildId) {
		execute("DELETE FROM %s WHERE (guildId=%s)".formatted(table, guildId));
	}

	public String getGuildId(Integer panelId) {
		return selectOne("SELECT guildId FROM %s WHERE (panelId=%d)".formatted(table, panelId), "guildId", String.class);
	}

	public List<Integer> getPanelIds(String guildId) {
		return select("SELECT panelId FROM %s WHERE (guildId=%s)".formatted(table, guildId), "panelId", Integer.class);
	}

	public void updatePanel(Integer panelId, String title, String description, String image, String footer) {
		List<String> values = new ArrayList<>();
		if (title != null)
			values.add("title="+quote(title));
		if (description != null)
			values.add("description="+replaceNewline(description));
		if (image != null)
			values.add("image="+quote(image));
		if (footer != null)
			values.add("footer="+replaceNewline(footer));

		if (!values.isEmpty()) execute("UPDATE %s SET %s WHERE (panelId=%d)".formatted(table, String.join(", ", values), panelId));
	}

	public Panel getPanel(Integer panelId) {
		Map<String, Object> data = selectOne("SELECT * FROM %s WHERE (panelId=%d)".formatted(table, panelId),
			Set.of("title", "description", "image", "footer"));
		if (data==null) return null;
		return new Panel(data);
	}

	public String getPanelTitle(Integer panelId) {
		return selectOne("SELECT title FROM %s WHERE (panelId=%d)".formatted(table, panelId), "title", String.class);
	}

	public Map<Integer, String> getPanelsText(String guildId) {
		List<Map<String, Object>> data = select("SELECT panelId, title FROM %s WHERE (guildId=%s)".formatted(table, guildId), Set.of("panelId", "title"));
		if (data.isEmpty()) return Collections.emptyMap();
		return data.stream().limit(25).collect(Collectors.toMap(s -> (Integer) s.get("panelId"), s -> (String) s.get("title")));
	}

	public Integer countPanels(String guildId) {
		return count("SELECT COUNT(*) FROM %s WHERE (guildId=%s)".formatted(table, guildId));
	}

	// Tools
	private String replaceNewline(final String text) {
		return quote(text).replace("\\n", "<br>");
	}

	public static class Panel {
		private final String title;
		private final String description;
		private final String image;
		private final String footer;

		public Panel(Map<String, Object> map) {
			this.title = requireNonNull(map.get("title"));
			this.description = setNewline(getOrDefault(map.get("description"), null));
			this.image = getOrDefault(map.get("image"), null);
			this.footer = setNewline(getOrDefault(map.get("footer"), null));
		}
		
		private String setNewline(String text) {
			if (text==null) return null;
			return text.replaceAll("<br>", "\n");
		}

		public EmbedBuilder getPrefiledEmbed(final Integer color) {
			EmbedBuilder builder = new EmbedBuilder().setColor(color)
				.setTitle(title);
			if (description!=null) builder.setDescription(description);
			if (image!=null) builder.setImage(image);
			if (footer!=null) builder.setFooter(footer);
			return builder; 
		}
	}

}
