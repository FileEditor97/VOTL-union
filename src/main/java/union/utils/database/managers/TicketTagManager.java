package union.utils.database.managers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import union.utils.database.DBUtil;
import union.utils.database.LiteDBBase;

public class TicketTagManager extends LiteDBBase {
	
	private final String TABLE = "ticketTag";

	public TicketTagManager(DBUtil dbUtil) {
		super(dbUtil);
	}

	public void createTag(String guildId, Integer panelId, Integer tagType, String buttonText, String emoji, String categoryId, String message, String supportRoleIds, String ticketName, Integer buttonStyle) {
		List<String> keys = new ArrayList<>(10);
		List<Object> values = new ArrayList<>(10);
		keys.addAll(List.of("guildId", "panelId", "tagType", "buttonText", "ticketName", "buttonStyle"));
		values.addAll(List.of(guildId, panelId, tagType, buttonText, ticketName, buttonStyle));
		if (emoji != null) {
			keys.add("emoji");
			values.add(emoji);
		}
		if (categoryId != null) {
			keys.add("location");
			values.add(categoryId);
		}
		if (message != null) {
			keys.add("message");
			values.add(removeNewline(message));
		}
		if (supportRoleIds != null) {
			keys.add("supportRoles");
			values.add(supportRoleIds);
		}
		insert(TABLE, keys, values);
	}

	public Integer getIncrement() {
		return getIncrement(TABLE);
	}

	public void delete(Integer tagId) {
		delete(TABLE, "tagId", tagId);
	}

	public void deleteAll(String guildId) {
		delete(TABLE, "guildId", guildId);
	}

	public void updateTag(Integer tagId, Integer tagType, String buttonText, String emoji, String categoryId, String message, String supportRoleIds, String ticketName, Integer buttonStyle) {
		List<String> keys = new ArrayList<>(8);
		List<Object> values = new ArrayList<>(8);
		if (tagType != null) {
			keys.add("tagType");
			values.add(tagType);
		}
		if (buttonText != null) {
			keys.add("buttonText");
			values.add(buttonText);
		}
		if (emoji != null) {
			keys.add("emoji");
			values.add(emoji);
		}
		if (categoryId != null) {
			keys.add("location");
			values.add(categoryId);
		}
		if (message != null) {
			keys.add("message");
			values.add(removeNewline(message));
		}
		if (supportRoleIds != null) {
			keys.add("supportRoles");
			values.add(supportRoleIds);
		}
		if (ticketName != null) {
			keys.add("ticketName");
			values.add(ticketName);
		}
		if (buttonStyle != -1) {
			keys.add("buttonStyle");
			values.add(buttonStyle);
		}
		if (keys.size() > 0) update(TABLE, keys, values, "tagId", tagId);
	}

	public String getGuildId(Integer tagId) {
		Object data = selectOne(TABLE, "guildId", "tagId", tagId);
		if (data == null) return null;
		return (String) data;
	}

	public List<Integer> getTagIds(String guildId) {
		List<Object> data = select(TABLE, "tagId", "guildId", guildId);
		if (data.isEmpty()) return Collections.emptyList();
		return data.stream().map(obj -> (Integer) obj).toList();
	}

	public Integer countPanelTags(Integer panelId) {
		return countSelect(TABLE, List.of("panelId"), List.of(panelId));
	}

	public Integer getPanelId(Integer tagId) {
		Object data = selectOne(TABLE, "panelId", "tagId", tagId);
		if (data == null) return null;
		return (Integer) data;
	}

	public String getTagText(Integer tagId) {
		Object data = selectOne(TABLE, "buttonText", "tagId", tagId);
		if (data == null) return null;
		return (String) data;
	}

	public Map<Integer, String> getTagsText(String guildId) {
		List<Map<String, Object>> data = select(TABLE, List.of("tagId", "buttonText"), "guildId", guildId);
		if (data.isEmpty()) return Collections.emptyMap();
		return data.stream().limit(25).collect(Collectors.toMap(s -> (Integer) s.get("tagId"), s -> (String) s.get("buttonText")));
	}

	public List<Map<String, Object>> getPanelTags(Integer panelId) {
		List<Map<String, Object>> data = select(TABLE, List.of("tagId", "buttonText", "buttonStyle", "emoji"), "panelId", panelId);
		if (data.isEmpty()) return Collections.emptyList();
		return data;
	}

	public Map<String, Object> getTag(Integer tagId) {
		Map<String, Object> data = selectOne(TABLE, List.of("buttonText", "buttonStyle", "emoji", "tagType", "location", "message", "supportRoles", "ticketName"), "tagId", tagId);
		if (data.isEmpty()) return null;
		return data;
	}

	public Map<String, Object> getTagInfo(Integer tagId) {
		Map<String, Object> data = selectOne(TABLE, List.of("tagType", "location", "message", "supportRoles", "ticketName"), "tagId", tagId);
		if (data.isEmpty()) return null;
		return data;
	}

	// Tools
	private String removeNewline(String text) {
		return text.replace("\\n", "<br>");
	}
	
}
