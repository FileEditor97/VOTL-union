package union.utils.database.managers;

import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

import java.time.Instant;
import java.util.List;

public class GameStrikeManager extends LiteDBBase {

	private final String channels = "gameChannels";
	private final String strikes = "gameStrikes";

	public GameStrikeManager(ConnectionUtil cu) {
		super(cu, null);
	}

	public void removeGuild(long guildId) {
		execute("DELETE FROM gameChannels WHERE (guildId=%s); DELETE FROM gameStrikes WHERE (guildId=%<s)".formatted(guildId));
	}

	public void removeChannel(long channelId) {
		execute("DELETE FROM gameChannels WHERE (channelId=%s); DELETE FROM gameStrikes WHERE (channelId=%<s)".formatted(channelId));
	}

	// Channels
	public void addChannel(long guildId, long channelId, int maxStrike) {
		execute("INSERT INTO %s(guildId, channelId, maxStrikes) VALUES (%s, %s, %s)".formatted(channels, guildId, channelId, maxStrike));
	}

	public void setMaxStrikes(long channelId, int maxStrikes) {
		execute("UPDATE %s SET maxStrikes=%s WHERE (channelId=%s)".formatted(channels, maxStrikes, channelId));
	}

	public Integer getMaxStrikes(long channelId) {
		return selectOne("SELECT maxStrikes FROM %s WHERE (channelId=%s)".formatted(channels, channelId), "maxStrikes", Integer.class);
	}

	public List<Long> getChannels(long guildId) {
		return select("SELECT channelId FROM %s WHERE (guildId=%s)".formatted(channels, guildId), "channelId", Long.class);
	}

	// Strikes
	public void addStrike(long guildId, long channelId, long userId) {
		execute("INSERT INTO %s(guildId, channelId, userId, count, lastUpdate) VALUES (%s, %s, %s, 1, %s) ON CONFLICT(channelId, userId) DO UPDATE SET count=count+1, lastUpdate=%<s".formatted(
			strikes, guildId, channelId, userId, Instant.now().getEpochSecond()
		));
	}

	public Instant getLastUpdate(long channelId, long userId) {
		Long data = selectOne("SELECT lastUpdate FROM %s WHERE (channelId=%s AND userId=%s)".formatted(strikes, channelId, userId), "lastUpdate", Long.class);
		return data==null ? null : Instant.ofEpochSecond(data);
	}

	public Integer countStrikes(long channelId, long userId) {
		return selectOne("SELECT count FROM %s WHERE (channelId=%s AND userId=%s)".formatted(strikes, channelId, userId), "count", Integer.class);
	}

	public void clearStrikes(long channelId, long userId) {
		execute("DELETE FROM %s WHERE (channelId=%s AND userId=%s)".formatted(strikes, channelId, userId));
	}

}
