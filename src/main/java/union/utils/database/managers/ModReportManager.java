package union.utils.database.managers;

import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ModReportManager extends LiteDBBase {
	public ModReportManager(ConnectionUtil cu) {
		super(cu, "modReport");
	}

	public void setup(long guildId, long channelId, String roleIds, LocalDateTime nextReport, int interval) {
		execute(("INSERT INTO %s(guildId, channelId, roleIds, nextReport, interval) VALUES (%d, %d, %s, %d, %d)"+
			"ON CONFLICT(guildId) DO UPDATE SET channelId=%3$d, roleIds=%4$s, nextReport=%5$d, interval=%6$d"
			).formatted(table, guildId, channelId, roleIds, nextReport.toEpochSecond(ZoneOffset.UTC), interval));
	}

	public void removeGuild(long guildId) {
		execute("DELETE FROM %s WHERE (guildId = %d)".formatted(table, guildId));
	}

	public void updateNext(long channelId, LocalDateTime nextReport) {
		execute("UPDATE %s SET nextReport = %d WHERE (channelId = %d)"
			.formatted(table, nextReport.toEpochSecond(ZoneOffset.UTC), channelId));
	}

	public List<Map<String, Object>> getExpired(LocalDateTime now) {
		List<Map<String, Object>> list = select("SELECT * FROM %s WHERE (nextReport<=%d)"
			.formatted(table, now.toEpochSecond(ZoneOffset.UTC)),
			Set.of("guildId", "channelId", "roleIds", "nextReport", "interval")
		);
		if (list.isEmpty()) return List.of();
		return list;
	}
}
