package union.utils.database.managers;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import union.utils.database.LiteDBBase;
import union.objects.CaseType;
import union.utils.database.ConnectionUtil;

public class CaseManager extends LiteDBBase {

	private final String table = "cases";

	private final List<String> fullCaseKeys = List.of("caseId", "type", "targetId", "targetTag", "modId", "modTag", "guildId", "reason", "timeStart", "duration", "active");
	
	public CaseManager(ConnectionUtil cu) {
		super(cu);
	}

	// add new case
	public void add(CaseType type, long userId, String userName, long modId, String modName, long guildId, String reason, Instant timeStart, Duration duration) {
		execute("INSERT INTO %s(type, targetId, targetTag, modId, modTag, guildId, reason, timeStart, duration, active) VALUES (%d, %d, %s, %d, %s, %d, %s, %d, %d, %d)"
			.formatted(table, type.getType(), userId, quote(userName), modId, quote(modName), guildId, quote(reason),
			timeStart.getEpochSecond(), duration.getSeconds(), type.isActiveInt()));
	}

	// get last case's ID
	public Integer getIncrement() {
		return getIncrement(table);
	}

	// update case reason
	public void updateReason(Integer caseId, String reason) {
		execute("UPDATE %s SET reason=%s WHERE (caseId=%d)".formatted(table, quote(reason), caseId));
	}

	// update case duration
	public void updateDuration(Integer caseId, Duration duration) {
		execute("UPDATE %s SET duration=%d WHERE (caseId=%d)".formatted(table, quote(duration.getSeconds()), caseId));
	}

	// set case inactive
	public void setInactive(Integer caseId) {
		execute("UPDATE %s SET active=0 WHERE (caseId=%d)".formatted(table, caseId));
	}

	// get case info
	public CaseData getInfo(Integer caseId) {
		Map<String, Object> data = selectOne("SELECT * FROM %s WHERE (caseId=%d)".formatted(table, caseId), fullCaseKeys);
		if (data == null) return null;
		return new CaseData(data);
	}

	// get 10 guild's cases sorted in pages 
	public List<CaseData> getGuildAll(long guildId, Integer page) {
		List<Map<String, Object>> data = select("SELECT * FROM %s WHERE (guildId=%d) ORDER BY caseId DESC LIMIT 10 OFFSET %d"
			.formatted(table, guildId, (page-1)*10), fullCaseKeys);
		if (data.isEmpty()) return Collections.emptyList();
		return data.stream().map(map -> new CaseData(map)).toList();
	}

	// get 10 cases for guild's user sorted in pages
	public List<CaseData> getGuildTarget(long guildId, long userId, Integer page) {
		List<Map<String, Object>> data = select("SELECT * FROM %s WHERE (guildId=%d AND targetId=%d) ORDER BY caseId DESC LIMIT 10 OFFSET %d"
			.formatted(table, guildId, userId, (page-1)*10), fullCaseKeys);
		if (data.isEmpty()) return Collections.emptyList();
		return data.stream().map(map -> new CaseData(map)).toList();
	}

	// get 10 active timed cases sorted in pages
	public List<CaseData> getActiveTimed(long guildId, Integer page) {
		List<Map<String, Object>> data = select("SELECT * FROM %s WHERE (active=1 AND duration>0 AND guildId=%d) ORDER BY caseId DESC LIMIT 10 OFFSET %d"
			.formatted(table, guildId, (page-1)*10), fullCaseKeys);
		if (data.isEmpty()) return Collections.emptyList();
		return data.stream().map(map -> new CaseData(map)).toList();
	}

	// get user active temporary cases data
	public CaseData getMemberActive(long userId, long guildId, CaseType type) {
		Map<String, Object> data = selectOne("SELECT * FROM %s WHERE (guildId=%d AND targetId=%d AND type=%d AND active=1)"
			.formatted(table, guildId, userId, type.getType()), fullCaseKeys);
		if (data == null) return null;
		return new CaseData(data);
	}

	// get user active temporary cases data
	/* public CaseData getMemberExpirable(long userId, long guildId, CaseType type) {
		Map<String, Object> data = selectOne("SELECT * FROM %s WHERE (guildId=%d AND targetId=%d AND type=%d AND active=1 AND duration>0)"
			.formatted(table, guildId, userId, type.getType()), fullCaseKeys);
		if (data == null) return null;
		return new CaseData(data);
	} */

	// get user's last case
	public CaseData getMemberLast(long userId, long guildId) {
		Map<String, Object> data = selectOne("SELECT * FROM %s WHERE (guildId=%d AND targetId=%d) ORDER BY caseId DESC LIMIT 1"
			.formatted(table, guildId, userId), fullCaseKeys);
		if (data == null) return null;
		return new CaseData(data);
	}

	//  BANS
	// get all active expired bans
	public List<CaseData> getExpired() {
		List<Map<String, Object>> data = select("SELECT * FROM %s WHERE (active=1 AND timeStart+duration<%d) ORDER BY caseId DESC LIMIT 10"
			.formatted(table, Instant.now().getEpochSecond()), fullCaseKeys);
		if (data.isEmpty()) return Collections.emptyList();
		return data.stream().map(map -> new CaseData(map)).toList();
	}

	

	public static class CaseData {
		private final Integer caseId;
		private final CaseType type;
		private final Long targetId;
		private final String targetTag;
		private final Long modId;
		private final String modTag;
		private final Long guildId;
		private final String reason;
		private final Instant timeStart;
		private final Duration duration;
		private final Boolean active;

		public CaseData(Map<String, Object> map) {
			this.caseId = (Integer) map.get("caseId");
			this.type = CaseType.byType((Integer) map.get("type"));
			this.targetId = Long.parseLong((String) map.get("targetId"));
			this.targetTag = (String) map.get("targetTag");
			this.modId = Long.parseLong((String) map.get("modId"));
			this.modTag = (String) map.get("modTag");
			this.guildId = Long.parseLong((String) map.get("guildId"));
			this.reason = (String) map.get("reason");
			this.timeStart = Instant.ofEpochSecond((Integer) map.get("timeStart"));
			this.duration = Duration.ofSeconds(Optional.ofNullable((Integer) map.get("duration")).orElse(0));
			this.active = ((Integer) map.get("active")) == 1;
		}

		public String getCaseId() {
			return String.valueOf(caseId);
		}
		public Integer getCaseIdInt() {
			return caseId;
		}
		public CaseType getCaseType() {
			return type;
		}

		public Long getTargetId() {
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

		public Long getGuildId() {
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

		public Boolean isActive() {
			return active;
		}

		public Instant getTimeEnd() {
			return timeStart.plus(duration);
		}
	}

}
