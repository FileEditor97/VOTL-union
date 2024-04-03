package union.objects.logs;

public enum LogEvent {
	CHANNEL_CREATE("create", LogType.CHANNEL),
	CHANNEL_UPDATE("update", LogType.CHANNEL),
	CHANNEL_DELETE("delete", LogType.CHANNEL),
	CHANNEL_OVERRIDE_CREATE("override_create", LogType.CHANNEL),
	CHANNEL_OVERRIDE_UPDATE("override_update", LogType.CHANNEL),
	CHANNEL_OVERRIDE_DELETE("override_delete", LogType.CHANNEL),
	ROLE_CREATE("create", LogType.ROLE),
	ROLE_UPDATE("update", LogType.ROLE),
	ROLE_DELETE("delete", LogType.ROLE),
	GUILD_UPDATE("update", LogType.SERVER),
	EMOJI_CREATE("emoji_create", LogType.SERVER),
	EMOJI_UPDATE("emoji_update", LogType.SERVER),
	EMOJI_DELETE("emoji_delete", LogType.SERVER),
	STICKER_CREATE("sticker_create", LogType.SERVER),
	STICKER_UPDATE("sticker_update", LogType.SERVER),
	STICKER_DELETE("sticker_delete", LogType.SERVER),
	MEMBER_JOIN("join", LogType.MEMBER),
	MEMBER_LEAVE("leave", LogType.MEMBER),
	MEMBER_ROLE_CHANGE("role_change", LogType.MEMBER),
	MEMBER_NAME_CHANGE("name_change", LogType.MEMBER),
	MEMBER_KICK("kick", LogType.MEMBER),
	BAN("ban", LogType.MODERATION),
	UNBAN("unban", LogType.MODERATION),
	TIMEOUT("timeout", LogType.MODERATION),
	REMOVE_TIMEOUT("remove_timeout", LogType.MODERATION),
	VC_JOIN("join", LogType.VOICE),
	VC_LEAVE("leave", LogType.VOICE),
	VC_SWITCH("switch", LogType.VOICE),
	MESSAGE_DELETE("delete", LogType.MESSAGE),
	MESSAGE_BULK_DELETE("bulk_delete", LogType.MESSAGE),
	MESSAGE_UPDATE("update", LogType.MESSAGE),
	//INVITE_SENT("sent", LogType.INVITE),
	OTHER("other");

	private final String path;
	private final LogType type;

	LogEvent(String path, LogType type) {
		this.path = type.getPath()+"."+path;
		this.type = type;
	}

	LogEvent(String path) {
		this.path = LogType.OTHER.getPath()+"."+path;
		this.type = LogType.OTHER;
	}

	public String getPath() {
		return path;
	}

	public LogType getType() {
		return type;
	}

	@Override
	public String toString() {
		return name().toLowerCase();
	}
}
