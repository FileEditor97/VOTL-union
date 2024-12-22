package union.utils.database.managers;

import static union.utils.CastUtil.getOrDefault;
import static union.utils.CastUtil.requireNonNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import union.objects.CaseType;
import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

public class CaseManager extends LiteDBBase {

	private final Set<String> fullCaseKeys = Set.of("rowId", "localId", "type", "targetId",
		"targetTag", "modId", "modTag", "guildId", "reason",
		"timeStart", "duration", "active", "logUrl");
	
	public CaseManager(ConnectionUtil cu) {
		super(cu, "cases");
	}

	// add new case
	public CaseData add(CaseType type, long userId, String userName, long modId, String modName, long guildId, String reason, Instant timeStart, Duration duration) {
		int rowId = executeWithRow("INSERT INTO %s(type, targetId, targetTag, modId, modTag, guildId, reason, timeStart, duration, active) VALUES (%d, %d, %s, %d, %s, %d, %s, %d, %d, %d)"
			.formatted(table, type.getType(), userId, quote(userName), modId, quote(modName), guildId, quote(reason),
			timeStart.getEpochSecond(), duration == null ? -1 : duration.getSeconds(), type.isActiveInt()));
		if (rowId == 0) return null;
		execute("UPDATE %s SET localId=(SELECT IFNULL(MAX(localId), 0) + 1 FROM cases WHERE guildId=%s) WHERE rowId=%s;".formatted(table, guildId, rowId));
		return getInfo(rowId);
	}

	// update case reason
	public boolean updateReason(int rowId, String reason) {
		return execute("UPDATE %s SET reason=%s WHERE (rowId=%d)".formatted(table, quote(reason), rowId));
	}

	// update case duration
	public boolean updateDuration(int rowId, Duration duration) {
		return execute("UPDATE %s SET duration=%d WHERE (rowId=%d)".formatted(table, duration.getSeconds(), rowId));
	}

	// set case inactive
	public void setInactive(int rowId) {
		execute("UPDATE %s SET active=0 WHERE (rowId=%d)".formatted(table, rowId));
	}

	public void setLogUrl(int rowId, String logUrl) {
		if (logUrl==null) return;
		execute("UPDATE %s SET logUrl=%s WHERE (rowId=%d)".formatted(table, quote(logUrl), rowId));
	}

	// get case info
	public CaseData getInfo(int rowId) {
		Map<String, Object> data = selectOne("SELECT * FROM %s WHERE (rowId=%d)".formatted(table, rowId), fullCaseKeys);
		if (data == null) return null;
		return new CaseData(data);
	}

	// get case info
	public CaseData getInfo(long guildId, int localId) {
		Map<String, Object> data = selectOne("SELECT * FROM %s WHERE (guildId=%d AND localId=%d)".formatted(table, guildId, localId), fullCaseKeys);
		if (data == null) return null;
		return new CaseData(data);
	}

	// get 10 cases for guild's user sorted in pages
	public List<CaseData> getGuildUser(long guildId, long userId, int page) {
		List<Map<String, Object>> data = select("SELECT * FROM %s WHERE (guildId=%d AND targetId=%d) ORDER BY rowId DESC LIMIT 10 OFFSET %d"
				.formatted(table, guildId, userId, (page-1)*10), fullCaseKeys);
		if (data.isEmpty()) return Collections.emptyList();
		return data.stream().map(CaseData::new).toList();
	}

	// get 10 cases for guild's user sorted in pages, active or inactive only
	public List<CaseData> getGuildUser(long guildId, long userId, int page, boolean active) {
		List<Map<String, Object>> data = select("SELECT * FROM %s WHERE (guildId=%d AND targetId=%d AND active=%d) ORDER BY rowId DESC LIMIT 10 OFFSET %d"
			.formatted(table, guildId, userId, active?1:0, (page-1)*10), fullCaseKeys);
		if (data.isEmpty()) return Collections.emptyList();
		return data.stream().map(CaseData::new).toList();
	}

	// get user active temporary cases data
	public CaseData getMemberActive(long userId, long guildId, CaseType type) {
		Map<String, Object> data = selectOne("SELECT * FROM %s WHERE (guildId=%d AND targetId=%d AND type=%d AND active=1)"
			.formatted(table, guildId, userId, type.getType()), fullCaseKeys);
		if (data == null) return null;
		return new CaseData(data);
	}

	// set all ban cases for user inactive
	public boolean setInactiveStrikeCases(long userId, long guildId) {
		return execute("UPDATE %s SET active=0 WHERE (targetId=%d AND guildId=%d AND type>20)".formatted(table, userId, guildId));
	}

	// set all strike cases for user inactive
	// Better way for this is harder...
	public void setInactiveByType(long userId, long guildId, CaseType type) {
		execute("UPDATE %s SET active=0 WHERE (targetId=%d AND guildId=%d AND type=%d)".formatted(table, userId, guildId, type.getType()));
	}

	// get case pages
	public int countCases(long guildId, long userId) {
		return count("SELECT COUNT(*) FROM %s WHERE (guildId=%d AND targetId=%d)".formatted(table, guildId, userId));
	}

	// count cases by moderator after certain date
	public Map<Integer, Integer> countCasesByMod(long guildId, long modId, Instant afterTime) {
		List<Map<String, Object>> data = select("SELECT type, COUNT(*) AS cc FROM %s WHERE (guildId=%d AND modId=%d AND timeStart>%d) GROUP BY type"
			.formatted(table, guildId, modId, afterTime.getEpochSecond()), Set.of("type", "cc"));
		if (data.isEmpty()) return Collections.emptyMap();
		return data.stream().collect(Collectors.toMap(s -> (Integer) s.get("type"), s -> (Integer) s.get("cc")));
	}

	// count all cases by moderator
	public Map<Integer, Integer> countCasesByMod(long guildId, long modId) {
		List<Map<String, Object>> data = select("SELECT type, COUNT(*) AS cc FROM %s WHERE (guildId=%d AND modId=%d) GROUP BY type"
			.formatted(table, guildId, modId), Set.of("type", "cc"));
		if (data.isEmpty()) return Collections.emptyMap();
		return data.stream().collect(Collectors.toMap(s -> (Integer) s.get("type"), s -> (Integer) s.get("cc")));
	}

	// count all cases by moderator after and before certain dates
	public Map<Integer, Integer> countCasesByMod(long guildId, long modId, Instant afterTime, Instant beforeTime) {
		List<Map<String, Object>> data = select("SELECT type, COUNT(*) AS cc FROM %s WHERE (guildId=%d AND modId=%d AND timeStart>%d AND timeStart<%d) GROUP BY type"
			.formatted(table, guildId, modId, afterTime.getEpochSecond(), beforeTime.getEpochSecond()), Set.of("type", "cc"));
		if (data.isEmpty()) return Collections.emptyMap();
		return data.stream().collect(Collectors.toMap(s -> (Integer) s.get("type"), s -> (Integer) s.get("cc")));
	}

	//  BANS
	// get all active expired bans
	public List<CaseData> getExpired() {
		List<Map<String, Object>> data = select("SELECT * FROM %s WHERE (active=1 AND type<20 AND duration>0 AND timeStart+duration<%d) ORDER BY rowId DESC LIMIT 10"
			.formatted(table, Instant.now().getEpochSecond()), fullCaseKeys);
		if (data.isEmpty()) return Collections.emptyList();
		return data.stream().map(CaseData::new).toList();
	}

	public static class CaseData {
		private final int rowId, localId;
		private final CaseType type;
		private final long targetId, guildId;
		private final Long modId;
		private final String targetTag, modTag, reason, logUrl;
		private final Instant timeStart;
		private final Duration duration;
		private final boolean active;

		public CaseData(Map<String, Object> map) {
			this.rowId = requireNonNull(map.get("rowId"));
			this.localId = requireNonNull(map.get("localId"));
			this.type = CaseType.byType(requireNonNull(map.get("type")));
			this.targetId = requireNonNull(map.get("targetId"));
			this.targetTag = getOrDefault(map.get("targetTag"), null);
			this.modId = getOrDefault(map.get("modId"), 0L);
			this.modTag = getOrDefault(map.get("modTag"), null);
			this.guildId = requireNonNull(map.get("guildId"));
			this.reason = getOrDefault(map.get("reason"), null);
			this.timeStart = Instant.ofEpochSecond(getOrDefault(map.get("timeStart"), 0L));
			this.duration = Duration.ofSeconds(getOrDefault(map.get("duration"), 0L));
			this.active = ((Integer) requireNonNull(map.get("active"))) == 1;
			this.logUrl = getOrDefault(map.get("logUrl"), null);
		}

		public int getRowId() {
			return rowId;
		}

		public String getLocalId() {
			return String.valueOf(localId);
		}
		public int getLocalIdInt() {
			return localId;
		}
		public CaseType getCaseType() {
			return type;
		}

		public long getTargetId() {
			return targetId;
		}
		public String getTargetTag() {
			return targetTag;
		}

		public Long getModId() {
			return modId;
		}
		public String getModTag() {
			return modTag;
		}

		public long getGuildId() {
			return guildId;
		}

		public String getReason() {
			return reason;
		}

		public Instant getTimeStart() {
			return timeStart;
		}

		public Duration getDuration() {
			return duration;
		}

		public boolean isActive() {
			return active;
		}

		public String getLogUrl() {
			return logUrl;
		}

		public Instant getTimeEnd() {
			return duration.isZero() ? null : timeStart.plus(duration);
		}
	}

}
