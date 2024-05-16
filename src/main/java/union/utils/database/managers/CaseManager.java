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

	private final Set<String> fullCaseKeys = Set.of("caseId", "type", "targetId", "targetTag", "modId", "modTag", "guildId", "reason", "timeStart", "duration", "active");
	
	public CaseManager(ConnectionUtil cu) {
		super(cu, "cases");
	}

	// add new case
	public void add(CaseType type, long userId, String userName, long modId, String modName, long guildId, String reason, Instant timeStart, Duration duration) {
		execute("INSERT INTO %s(type, targetId, targetTag, modId, modTag, guildId, reason, timeStart, duration, active) VALUES (%d, %d, %s, %d, %s, %d, %s, %d, %d, %d)"
			.formatted(table, type.getType(), userId, quote(userName), modId, quote(modName), guildId, quote(reason),
			timeStart.getEpochSecond(), duration == null ? -1 : duration.getSeconds(), type.isActiveInt()));
	}

	// update case reason
	public void updateReason(int caseId, String reason) {
		execute("UPDATE %s SET reason=%s WHERE (caseId=%d)".formatted(table, quote(reason), caseId));
	}

	// update case duration
	public void updateDuration(int caseId, Duration duration) {
		execute("UPDATE %s SET duration=%d WHERE (caseId=%d)".formatted(table, duration.getSeconds(), caseId));
	}

	// set case inactive
	public void setInactive(int caseId) {
		execute("UPDATE %s SET active=0 WHERE (caseId=%d)".formatted(table, caseId));
	}

	// get case info
	public CaseData getInfo(int caseId) {
		Map<String, Object> data = selectOne("SELECT * FROM %s WHERE (caseId=%d)".formatted(table, caseId), fullCaseKeys);
		if (data == null) return null;
		return new CaseData(data);
	}

	// get 10 cases for guild's user sorted in pages
	public List<CaseData> getGuildUser(long guildId, long userId, int page, boolean active) {
		List<Map<String, Object>> data = select("SELECT * FROM %s WHERE (guildId=%d AND targetId=%d AND active=%d) ORDER BY caseId DESC LIMIT 10 OFFSET %d"
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
	public void setInactiveStrikeCases(long userId, long guildId) {
		execute("UPDATE %s SET active=0 WHERE (targetId=%d AND guildId=%d AND type>20)".formatted(table, userId, guildId));
	}

	// set all strike cases for user inactive
	// Better way for this is harder...
	public void setInactiveByType(long userId, long guildId, CaseType type) {
		execute("UPDATE %s SET active=0 WHERE (targetId=%d AND guildId=%d AND type=%d)".formatted(table, userId, guildId, type.getType()));
	}

	// get user's last case
	public CaseData getMemberLast(long userId, long guildId) {
		Map<String, Object> data = selectOne("SELECT * FROM %s WHERE (guildId=%d AND targetId=%d) ORDER BY caseId DESC LIMIT 1"
			.formatted(table, guildId, userId), fullCaseKeys);
		if (data == null) return null;
		return new CaseData(data);
	}

	// get case pages
	public Integer countCases(long guildId, long userId) {
		return count("SELECT COUNT(*) FROM %s WHERE (guildId=%d AND targetId=%d)".formatted(table, guildId, userId));
	}

	// count cases by moderator
	public Map<Integer, Integer> countCasesByMod(long guildId, long modId, Instant afterTime) {
		List<Map<String, Object>> data = select("SELECT type, COUNT(*) AS cc FROM %s WHERE (guildId=%d AND modId=%d AND timeStart>%d) GROUP BY type"
			.formatted(table, guildId, modId, afterTime.getEpochSecond()), Set.of("type", "cc"));
		if (data.isEmpty()) return Collections.emptyMap();
		return data.stream().collect(Collectors.toMap(s -> (Integer) s.get("type"), s -> (Integer) s.get("cc")));
	}
	
	public Map<Integer, Integer> countCasesByMod(long guildId, long modId) {
		List<Map<String, Object>> data = select("SELECT type, COUNT(*) AS cc FROM %s WHERE (guildId=%d AND modId=%d) GROUP BY type"
			.formatted(table, guildId, modId), Set.of("type", "cc"));
		if (data.isEmpty()) return Collections.emptyMap();
		return data.stream().collect(Collectors.toMap(s -> (Integer) s.get("type"), s -> (Integer) s.get("cc")));
	}

	//  BANS
	// get all active expired bans
	public List<CaseData> getExpired() {
		List<Map<String, Object>> data = select("SELECT * FROM %s WHERE (active=1 AND type<20 AND duration>0 AND timeStart+duration<%d) ORDER BY caseId DESC LIMIT 10"
			.formatted(table, Instant.now().getEpochSecond()), fullCaseKeys);
		if (data.isEmpty()) return Collections.emptyList();
		return data.stream().map(CaseData::new).toList();
	}

	public static class CaseData {
		private final int caseId;
		private final CaseType type;
		private final long targetId, guildId;
		private final Long modId;
		private final String targetTag, modTag, reason;
		private final Instant timeStart;
		private final Duration duration;
		private final boolean active;

		public CaseData(Map<String, Object> map) {
			this.caseId = requireNonNull(map.get("caseId"));
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
		}

		public String getCaseId() {
			return String.valueOf(caseId);
		}
		public int getCaseIdInt() {
			return caseId;
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
	}

}
