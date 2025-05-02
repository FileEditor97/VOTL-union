package union.listeners;

import com.jayway.jsonpath.JsonPath;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audit.AuditLogChange;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.audit.AuditLogKey;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.guild.GuildAuditLogEntryCreateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import union.App;
import union.objects.AnticrashAction;
import union.objects.constants.Constants;
import union.objects.logs.LogType;
import union.utils.CastUtil;
import union.utils.database.DBUtil;
import union.utils.file.lang.LocaleUtil;
import union.utils.logs.LoggingUtil;

import java.sql.SQLException;
import java.util.*;

public class AuditListener extends ListenerAdapter {

	private final LocaleUtil lu;
	private final DBUtil db;
	private final LoggingUtil logger;

	private final Set<Permission> dangerPermissions = Set.of(
		Permission.ADMINISTRATOR, Permission.MANAGE_SERVER, Permission.MANAGE_ROLES, Permission.MANAGE_CHANNEL,
		Permission.KICK_MEMBERS, Permission.BAN_MEMBERS, Permission.MODERATE_MEMBERS,
		Permission.MESSAGE_MANAGE, Permission.MANAGE_THREADS, Permission.MANAGE_WEBHOOKS
	);
 
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
				try {
					db.logExemption.removeExemption(event.getGuild().getIdLong(), entry.getTargetIdLong());
				} catch (SQLException ignored) {}
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
				
				logger.server.onRoleCreate(entry);
			}
			case ROLE_DELETE -> {
				// check if enabled log
				if (!db.getLogSettings(event.getGuild()).enabled(LogType.ROLE)) return;
				
				logger.server.onRoleDelete(entry);
			}
			case ROLE_UPDATE -> {
				// check if added bad perm
				AuditLogChange change = entry.getChangeByKey(AuditLogKey.ROLE_PERMISSIONS);
				if (change != null) {
					long guildId = event.getGuild().getIdLong();
					// anti-crash
					// Check if added role is watched - then add to cache or update time
					AnticrashAction action = db.guildSettings.getCachedAnticrashAction(guildId);
					if (action == null) {
						action = db.getGuildSettings(event.getGuild()).getAnticrashAction();
						db.guildSettings.addAnticrashCache(guildId, action);
					}
					if (action.isEnabled() && !entry.getUserId().equals(Constants.DEVELOPER_ID)) {
						try {
							var oldPerms = Optional.ofNullable((String) change.getOldValue())
								.map(v -> Permission.getPermissions(Long.parseLong(v)))
								.orElse(EnumSet.noneOf(Permission.class));
							var newPerms = Optional.ofNullable((String) change.getNewValue())
								.map(v -> Permission.getPermissions(Long.parseLong(v)))
								.orElse(EnumSet.noneOf(Permission.class));

							newPerms.removeAll(oldPerms);

							if (newPerms.stream().anyMatch(dangerPermissions::contains)) {
								App.getInstance().getAlertUtil().watch(guildId, entry.getUserIdLong());
							}
						} catch (Exception ignored) {}
					}
				}

				// check if enabled log
				if (!db.getLogSettings(event.getGuild()).enabled(LogType.ROLE)) return;
				
				logger.server.onRoleUpdate(entry);
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
				long guildId = event.getGuild().getIdLong();
				// anti-crash
				// Check if added role is watched - then add to cache or update time
				AnticrashAction action = db.guildSettings.getCachedAnticrashAction(guildId);
				if (action == null) {
					action = db.getGuildSettings(event.getGuild()).getAnticrashAction();
					db.guildSettings.addAnticrashCache(guildId, action);
				}
				if (action.isEnabled() && !entry.getUserId().equals(Constants.DEVELOPER_ID)) {
					AuditLogChange change = entry.getChangeByKey(AuditLogKey.MEMBER_ROLES_ADD);
					if (change != null) {
						if (change.getNewValue() instanceof List<?> values) {
							boolean isWatched = values.stream()
								.map(v -> {
									try {
										return CastUtil.castLong(JsonPath.read(v, "$.id"));
									} catch (Exception ignored) {
										return null;
									}
								})
								.filter(Objects::nonNull)
								.map(roleId -> event.getGuild().getRoleById(roleId))
								.filter(Objects::nonNull)
								.anyMatch(role -> role.getPermissions().stream().anyMatch(dangerPermissions::contains));
							if (isWatched) {
								App.getInstance().getAlertUtil().watch(guildId, entry.getTargetIdLong());
							}
						}
					}
				}

				// check if enabled log
				if (!db.getLogSettings(event.getGuild()).enabled(LogType.MEMBER)) return;
				// Ignore role changes by bot, as bot already logs with role connected changes (except verify and strike)
				if (entry.getUserIdLong() == event.getJDA().getSelfUser().getIdLong()) return;

				logger.member.onRoleChange(entry);
			}
			case THREAD_CREATE -> {
				// check if parent channel is managed
				if (event.getEntry().getChangeByKey(AuditLogKey.CHANNEL_TYPE).getNewValue().equals(ChannelType.GUILD_PUBLIC_THREAD.getId())) {
					long threadId = event.getEntry().getTargetIdLong();
					ThreadChannel thread = event.getGuild().getThreadChannelById(threadId);
					if (thread != null && db.threadControl.exist(thread.getParentChannel().getIdLong())) {
						// create controls
						createThreadPanel(thread);
					}
				}
			}
			default -> {
				// ignored
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
