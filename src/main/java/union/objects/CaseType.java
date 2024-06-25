package union.objects;

import java.util.HashMap;
import java.util.Map;

public enum CaseType {
	BAN(1, "case_type.ban", true),
	MUTE(2, "case_type.mute", true),
	KICK(3, "case_type.kick", false),
	BLACKLIST(10, "case_type.blacklist", true),
	UNBAN(11, "case_type.unban", false),
	UNMUTE(12, "case_type.unmute", false),
	STRIKE_1(21, "case_type.strike1", true),
	STRIKE_2(22, "case_type.strike2", true),
	STRIKE_3(23, "case_type.strike3", true);

	private final Integer type;
	private final String path;
	private final Boolean active;

	private static final Map<Integer, CaseType> BY_TYPE = new HashMap<>();

	static {
		for (CaseType ct : CaseType.values()) {
			BY_TYPE.put(ct.getType(), ct);
		}
	}

	CaseType(Integer type, String path, Boolean active) {
		this.type = type;
		this.path = path;
		this.active = active;
	}

	public int getType() {
		return type;
	}

	public String getPath() {
		return path;
	}

	public int isActiveInt() {
		return active ? 1 : 0;
	}

	@Override
	public String toString() {
		return this.name().toLowerCase();
	}

	public static CaseType byType(Integer type) {
		return BY_TYPE.get(type);
	}
	
}
