package union.listeners;

import union.App;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class MessageListener extends ListenerAdapter {

	private final App bot;
	
	public MessageListener(App bot) {
		this.bot = bot;
	}

	@Override
	public void onMessageReceived(MessageReceivedEvent event) {
		User user = event.getAuthor();
		Guild guild = event.getGuild();

		if (user.isBot()) return;
		if (!bot.getDBUtil().verify.isCheckEnabled(guild.getId())) return;

		Role role = guild.getRoleById(bot.getDBUtil().verify.getVerifyRole(guild.getId()));
		if (!event.getMember().getRoles().contains(role)) return;
		
		String userId = user.getId();
		// check if is forced user
		if (bot.getDBUtil().verifyCache.isVerified(userId)) return;
		// check if still has account connected
		String steam64 = bot.getDBUtil().verifyRequest.getSteam64(userId);
		if (steam64 == null) {
			// remove verification role from user
			try {
				guild.removeRoleFromMember(user, role).reason("Autocheck: No account connected").queue(
					success -> {
						user.openPrivateChannel().queue(
							dm -> dm.sendMessage(bot.getLocaleUtil().getLocalized(guild.getLocale(), "bot.verification.role_removed").replace("{server}", guild.getName())).queue(done -> {}, failure -> {})
						);
						bot.getLogListener().onUnverified(user, null, guild, "Autocheck: No account connected");
					}
				);
			} catch (Exception ex) {}
		} else {
			// add user to local database
			bot.getDBUtil().verifyCache.addUser(userId, steam64);
		}
	}
	
}
