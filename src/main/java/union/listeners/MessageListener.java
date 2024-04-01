package union.listeners;

import union.App;
import union.objects.annotation.NotNull;
import union.objects.logs.LogType;

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

	private final App bot;
	
	public MessageListener(App bot) {
		this.bot = bot;
	}

	@Override
	public void onMessageReceived(MessageReceivedEvent event) {
		if (event.getAuthor().isBot()) return;
		
		final Guild guild = event.getGuild();
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


	private final LogType type = LogType.MESSAGE;
	
	@Override
	public void onMessageUpdate(@NotNull MessageUpdateEvent event) {}

	@Override
	public void onMessageDelete(@NotNull MessageDeleteEvent event) {}

	@Override
	public void onMessageBulkDelete(@NotNull MessageBulkDeleteEvent event) {}

	@Override
	public void onMessageReactionRemoveAll(@NotNull MessageReactionRemoveAllEvent event) {}
	
}
