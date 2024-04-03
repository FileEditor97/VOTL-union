package union.listeners;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import union.App;
import union.objects.annotation.NotNull;
import union.objects.constants.Constants;
import union.objects.logs.LogType;
import union.utils.CastUtil;
import union.utils.FixedCache;

import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.audit.AuditLogOption;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Message.Attachment;
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
	FixedCache<Long, MessageData> cache = new FixedCache<>(Constants.DEFAULT_CACHE_SIZE*20); // messageId, message

	private final App bot;
	
	public MessageListener(App bot) {
		this.bot = bot;
	}

	@Override
	public void onMessageReceived(MessageReceivedEvent event) {
		if (event.getAuthor().isBot() || !event.isFromGuild()) return;
		
		// cache message if not exception channel
		final Guild guild = event.getGuild();
		if (bot.getDBUtil().getLogSettings(event.getGuild()).enabled(LogType.MESSAGE)
			&& !bot.getDBUtil().logExceptions.isException(guild.getIdLong(), event.getChannel().getIdLong()))
		{
			cache.put(event.getMessageIdLong(), new MessageData(event.getMessage()));
		}

		// verification check
		if (!bot.getDBUtil().getVerifySettings(guild).isCheckEnabled()) return;

		final long userId = event.getAuthor().getIdLong();
		if (bot.getDBUtil().verifyCache.isVerified(userId)) return;

		final Role role = guild.getRoleById(bot.getDBUtil().getVerifySettings(guild).getRoleId());
		if (!event.getMember().getRoles().contains(role)) return;
		
		// check if still has account connected
		final String steam64Str = bot.getDBUtil().unionVerify.getSteam64(String.valueOf(userId));
		if (steam64Str == null) {
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
			} catch (Exception ex) {}
		} else {
			// add user to local database
			bot.getDBUtil().verifyCache.addUser(userId, Long.valueOf(steam64Str));
		}
	}

	
	@Override
	public void onMessageUpdate(@NotNull MessageUpdateEvent event) {
		if (event.getAuthor().isBot() || !event.isFromGuild()) return;
		if (!bot.getDBUtil().getLogSettings(event.getGuild()).enabled(LogType.MESSAGE)) return;

		final long guildId = event.getGuild().getIdLong();
		if (bot.getDBUtil().logExceptions.isException(guildId, event.getChannel().getIdLong())) return;
		
		final long messageId = event.getMessageIdLong();
		MessageData oldData = cache.get(messageId);
		MessageData newData = new MessageData(event.getMessage());
		cache.put(event.getMessageIdLong(), newData);

		bot.getLogger().message.onMessageUpdate(event.getMember(), event.getGuildChannel(), messageId, oldData, newData);
	}

	@Override
	public void onMessageDelete(@NotNull MessageDeleteEvent event) {
		if (!event.isFromGuild()) return;
		if (!bot.getDBUtil().getLogSettings(event.getGuild()).enabled(LogType.MESSAGE)) return;

		final long messageId = event.getMessageIdLong();

		MessageData data = cache.get(messageId);
		if (data != null) cache.pull(messageId);

		if (bot.getDBUtil().logExceptions.isException(event.getGuild().getIdLong(), event.getChannel().getIdLong())) return;

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
		messageIds.forEach(id -> {
			if (cache.contains(id)) {
				messages.add(cache.get(id));
				cache.pull(id);
			}
		});
		event.getGuild().retrieveAuditLogs()
			.type(ActionType.MESSAGE_BULK_DELETE)
			.limit(1)
			.queue(list -> {
				if (list.isEmpty()) {
					bot.getLogger().message.onMessageBulkDelete(event.getChannel(), "Unknown", messages, null);
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

	public class MessageData {
		private final String content, authorName;
		private final Attachment attachment;
		private final long authorId;

		public MessageData(Message message) {
			this.content = message.getContentRaw();
			if (message.getAttachments().isEmpty())
				this.attachment = null;
			else
				this.attachment = message.getAttachments().get(0);
			this.authorId = message.getAuthor().getIdLong();
			this.authorName = message.getAuthor().getName();
		}

		public String getContent() {
			return content;
		}

		public Attachment getAttachment() {
			return attachment;
		}

		public long getAuthorId() {
			return authorId;
		}

		public String getAuthorName() {
			return authorName;
		}
	}
	
}
