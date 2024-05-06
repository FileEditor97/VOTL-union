package union.objects;

import java.util.HashMap;
import java.util.Map;

/* 
 * Access is distributed through 0-10 integer levels, where
 * 10 - highest (bot developer)
 * 0 - lowest (public access)
 */
public enum CmdAccessLevel {
	ALL     (0, "everyone"),		// Default
	EXCEPT  (1, "automod exception"),// Role
	HELPER	(2, "helper"),			// Role
	MOD     (3, "moderator"),		// Role
	ADMIN   (5, "administrator"),	// by Permission (Administrator)
	OPERATOR(7, "group operator"),	// User
	OWNER   (8, "server owner"),	// by Guild info
	DEV     (10, "bot developer");	// by config

	private final Integer level;
	private final String name;

	private static final Map<Integer, CmdAccessLevel> BY_LEVEL = new HashMap<Integer, CmdAccessLevel>();

	static {
		for (CmdAccessLevel al : CmdAccessLevel.values()) {
			BY_LEVEL.put(al.getLevel(), al);
		}
	}

	CmdAccessLevel(Integer level, String name) {
		this.level = level;
		this.name = name;
	}

	public Integer getLevel() {
		return level;
	}

	public String getName() {
		return name;
	}

	public boolean isHigherThan(CmdAccessLevel other) {
		return(this.level > other.getLevel());
	}

	public boolean isLowerThan(CmdAccessLevel other) {
		return(this.level < other.getLevel());
	}

	public static CmdAccessLevel byLevel(Integer level) {
		return BY_LEVEL.get(level);
	}

}
