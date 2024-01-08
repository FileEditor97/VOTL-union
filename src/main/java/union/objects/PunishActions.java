package union.objects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum PunishActions {
	MUTE(1, "punish_action.mute", "t(\\d+)"),		// mute duration
	KICK(2, "punish_action.kick"),
	BAN(4, "punish_action.ban", "t(\\d+)"),		// ban duration
	REMOVE_ROLE(8, "punish_action.remove_role", "rr(\\d+)"),	// role ID
	ADD_ROLE(16, "punish_action.add_role", "ar(\\d+)");		// role ID

	private final Integer type;
	private final String path;
	private final String pattern;
	
	private static final Map<Integer, PunishActions> BY_TYPE = new HashMap<Integer, PunishActions>();

	static {
		for (PunishActions ct : PunishActions.values()) {
			BY_TYPE.put(ct.getType(), ct);
		}
	}

	PunishActions(Integer type, String path, String pattern) {
		this.type = type;
		this.path = path;
		this.pattern = pattern;
	}

	PunishActions(Integer type, String path) {
		this.type = type;
		this.path = path;
		this.pattern = "";
	}

	public Integer getType() {
		return type;
	}

	public String getPath() {
		return path;
	}

	public Pattern getPattern() {
		return Pattern.compile(pattern);
	}

	public static List<PunishActions> decodeActions(Integer data) {
		List<PunishActions> actions = new ArrayList<>();
		for (PunishActions v : values()) {
			if ((data & v.type) == v.type) actions.add(v);
		}
		return actions;
	}

	public static Integer encodeActions(List<PunishActions> actions) {
		Integer data = 0;
		for (PunishActions v : actions) {
			data += v.type;
		}
		return data;
	}

	public static PunishActions byType(Integer type) {
		return BY_TYPE.get(type);
	}

	public String getMatchedValue(String data) {
		Matcher matcher = getPattern().matcher(data);
		if (!matcher.matches()) return null;
		return matcher.group(1);
	}

}
