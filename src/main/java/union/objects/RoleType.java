package union.objects;

import java.util.HashMap;
import java.util.Map;

public enum RoleType {
	ASSIGN(0, "role_type.assign"),
	ASSIGN_TEMP(1, "role_type.assign_temp"),
	TOGGLE(2, "role_type.toggle"),
	CUSTOM(3, "role_type.custom");

	private final int type;
	private final String path;

	private static final Map<Integer, RoleType> BY_TYPE;

	static {
		BY_TYPE = new HashMap<>();
		for (RoleType rt : RoleType.values()) {
			BY_TYPE.put(rt.getType(), rt);
		}
	}

	RoleType(int type, String path) {
		this.type = type;
		this.path = path;
	}

	public int getType() {
		return type;
	}

	public String getPath() {
		return path;
	}

	@Override
	public String toString() {
		return this.name().toLowerCase();
	}

	public static RoleType byType(int type) {
		return BY_TYPE.get(type);
	}

	public static RoleType byName(String name) {
		for (RoleType rt : RoleType.values()) {
			if (rt.toString().equalsIgnoreCase(name))
				return rt;
		}
		return null;
	}
}
