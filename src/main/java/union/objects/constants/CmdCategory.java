package union.objects.constants;

import union.base.command.Category;

public final class CmdCategory {
	private CmdCategory() {
		throw new IllegalStateException("Utility class");
	}

	public static final Category GUILD = new Category("guild");
	public static final Category OWNER = new Category("owner");
	public static final Category WEBHOOK = new Category("webhook");
	public static final Category MODERATION = new Category("moderation");
	public static final Category VERIFICATION = new Category("verification");
	public static final Category TICKETING = new Category("ticketing");
	public static final Category VOICE = new Category("voice");
	public static final Category ROLES = new Category("roles");
	public static final Category OTHER = new Category("other");
}
