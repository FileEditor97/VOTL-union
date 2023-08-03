package union.objects;

public enum CmdModule {
	WEBHOOK("modules.webhook"),
	MODERATION("modules.moderation"),
	VERIFICATION("modules.verification"),
	TICKETING("modules.ticketing");
	
	private final String path;
	
	CmdModule(String path) {
		this.path = path;
	}

	public String getPath() {
		return path;
	}

}
