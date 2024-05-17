package union.utils.database.managers;

import static union.utils.CastUtil.getOrDefault;
import static union.utils.CastUtil.requireNonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import union.objects.constants.Constants;
import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.internal.interactions.component.ButtonImpl;

public class TicketTagManager extends LiteDBBase {

	public TicketTagManager(ConnectionUtil cu) {
		super(cu, "ticketTag");
	}

	public void createTag(String guildId, Integer panelId, Integer tagType, String buttonText, String emoji, String categoryId, String message, String supportRoleIds, String ticketName, Integer buttonStyle) {
		List<String> keys = new ArrayList<>(10);
		List<String> values = new ArrayList<>(10);
		keys.addAll(List.of("guildId", "panelId", "tagType", "buttonText", "ticketName", "buttonStyle"));
		values.addAll(List.of(guildId, panelId.toString(), tagType.toString(), quote(buttonText), quote(ticketName), buttonStyle.toString()));
		if (emoji != null) {
			keys.add("emoji");
			values.add(quote(emoji));
		}
		if (categoryId != null) {
			keys.add("location");
			values.add(categoryId);
		}
		if (message != null) {
			keys.add("message");
			values.add(replaceNewline(message));
		}
		if (supportRoleIds != null) {
			keys.add("supportRoles");
			values.add(quote(supportRoleIds));
		}
		execute("INSERT INTO %s(%s) VALUES (%s)".formatted(table, String.join(", ", keys), String.join(", ", values)));
	}

	public Integer getIncrement() {
		return getIncrement(table);
	}

	public void deleteTag(Integer tagId) {
		execute("DELETE FROM %s WHERE (tagId=%d)".formatted(table, tagId));
	}

	public void deleteAll(String guildId) {
		execute("DELETE FROM %s WHERE (guildId=%s)".formatted(table, guildId));
	}

	public void updateTag(Integer tagId, Integer tagType, String buttonText, String emoji, String categoryId, String message, String supportRoleIds, String ticketName, Integer buttonStyle) {
		List<String> values = new ArrayList<String>();
		if (tagType != null) 
			values.add("tagType="+tagType);
		if (buttonText != null) 
			values.add("buttonText="+quote(buttonText));
		if (emoji != null) 
			values.add("emoji="+quote(emoji));
		if (categoryId != null) 
			values.add("location="+categoryId);
		if (message != null) 
			values.add("message="+replaceNewline(message));
		if (supportRoleIds != null) 
			values.add("supportRoles="+quote(supportRoleIds));
		if (ticketName != null) 
			values.add("ticketName="+quote(ticketName));
		if (buttonStyle != -1) 
			values.add("buttonStyle="+buttonStyle);
		
		if (!values.isEmpty()) execute("UPDATE %s SET %s WHERE (tagId=%d)".formatted(table, String.join(", ", values), tagId));
	}

	public String getGuildId(Integer tagId) {
		return selectOne("SELECT guildId FROM %s WHERE (tagId=%d)".formatted(table, tagId), "guildId", String.class);
	}

	public List<Integer> getTagIds(String guildId) {
		return select("SELECT tagId FROM %s WHERE (guildId=%s)".formatted(table, guildId), "tagId", Integer.class);
	}

	public Integer countPanelTags(Integer panelId) {
		return count("SELECT COUNT(*) FROM %s WHERE (panelId=%d)".formatted(table, panelId));
	}

	public Integer getPanelId(Integer tagId) {
		return selectOne("SELECT panelId FROM %s WHERE (tagId=%d)".formatted(table, tagId), "panelId", Integer.class);
	}

	public String getTagText(Integer tagId) {
		return selectOne("SELECT buttonText FROM %s WHERE (tagId=%d)".formatted(table, tagId), "buttonText", String.class);
	}

	public Map<Integer, String> getTagsText(String guildId) {
		List<Map<String, Object>> data = select("SELECT tagId, buttonText FROM %s WHERE (guildId=%s)".formatted(table, guildId), Set.of("tagId", "buttonText"));
		if (data.isEmpty()) return Collections.emptyMap();
		return data.stream().limit(25).collect(Collectors.toMap(s -> (Integer) s.get("tagId"), s -> (String) s.get("buttonText")));
	}

	public List<Button> getPanelTags(Integer panelId) {
		List<Map<String, Object>> data = select("SELECT * FROM %s WHERE (panelId=%s)".formatted(table, panelId),
			Set.of("tagId", "buttonText", "buttonStyle", "emoji")
		);
		if (data.isEmpty()) return Collections.emptyList();
		return data.stream().map(Tag::createButton).toList();
	}

	public Tag getTagFull(Integer tagId) {
		Map<String, Object> data = selectOne("SELECT * FROM %s WHERE (tagId=%s)".formatted(table, tagId),
			Set.of("buttonText", "buttonStyle", "emoji", "tagType", "location", "message", "supportRoles", "ticketName")
		);
		if (data==null) return null;
		return new Tag(data, true);
	}

	public Tag getTagInfo(Integer tagId) {
		Map<String, Object> data = selectOne("SELECT * FROM %s WHERE (tagId=%s)".formatted(table, tagId),
			Set.of("tagType", "location", "message", "supportRoles", "ticketName")
		);
		if (data==null) return null;
		return new Tag(data, false);
	}

	private String replaceNewline(final String text) {
		return quote(text).replace("\\n", "<br>");
	}

	public static class Tag {
		private final int tagType; // 0 - Thread, 1 - Channel
		private final String buttonText;
		private final String ticketName;
		private final ButtonStyle buttonStyle;
		private final Emoji emoji;
		private final String location;
		private final String message;
		private final String supportRoles;

		public Tag(Map<String, Object> map, boolean includeButton) {
			this.tagType = requireNonNull(map.get("tagType"));
			this.ticketName = requireNonNull(map.get("ticketName"));
			this.location = getOrDefault(map.get("location"), null);
			this.message = setNewline(getOrDefault(map.get("message"), null));
			this.supportRoles = getOrDefault(map.get("supportRoles"), null);
			if (includeButton) {
				this.buttonText = requireNonNull(map.get("buttonText"));
				this.buttonStyle = ButtonStyle.fromKey(getOrDefault(map.get("buttonStyle"), 0));
				this.emoji = Optional.ofNullable((String) map.get("emoji")).map(Emoji::fromFormatted).orElse(null);
			} else {
				this.buttonText = null;
				this.buttonStyle = null;
				this.emoji = null;
			}
		}
		
		private String setNewline(String text) {
			if (text==null) return null;
			return text.replaceAll("<br>", "\n");
		}

		public EmbedBuilder getPreviewEmbed(Function<String, String> locale, Integer tagId) {
			return new EmbedBuilder().setColor(Constants.COLOR_DEFAULT)
				.setTitle("Tag ID: %d".formatted(tagId))
				.addField(locale.apply(".type"), (tagType > 1 ? "Channel" : "Thread"), true)
				.addField(locale.apply(".name"), "`"+ticketName+"`", true);
		}

		public Button previewButton() {
			return new ButtonImpl("tag_preview", buttonText, buttonStyle, null, true, emoji);
		}

		public static Button createButton(Map<String, Object> map) {
			int tagId = requireNonNull(map.get("tagId"));
			String buttonText = requireNonNull(map.get("buttonText"));
			ButtonStyle style = ButtonStyle.fromKey(requireNonNull(map.get("buttonStyle")));
			Emoji emoji = Optional.ofNullable((String) map.get("emoji")).map(Emoji::fromFormatted).orElse(null);
			return new ButtonImpl("tag:"+tagId, buttonText, style, null, false, emoji);
		}

		public String getTicketName() {
			return ticketName;
		}

		public int getTagType() {
			return tagType;
		}

		public String getLocation() {
			return location;
		}

		public String getMessage() {
			return message;
		}

		public String getSupportRoles() {
			return supportRoles;
		}
	}
	
}
