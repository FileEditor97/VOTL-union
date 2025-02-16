package union.commands.ticketing;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import net.dv8tion.jda.api.requests.ErrorResponse;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import java.util.List;
import java.util.stream.Stream;

public class CloseCmd extends CommandBase {

	public CloseCmd() {
		this.name = "close";
		this.path = "bot.ticketing.close";
		this.options = List.of(
			new OptionData(OptionType.STRING, "reason", lu.getText(path+".reason.help")).setMaxLength(200)
		);
		this.module = CmdModule.TICKETING;
		this.category = CmdCategory.TICKETING;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();

		long channelId = event.getChannel().getIdLong();
		Long authorId = bot.getDBUtil().ticket.getUserId(channelId);

		if (authorId == null) {
			// If this channel is not a ticket
			editError(event, path+".not_ticket");
			return;
		}
		if (bot.getDBUtil().ticket.isClosed(channelId)) {
			// Ticket is closed
			event.getChannel().delete().queue();
			return;
		}

		// Check access
		final boolean isAuthor = authorId.equals(event.getUser().getIdLong());
		if (!isAuthor) {
			switch (bot.getDBUtil().getTicketSettings(event.getGuild()).getAllowClose()) {
				case EVERYONE -> {}
				case HELPER -> {
					// Check if user has Helper+ access
					if (!bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.HELPER)) {
						// No access - reject
						editError(event, "errors.interaction.no_access", "Helper+ access");
						return;
					}
				}
				case SUPPORT -> {
					// Check if user is ticket support or has Admin+ access
					int tagId = bot.getDBUtil().ticket.getTag(channelId);
					if (tagId==0) {
						// Role request ticket
						List<Long> supportRoleIds = bot.getDBUtil().getTicketSettings(event.getGuild()).getRoleSupportIds();
						if (supportRoleIds.isEmpty()) supportRoleIds = bot.getDBUtil().access.getRoles(event.getGuild().getIdLong(), CmdAccessLevel.MOD);
						// Check
						if (denyCloseSupport(supportRoleIds, event.getMember())) {
							editError(event, "errors.interaction.no_access", "'Support' for this ticket or Admin+ access");
							return;
						}
					} else {
						// Standard ticket
						final List<Long> supportRoleIds = Stream.of(bot.getDBUtil().tags.getSupportRolesString(tagId).split(";"))
							.map(Long::parseLong)
							.toList();
						// Check
						if (denyCloseSupport(supportRoleIds, event.getMember())) {
							editError(event, "errors.interaction.no_access", "'Support' for this ticket or Admin+ access");
							return;
						}
					}
				}
			}
		}

		String reason = event.optString(
			"reason",
			isAuthor ? lu.getLocalized(event.getGuildLocale(), "bot.ticketing.listener.closed_author")
				: lu.getLocalized(event.getGuildLocale(), "bot.ticketing.listener.closed_support")
		);

		event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
			.setDescription(lu.getLocalized(event.getGuildLocale(), "bot.ticketing.listener.delete_countdown"))
			.build()
		).queue(msg -> {
			bot.getTicketUtil().closeTicket(channelId, event.getUser(), reason, failure -> {
				if (ErrorResponse.UNKNOWN_MESSAGE.test(failure) || ErrorResponse.UNKNOWN_CHANNEL.test(failure)) return;
				msg.editMessageEmbeds(bot.getEmbedUtil().getError(event, "bot.ticketing.listener.close_failed")).queue();
				bot.getAppLogger().error("Couldn't close ticket with channelID: {}", channelId, failure);
			});
		});
	}

	private boolean denyCloseSupport(List<Long> supportRoleIds, Member member) {
		if (supportRoleIds.isEmpty()) return false; // No data to check against
		final List<Role> roles = member.getRoles(); // Check if user has any support role
		if (!roles.isEmpty() && roles.stream().anyMatch(r -> supportRoleIds.contains(r.getIdLong()))) return false;
		return !bot.getCheckUtil().hasAccess(member, CmdAccessLevel.ADMIN); // if user has Admin access
	}

}
