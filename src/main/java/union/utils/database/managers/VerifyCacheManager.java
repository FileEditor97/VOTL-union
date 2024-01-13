package union.utils.database.managers;

import java.util.List;

import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

public class VerifyCacheManager extends LiteDBBase {
	
	private final String table = "verified";

	public VerifyCacheManager(ConnectionUtil cu) {
		super(cu);
	}

	public void addUser(String discordId, String steam64) {
		execute("INSERT INTO %s(discordId, steam64) VALUES (%s, %s)".formatted(table, discordId, quote(steam64)));
	}

	public void addForcedUser(String discordId) {
		execute("INSERT INTO %s(discordId, forced) VALUES (%s, 1) ON CONFLICT(discordId) DO UPDATE SET forced=1".formatted(table, discordId));
	}

	public void removeByDiscord(String discordId) {
		execute("DELETE FROM %s WHERE (discordId=%s)".formatted(table, discordId));
	}

	public void removeBySteam(String steam64) {
		execute("DELETE FROM %s WHERE (steam64=%s)".formatted(table, quote(steam64)));
	}

	public List<String> getForcedUsers() {
		return select("SELECT discordId FROM %s WHERE (forced=1)".formatted(table), "discordId", String.class);
	}

	public boolean isVerified(String discordId) {
		return selectOne("SELECT discordId FROM %s WHERE (discordId=%s)".formatted(table, discordId), "discordId", String.class) != null;
	}

	public boolean isForced(String discordId) {
		return selectOne("SELECT discordId FROM %s WHERE (discordId=%s AND forced=1)".formatted(table, discordId), "discordId", String.class) != null;
	}

	public void setSteam64(String discordId, String steam64) {
		execute("UPDATE %s SET steam64=%s WHERE (discordId=%s)".formatted(table, steam64, discordId));
	}

	public void removeSteam64(String discordId) {
		execute("UPDATE %s SET steam64=NULL WHERE (discordId=%s)".formatted(table, discordId));
	}

	public String getSteam64(String discordId) {
		return selectOne("SELECT steam64 FROM %s WHERE (discordId=%s)".formatted(table, discordId), "steam64", String.class);
	}

	public String getDiscordId(String steam64) {
		return selectOne("SELECT discordId FROM %s WHERE (steam64=%s)".formatted(table, quote(steam64)), "discordId", String.class);
	}

	public void purgeVerified() {
		execute("DELETE FROM %s WHERE (forced=0)".formatted(table));
	}

	public Integer count() {
		return count("SELECT COUNT(*) FROM %s".formatted(table));
	}
	
}
