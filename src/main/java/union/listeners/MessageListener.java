package union.listeners;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.jetbrains.annotations.NotNull;
import union.App;
import union.objects.logs.LogType;
import union.objects.logs.MessageData;
import union.utils.CastUtil;

import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.audit.AuditLogOption;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveAllEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.ErrorResponse;

public class MessageListener extends ListenerAdapter {
	// Cache
	private final Cache<Long, MessageData> cache = Caffeine.newBuilder()
		.expireAfterWrite(5, TimeUnit.DAYS)
		.maximumSize(5000)
		.build();

	private final App bot;
	
	public MessageListener(App bot) {
		this.bot = bot;
	}

	@Override
	public void onMessageReceived(MessageReceivedEvent event) {
		if (event.getAuthor().isBot() || !event.isFromGuild()) return; //ignore bots and Private messages

		// cache message if not exception channel
		final Guild guild = event.getGuild();
		final long guildId = guild.getIdLong();
		if (bot.getDBUtil().getLogSettings(guild).enabled(LogType.MESSAGE)) {
			// check channel
			if (!bot.getDBUtil().logExemption.isExemption(guildId, event.getChannel().getIdLong())) {
				// check category
				long categoryId = switch (event.getChannelType()) {
					case TEXT, VOICE, STAGE, NEWS -> event.getGuildChannel().asStandardGuildChannel().getParentCategoryIdLong();
					case GUILD_PUBLIC_THREAD, GUILD_NEWS_THREAD -> event.getChannel().asThreadChannel().getParentChannel()
						.asStandardGuildChannel().getParentCategoryIdLong();
					default -> 0;
				};
				if (categoryId == 0 || !bot.getDBUtil().logExemption.isExemption(guildId, categoryId)) {
					cache.put(event.getMessageIdLong(), new MessageData(event.getMessage()));
				}
			}
		}

		// reward
		bot.getLevelUtil().rewardMessagePlayer(event);

		// verification check
		if (!bot.getDBUtil().getVerifySettings(guild).isCheckEnabled()) return;
		if (bot.getSettings().isDbVerifyDisabled()) return;

		final long userId = event.getAuthor().getIdLong();
		if (bot.getDBUtil().verifyCache.isVerified(userId)) return;

		final Role role = guild.getRoleById(bot.getDBUtil().getVerifySettings(guild).getRoleId());
		if (!event.getMember().getRoles().contains(role)) return;
		
		// check if still has account connected
		final Long steam64 = bot.getDBUtil().unionVerify.getSteam64(String.valueOf(userId));
		if (steam64 == null) {
			// remove verification role from user
			try {
				final User user = event.getAuthor();
				guild.removeRoleFromMember(user, role).reason("Autocheck: No account connected").queue(
					success -> {
						user.openPrivateChannel().queue(dm ->
							dm.sendMessage(bot.getLocaleUtil().getLocalized(guild.getLocale(), "bot.verification.role_removed").replace("{server}", guild.getName()))
								.queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER))
						);
						bot.getLogger().verify.onUnverified(user, null, guild, "Autocheck: No account connected");
					}
				);
			} catch (Exception ignored) {}
		} else {
			// add user to local database
			try {
				bot.getDBUtil().verifyCache.addUser(userId, steam64);
			} catch (SQLException ignored) {}
		}
	}

	
	@Override
	public void onMessageUpdate(@NotNull MessageUpdateEvent event) {
		if (event.getAuthor().isBot() || !event.isFromGuild()) return;
		if (!bot.getDBUtil().getLogSettings(event.getGuild()).enabled(LogType.MESSAGE)) return;

		final long guildId = event.getGuild().getIdLong();
		// check channel
		if (bot.getDBUtil().logExemption.isExemption(guildId, event.getChannel().getIdLong())) return;
		// check category
		long categoryId = switch (event.getChannelType()) {
			case TEXT, VOICE, STAGE, NEWS -> event.getGuildChannel().asStandardGuildChannel().getParentCategoryIdLong();
			case GUILD_PUBLIC_THREAD, GUILD_NEWS_THREAD -> event.getChannel().asThreadChannel().getParentChannel()
				.asStandardGuildChannel().getParentCategoryIdLong();
			default -> 0;
		};
		if (categoryId != 0 && bot.getDBUtil().logExemption.isExemption(guildId, categoryId)) {
			return;
		}
		
		final long messageId = event.getMessageIdLong();
		MessageData oldData = cache.getIfPresent(messageId);
		MessageData newData = new MessageData(event.getMessage());
		cache.put(event.getMessageIdLong(), newData);

		bot.getLogger().message.onMessageUpdate(event.getMember(), event.getGuildChannel(), messageId, oldData, newData);
	}

	@Override
	public void onMessageDelete(@NotNull MessageDeleteEvent event) {
		if (!event.isFromGuild()) return;
		if (!bot.getDBUtil().getLogSettings(event.getGuild()).enabled(LogType.MESSAGE)) return;

		final long messageId = event.getMessageIdLong();

		MessageData data = cache.getIfPresent(messageId);
		if (data != null) cache.invalidate(messageId);

		final long guildId = event.getGuild().getIdLong();
		// check channel
		if (bot.getDBUtil().logExemption.isExemption(guildId, event.getChannel().getIdLong())) return;
		// check category
		long categoryId = switch (event.getChannelType()) {
			case TEXT, VOICE, STAGE, NEWS -> event.getGuildChannel().asStandardGuildChannel().getParentCategoryIdLong();
			case GUILD_PUBLIC_THREAD, GUILD_NEWS_THREAD -> event.getChannel().asThreadChannel().getParentChannel()
				.asStandardGuildChannel().getParentCategoryIdLong();
			default -> 0;
		};
		if (categoryId != 0 && bot.getDBUtil().logExemption.isExemption(guildId, categoryId)) {
			return;
		}

		event.getGuild().retrieveAuditLogs()
			.type(ActionType.MESSAGE_DELETE)
			.limit(1)
			.queue(list -> {
				if (!list.isEmpty() && data != null) {
					AuditLogEntry entry = list.get(0);
					if (entry.getTargetIdLong() == data.getAuthorId() && entry.getTimeCreated().isAfter(OffsetDateTime.now().minusSeconds(15))) {
						bot.getLogger().message.onMessageDelete(event.getGuildChannel(), messageId, data, entry.getUserIdLong());
						return;
					}
				}
				bot.getLogger().message.onMessageDelete(event.getGuildChannel(), messageId, data, null);
			},
			failure -> {
				bot.getAppLogger().warn("Failed to queue audit log for message deletion.", failure);
				bot.getLogger().message.onMessageDelete(event.getGuildChannel(), messageId, data, null);
			});
	}

	@Override
	public void onMessageBulkDelete(@NotNull MessageBulkDeleteEvent event) {
		if (!bot.getDBUtil().getLogSettings(event.getGuild()).enabled(LogType.MESSAGE)) return;

		final List<Long> messageIds = event.getMessageIds().stream().map(CastUtil::castLong).toList();
		if (messageIds.isEmpty()) return;

		List<MessageData> messages = new ArrayList<>();
		cache.getAllPresent(messageIds).forEach((k, v) -> {
			messages.add(v);
			cache.invalidate(k);
		});
		event.getGuild().retrieveAuditLogs()
			.type(ActionType.MESSAGE_BULK_DELETE)
			.limit(1)
			.queue(list -> {
				if (list.isEmpty()) {
					bot.getLogger().message.onMessageBulkDelete(event.getChannel(), String.valueOf(messageIds.size()), messages, null);
				} else {
					AuditLogEntry entry = list.get(0);
					String count = entry.getOption(AuditLogOption.COUNT);
					if (entry.getTimeCreated().isAfter(OffsetDateTime.now().minusSeconds(15)))
						bot.getLogger().message.onMessageBulkDelete(event.getChannel(), count, messages, entry.getUserIdLong());
					else
						bot.getLogger().message.onMessageBulkDelete(event.getChannel(), count, messages, null);
				}
			});
	}

	@Override
	public void onMessageReactionRemoveAll(@NotNull MessageReactionRemoveAllEvent event) {}
	
}
