package union.utils.database.managers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import union.utils.database.DBUtil;
import union.utils.database.LiteDBBase;

public class TicketPanelManager extends LiteDBBase {
	
	private final String TABLE = "ticketPanel";

	public TicketPanelManager(DBUtil dbUtil) {
		super(dbUtil);
	}

	public void createPanel(String guildId, String title, String description, String image, String footer) {
		List<String> keys = new ArrayList<>(5);
		List<Object> values = new ArrayList<>(5);
		keys.add("guildId");
		values.add(guildId);
		keys.add("title");
		values.add(title);
		if (description != null) {
			keys.add("description");
			values.add(removeNewline(description));
		}
		if (image != null) {
			keys.add("image");
			values.add(image);
		}
		if (footer != null) {
			keys.add("footer");
			values.add(removeNewline(footer));
		}
		insert(TABLE, keys, values);
	}

	public Integer getIncrement() {
		return getIncrement(TABLE);
	}

	public void delete(Integer panelId) {
		delete(TABLE, "panelId", panelId);
	}

	public void deleteAll(String guildId) {
		delete(TABLE, "guildId", guildId);
	}

	public String getGuildId(Integer panelId) {
		Object data = selectOne(TABLE, "guildId", "panelId", panelId);
		if (data == null) return null;
		return String.valueOf(data);
	}

	public List<Integer> getPanelIds(String guildId) {
		List<Object> data = select(TABLE, "panelId", "guildId", guildId);
		if (data.isEmpty()) return Collections.emptyList();
		return data.stream().map(obj -> (Integer) obj).collect(Collectors.toList());
	}

	public void updatePanel(Integer panelId, String title, String description, String image, String footer) {
		List<String> keys = new ArrayList<>(4);
		List<Object> values = new ArrayList<>(4);
		if (title != null) {
			keys.add("title");
			values.add(title);
		}
		if (description != null) {
			keys.add("description");
			values.add(removeNewline(description));
		}
		if (image != null) {
			keys.add("image");
			values.add(image);
		}
		if (footer != null) {
			keys.add("footer");
			values.add(removeNewline(footer));
		}
		if (keys.size() > 0) update(TABLE, keys, values, "panelId", panelId);
	}

	@SuppressWarnings("all")
	public Map<String, String> getPanel(Integer panelId) {
		Map<String, Object> data = selectOne(TABLE, List.of("title", "description", "image", "footer"), "panelId", panelId);
		if (data.isEmpty()) return null;
		return (Map) data;
		//return data.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> (String) e.getValue()));
	}

	public String getPanelTitle(Integer panelId) {
		Object data = selectOne(TABLE, "title", "panelId", panelId);
		if (data == null) return null;
		return (String) data;
	}

	public Map<Integer, String> getPanelsText(String guildId) {
		List<Map<String, Object>> data = select(TABLE, List.of("panelId", "title"), "guildId", guildId);
		if (data.isEmpty()) return Collections.emptyMap();
		return data.stream().limit(25).collect(Collectors.toMap(s -> (Integer) s.get("panelId"), s -> (String) s.get("title")));
	}

	public Integer countPanels(String guildId) {
		return countSelect(TABLE, List.of("guildId"), List.of(guildId));
	}

	// Tools
	private String removeNewline(String text) {
		return text.replace("\\n", "<br>");
	}

}
