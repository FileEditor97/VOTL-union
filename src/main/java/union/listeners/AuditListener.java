package union.listeners;

import union.objects.logs.LogType;
import union.utils.database.DBUtil;
import union.utils.logs.LoggingUtil;

import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.events.guild.GuildAuditLogEntryCreateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class AuditListener extends ListenerAdapter {
	
	private final DBUtil db;
	private final LoggingUtil logger;
 
	public AuditListener(DBUtil dbUtil, LoggingUtil loggingUtil) {
		this.db = dbUtil;
		this.logger = loggingUtil;
	}

	@Override
	public void onGuildAuditLogEntryCreate(GuildAuditLogEntryCreateEvent event) {
		AuditLogEntry entry = event.getEntry();
		switch (entry.getType()) {
			case CHANNEL_CREATE -> {
				// check if enabled log
				if (!db.getLogSettings(event.getGuild()).enabled(LogType.CHANNEL)) return;
				
				logger.channel.onChannelCreate(entry);
			}
			case CHANNEL_UPDATE -> {
				// check if enabled log
				if (!db.getLogSettings(event.getGuild()).enabled(LogType.CHANNEL)) return;
				
				logger.channel.onChannelUpdate(entry);
			}
			case CHANNEL_DELETE -> {
				// check if enabled log
				if (!db.getLogSettings(event.getGuild()).enabled(LogType.CHANNEL)) return;
				
				logger.channel.onChannelDelete(entry);
			}
			case CHANNEL_OVERRIDE_CREATE -> {
				// check if enabled log
				if (!db.getLogSettings(event.getGuild()).enabled(LogType.CHANNEL)) return;

				logger.channel.onOverrideCreate(entry);
			}
			case CHANNEL_OVERRIDE_UPDATE -> {
				// check if enabled log
				if (!db.getLogSettings(event.getGuild()).enabled(LogType.CHANNEL)) return;

				logger.channel.onOverrideUpdate(entry);
			}
			case CHANNEL_OVERRIDE_DELETE -> {
				// check if enabled log
				if (!db.getLogSettings(event.getGuild()).enabled(LogType.CHANNEL)) return;

				logger.channel.onOverrideDelete(entry);
			}
			case ROLE_CREATE -> {
				// check if enabled log
				if (!db.getLogSettings(event.getGuild()).enabled(LogType.ROLE)) return;
				
				logger.role.onRoleCreate(entry);
			}
			case ROLE_DELETE -> {
				// check if enabled log
				if (!db.getLogSettings(event.getGuild()).enabled(LogType.ROLE)) return;
				
				logger.role.onRoleDelete(entry);
			}
			case ROLE_UPDATE -> {
				// check if enabled log
				if (!db.getLogSettings(event.getGuild()).enabled(LogType.ROLE)) return;
				
				logger.role.onRoleUpdate(entry);
			}
			case GUILD_UPDATE -> {
				// check if enabled log
				if (!db.getLogSettings(event.getGuild()).enabled(LogType.SERVER)) return;
				
				logger.server.onGuildUpdate(entry);
			}
			case EMOJI_CREATE -> {
				// check if enabled log
				if (!db.getLogSettings(event.getGuild()).enabled(LogType.SERVER)) return;
				
				logger.server.onEmojiCreate(entry);
			}
			case EMOJI_UPDATE -> {
				// check if enabled log
				if (!db.getLogSettings(event.getGuild()).enabled(LogType.SERVER)) return;
				
				logger.server.onEmojiUpdate(entry);
			}
			case EMOJI_DELETE -> {
				// check if enabled log
				if (!db.getLogSettings(event.getGuild()).enabled(LogType.SERVER)) return;
				
				logger.server.onEmojiDelete(entry);
			}
			case STICKER_CREATE -> {
				// check if enabled log
				if (!db.getLogSettings(event.getGuild()).enabled(LogType.SERVER)) return;
				
				logger.server.onStickerCreate(entry);
			}
			case STICKER_UPDATE -> {
				// check if enabled log
				if (!db.getLogSettings(event.getGuild()).enabled(LogType.SERVER)) return;
				
				logger.server.onStickerUpdate(entry);
			}
			case STICKER_DELETE -> {
				// check if enabled log
				if (!db.getLogSettings(event.getGuild()).enabled(LogType.SERVER)) return;
				
				logger.server.onStickerDelete(entry);
			}
			case MEMBER_ROLE_UPDATE -> {
				// check if enabled log
				if (!db.getLogSettings(event.getGuild()).enabled(LogType.MEMBER)) return;

				logger.member.onRoleChange(entry);
			}
			default -> {
				// other
			}	
		}
	}

}
