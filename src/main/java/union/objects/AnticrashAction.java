package union.objects;

import java.util.HashMap;
import java.util.Map;

public enum AnticrashAction {
	DISABLED(0),
	ROLES(1),
	KICK(2),
	BAN(3);

	public final int value;

	private static final Map<Integer, AnticrashAction> BY_VALUE = new HashMap<>();

	static {
		for (AnticrashAction action : AnticrashAction.values()) {
			BY_VALUE.put(action.value, action);
		}
	}

	AnticrashAction(int value) {
		this.value = value;
	}

	public boolean isDisabled() {
		return this == DISABLED;
	}

	public boolean isEnabled() {
		return this != DISABLED;
	}

	public static AnticrashAction byValue(int value) {
		return BY_VALUE.get(value);
	}
}
