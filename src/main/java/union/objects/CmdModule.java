package union.objects;

import java.util.EnumSet;

public enum CmdModule {
	WEBHOOK("modules.webhook", 1),
	MODERATION("modules.moderation", 2),
	STRIKES("modules.strikes", 3),
	VERIFICATION("modules.verification", 4),
	TICKETING("modules.ticketing", 5),
	VOICE("modules.voice", 6),
	REPORT("modules.report", 7),
	ROLES("modules.roles", 8),
	GAMES("modules.games", 9),
	LEVELS("modules.levels", 10);
	
	private final String path;
	private final int value;
	
	CmdModule(String path, int value) {
		this.path = path;
		this.value = (int) Math.pow(2, value-1);
	}

	public String getPath() {
		return path;
	}

	public int getValue() {
		return value;
	}

	public static EnumSet<CmdModule> decodeModules(int data) {
		EnumSet<CmdModule> modules = EnumSet.noneOf(CmdModule.class);
		for (CmdModule v : values()) {
			if ((data & v.value) == v.value) modules.add(v);
		}
		return modules;
	}

	@SuppressWarnings("unused")
	public static int encodeModules(EnumSet<CmdModule> actions) {
		int data = 0;
		for (CmdModule v : actions) {
			data += v.value;
		}
		return data;
	}
}
