package union.utils.database.managers;

import java.util.List;

import union.utils.database.DBUtil;
import union.utils.database.LiteDBBase;

public class GuildVoiceManager extends LiteDBBase {
	
	private final String TABLE = "guildVoice";

	public GuildVoiceManager(DBUtil util) {
		super(util);
	}

	public boolean exists(String guildId) {
		if (selectOne(TABLE, "guildId", "guildId", guildId) == null) return false;
		return true;
	}

	public void setup(String guildId, String categoryId, String channelId) {
		if (exists(guildId)) {
			update(TABLE, List.of("categoryId", "channelId"), List.of(categoryId, channelId), "guildId", guildId);
		} else {
			insert(TABLE, List.of("guildId", "categoryId", "channelId"), List.of(guildId, categoryId, channelId));
		}
	}

	public void remove(String guildId) {
		delete(TABLE, "guildId", guildId);
	}

	public void setName(String guildId, String defaultName) {
		update(TABLE, "defaultName", defaultName, "guildId", guildId);
	}

	public void setLimit(String guildId, Integer defaultLimit) {
		update(TABLE, "defaultLimit", defaultLimit, "guildId", guildId);
	}

	public String getCategory(String guildId) {
		Object data = selectOne(TABLE, "categoryId", "guildId", guildId);
		if (data == null) return null;
		return (String) data;
	}

	public String getChannel(String guildId) {
		Object data = selectOne(TABLE, "channelId", "guildId", guildId);
		if (data == null) return null;
		return (String) data;
	}

	public String getName(String guildId) {
		Object data = selectOne(TABLE, "defaultName", "guildId", guildId);
		if (data == null) return null;
		return (String) data;
	}

	public Integer getLimit(String guildId) {
		Object data = selectOne(TABLE, "defaultLimit", "guildId", guildId);
		if (data == null) return 0;
		return (Integer) data;
	}
}
