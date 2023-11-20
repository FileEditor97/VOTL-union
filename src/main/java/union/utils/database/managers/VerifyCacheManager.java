package union.utils.database.managers;

import java.util.Collections;
import java.util.List;

import union.utils.database.DBUtil;
import union.utils.database.LiteDBBase;

public class VerifyCacheManager extends LiteDBBase {
	
	private final String TABLE = "verified";

	public VerifyCacheManager(DBUtil util) {
		super(util);
	}

	public void addUser(String discordId, String steam64) {
		insert(TABLE, List.of("discordId", "steam64"), List.of(discordId, steam64));
	}

	public void addForcedUser(String discordId) {
		insert(TABLE, List.of("discordId", "forced"), List.of(discordId, 1));
	}

	public void setForced(String discordId) {
		update(TABLE, "forced", 1, "discordId", discordId);
	}

	public void removeByDiscord(String discordId) {
		delete(TABLE, "discordId", discordId);
	}

	public void removeBySteam(String steam64) {
		delete(TABLE, "steam64", steam64);
	}

	public List<String> getForcedUsers() {
		List<Object> objs = select(TABLE, "discordId", "forced", 1);
		if (objs.isEmpty()) return Collections.emptyList();
		return objs.stream().map(obj -> (String) obj).toList();
	}

	public boolean isVerified(String discordId) {
		if (selectOne(TABLE, "discordId", "discordId", discordId) == null) return false;
		return true;
	}

	public boolean isForced(String discordId) {
		if (selectOne(TABLE, "discordId", List.of("discordId", "forced"), List.of(discordId, 1)) == null) return false;
		return true;
	}

	public void setSteam64(String discordId, String steam64) {
		update(TABLE, "steam64", steam64, "discordId", discordId);
	}

	public void removeSteam64(String discordId) {
		update(TABLE, "steam64", "NULL", "discordId", discordId);
	}

	public String getSteam64(String discordId) {
		Object data = selectOne(TABLE, "steam64", "discordId", discordId);
		if (data == null) return null;
		return (String) data;
	}

	public String getDiscordId(String steam64) {
		Object data = selectOne(TABLE, "discordId", "steam64", steam64);
		if (data == null) return null;
		return (String) data;
	}

	public void purgeVerified() {
		deleteAll(TABLE, "forced", 0);
	}

	public Integer count() {
		return countAll(TABLE);
	}
}
