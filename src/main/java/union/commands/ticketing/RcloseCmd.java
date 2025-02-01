package union.commands.ticketing;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

import net.dv8tion.jda.api.entities.*;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import union.utils.message.TimeUtil;

public class RcloseCmd extends CommandBase {
	
	public RcloseCmd() {
		this.name = "rclose";
		this.path = "bot.ticketing.rclose";
		this.options = List.of(
			new OptionData(OptionType.STRING, "reason", lu.getText(path+".reason.help")).setMaxLength(200)
		);
		this.module = CmdModule.TICKETING;
		this.category = CmdCategory.TICKETING;
		this.accessLevel = CmdAccessLevel.HELPER;
	}

	@SuppressWarnings("FieldCanBeLocal")
	private final int CLOSE_AFTER_DELAY = 12; // hours

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();

		long channelId = event.getChannelIdLong();
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
		if (bot.getDBUtil().ticket.getTimeClosing(channelId) > 0) {
			// If request already exists (if there is no cancel button - GG)
			editError(event, path+".already_requested");
			return;
		}

		// Check access
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
		
		Guild guild = event.getGuild();
		UserSnowflake user = User.fromId(bot.getDBUtil().ticket.getUserId(channelId));
		Instant closeTime = Instant.now().plus(CLOSE_AFTER_DELAY, ChronoUnit.HOURS);

		MessageEmbed embed = new EmbedBuilder()
			.setColor(bot.getDBUtil().getGuildSettings(guild).getColor())
			.setDescription(bot.getLocaleUtil().getLocalized(guild.getLocale(), "bot.ticketing.listener.close_request")
				.replace("{user}", user.getAsMention())
				.replace("{time}", TimeUtil.formatTime(closeTime, false)))
			.build();
		Button close = Button.primary("ticket:close", bot.getLocaleUtil().getLocalized(guild.getLocale(), "ticket.close"));
		Button cancel = Button.secondary("ticket:cancel", bot.getLocaleUtil().getLocalized(guild.getLocale(), "ticket.cancel"));
		
		event.getHook().editOriginal("||%s||".formatted(user.getAsMention())).setEmbeds(embed).setActionRow(close, cancel).queue();
		bot.getDBUtil().ticket.setRequestStatus(
			channelId, closeTime.getEpochSecond(),
			event.optString("reason", lu.getLocalized(event.getGuildLocale(), "bot.ticketing.listener.closed_support"))
		);
	}

	private boolean denyCloseSupport(List<Long> supportRoleIds, Member member) {
		if (supportRoleIds.isEmpty()) return false; // No data to check against
		final List<Role> roles = member.getRoles(); // Check if user has any support role
		if (!roles.isEmpty() && roles.stream().anyMatch(r -> supportRoleIds.contains(r.getIdLong()))) return false;
		return !bot.getCheckUtil().hasAccess(member, CmdAccessLevel.ADMIN); // if user has Admin access
	}

}
