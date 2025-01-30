package union.utils.database.managers;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import union.objects.constants.Constants;
import union.utils.FixedCache;
import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

public class VerifyCacheManager extends LiteDBBase {
	// Cache
	// discordId - steam64
	private final Cache<Long, Long> cache = Caffeine.newBuilder()
		.maximumSize(1000)
		.expireAfterAccess(1, TimeUnit.DAYS)
		.build();

	public VerifyCacheManager(ConnectionUtil cu) {
		super(cu, "verified");
	}

	public void addUser(long discordId, long steam64) {
		invalidateCache(discordId);
		execute("INSERT INTO %s(discordId, steam64) VALUES (%s, %s) ON CONFLICT(discordId) DO UPDATE SET steam64=%s".formatted(table, discordId, steam64, steam64));
	}

	public void removeByDiscord(long discordId) {
		invalidateCache(discordId);
		execute("DELETE FROM %s WHERE (discordId=%s)".formatted(table, discordId));
	}

	public boolean isVerified(long discordId) {
		if (cache.getIfPresent(discordId) != null)
			return true;
		Long steam64 = selectOne("SELECT steam64 FROM %s WHERE (discordId=%s)".formatted(table, discordId), "steam64", Long.class);
		if (steam64 == null)
			return false;
		cache.put(discordId, steam64);
		return true;
	}

	public Long getSteam64(long discordId) {
		Long steam64 = cache.getIfPresent(discordId);
		if (steam64 != null)
			return steam64;
		steam64 = selectOne("SELECT steam64 FROM %s WHERE (discordId=%s)".formatted(table, discordId), "steam64", Long.class);
		if (steam64 == null)
			return null;
		cache.put(discordId, steam64);
		return steam64;
	}

	public Long getDiscordId(long steam64) {
		// Can't be cached
		return selectOne("SELECT discordId FROM %s WHERE (steam64=%s)".formatted(table, quote(steam64)), "discordId", Long.class);
	}


	public void addForcedUser(long discordId) {
		invalidateCache(discordId);
		execute("INSERT INTO %s(discordId) VALUES (%s) ON CONFLICT(discordId) DO NOTHING".formatted(table, discordId));
	}

	public void forceRemoveSteam64(long discordId) {
		invalidateCache(discordId);
		execute("UPDATE %s SET steam64=0 WHERE (discordId=%s)".formatted(table, discordId));
	}

	public List<Long> getForcedUsers() {
		return select("SELECT discordId FROM %s WHERE (steam64=0)".formatted(table), "discordId", Long.class);
	}

	public boolean isForced(long discordId) {
		Long steam64 = getSteam64(discordId);
		if (steam64 == null)
			return false;
        return steam64 == 0L;
    }

	public Integer count() {
		return count("SELECT COUNT(*) FROM %s".formatted(table));
	}

	private void invalidateCache(long discordId) {
		cache.invalidate(discordId);
	}
}
