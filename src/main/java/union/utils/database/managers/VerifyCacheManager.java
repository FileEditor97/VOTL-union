package union.utils.database.managers;

import java.util.List;

import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

public class VerifyCacheManager extends LiteDBBase {
	
	private final String table = "verified";

	public VerifyCacheManager(ConnectionUtil cu) {
		super(cu);
	}

	public void addUser(long discordId, long steam64) {
		execute("INSERT INTO %s(discordId, steam64) VALUES (%s, %s) ON CONFLICT(discordId) DO UPDATE SET steam64=%s".formatted(table, discordId, steam64, steam64));
	}

	public void addForcedUser(long discordId) {
		execute("INSERT INTO %s(discordId, forced) VALUES (%s, 1) ON CONFLICT(discordId) DO UPDATE SET forced=1".formatted(table, discordId));
	}

	public void removeByDiscord(long discordId) {
		execute("DELETE FROM %s WHERE (discordId=%s)".formatted(table, discordId));
	}

	public void removeBySteam(long steam64) {
		execute("DELETE FROM %s WHERE (steam64=%s)".formatted(table, steam64));
	}

	public List<Long> getForcedUsers() {
		return select("SELECT discordId FROM %s WHERE (forced=1)".formatted(table), "discordId", Long.class);
	}

	public boolean isVerified(long discordId) {
		return selectOne("SELECT discordId FROM %s WHERE (discordId=%s)".formatted(table, discordId), "discordId", Long.class) != null;
	}

	public boolean isForced(long discordId) {
		return selectOne("SELECT discordId FROM %s WHERE (discordId=%s AND forced=1)".formatted(table, discordId), "discordId", Long.class) != null;
	}

	@Deprecated
	public void setSteam64(long discordId, long steam64) {
		execute("UPDATE %s SET steam64=%s WHERE (discordId=%s)".formatted(table, steam64, discordId));
	}

	public void removeSteam64(long discordId) {
		execute("UPDATE %s SET steam64=NULL WHERE (discordId=%s)".formatted(table, discordId));
	}

	public Long getSteam64(long discordId) {
		return selectOne("SELECT steam64 FROM %s WHERE (discordId=%s)".formatted(table, discordId), "steam64", Long.class);
	}

	public Long getDiscordId(long steam64) {
		return selectOne("SELECT discordId FROM %s WHERE (steam64=%s)".formatted(table, quote(steam64)), "discordId", Long.class);
	}

	public void purgeVerified() {
		execute("DELETE FROM %s WHERE (forced=0)".formatted(table));
	}

	public Integer count() {
		return count("SELECT COUNT(*) FROM %s".formatted(table));
	}
	
}
