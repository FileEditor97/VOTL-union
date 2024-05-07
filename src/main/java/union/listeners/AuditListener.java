package union.listeners;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.audit.AuditLogKey;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import union.App;
import union.objects.constants.Constants;
import union.objects.logs.LogType;
import union.utils.database.DBUtil;
import union.utils.file.lang.LocaleUtil;
import union.utils.logs.LoggingUtil;

import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.events.guild.GuildAuditLogEntryCreateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class AuditListener extends ListenerAdapter {

	private final LocaleUtil lu;
	private final DBUtil db;
	private final LoggingUtil logger;
 
	public AuditListener(App bot) {
		this.lu = bot.getLocaleUtil();
		this.db = bot.getDBUtil();
		this.logger = bot.getLogger();
	}

	@Override
	public void onGuildAuditLogEntryCreate(GuildAuditLogEntryCreateEvent event) {
		AuditLogEntry entry = event.getEntry();
		switch (entry.getType()) {
			case CHANNEL_CREATE -> {
				// for thread, check if parent channel is managed
				if (event.getEntry().getChangeByKey(AuditLogKey.CHANNEL_TYPE).getNewValue().equals(ChannelType.GUILD_PUBLIC_THREAD)) {
					long threadId = event.getEntry().getTargetIdLong();
					ThreadChannel thread = event.getGuild().getThreadChannelById(threadId);
					if (thread != null && db.threadControl.exist(thread.getParentChannel().getIdLong())) {
						// create controls
						createThreadPanel(thread);
					}
				}
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
				// remove from db exceptions
				db.logExceptions.removeException(event.getGuild().getIdLong(), entry.getTargetIdLong());
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
				// Ignore role changes by bot, as bot already logs with role connected changes (except verify and strike)
				if (entry.getUserIdLong() == event.getJDA().getSelfUser().getIdLong()) return;

				logger.member.onRoleChange(entry);
			}
			default -> {
				// other
			}	
		}
	}

	private void createThreadPanel(ThreadChannel channel) {
		DiscordLocale locale = channel.getGuild().getLocale();
		MessageEmbed embed = new EmbedBuilder().setColor(0x595959)
				.setTitle(lu.getLocalized(locale, "threads.panel.title"))
				.build();
		ActionRow actionRow = ActionRow.of(
				Button.danger("thread:delete", lu.getLocalized(locale, "threads.panel.delete")),
				Button.danger("thread:lock", lu.getLocalized(locale, "threads.panel.lock"))
		);

		channel.sendMessageEmbeds(embed).setComponents(actionRow).queue();
	}

}
