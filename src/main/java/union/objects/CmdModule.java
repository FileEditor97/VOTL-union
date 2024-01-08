package union.objects;

public enum CmdModule {
	WEBHOOK("modules.webhook"),
	MODERATION("modules.moderation"),
	STRIKES("modules.strikes"),
	VERIFICATION("modules.verification"),
	TICKETING("modules.ticketing"),
	VOICE("modules.voice"),
	REPORT("modules.report"),
	ROLES("modules.roles");
	
	private final String path;
	
	CmdModule(String path) {
		this.path = path;
	}

	public String getPath() {
		return path;
	}

}
