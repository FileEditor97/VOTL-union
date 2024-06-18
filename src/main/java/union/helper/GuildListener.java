package union.helper;

import java.util.List;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import union.objects.constants.Constants;

import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.events.guild.GuildAuditLogEntryCreateEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.events.guild.UnavailableGuildLeaveEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.ErrorResponse;

public class GuildListener extends ListenerAdapter {

	private final Helper helper;

	private final List<ActionType> watchedTypes = List.of(ActionType.BAN, ActionType.CHANNEL_DELETE, ActionType.ROLE_DELETE, ActionType.INTEGRATION_DELETE);
	private final int triggerAmount = 6;

	public GuildListener(Helper helper) {
		this.helper = helper;
	}
	
	@Override
	public void onGuildAuditLogEntryCreate(GuildAuditLogEntryCreateEvent event) {
		if (event.getEntry().getType() == ActionType.BAN || event.getEntry().getType() == ActionType.UNBAN) {
			UserSnowflake admin = UserSnowflake.fromId(event.getEntry().getUserIdLong());
			// Ignore actions made by both bots
			if (admin.equals(helper.getJDA().getSelfUser()) || admin.equals(helper.getMainJDA().getSelfUser())) return;

			// Get master guilds IDs and send logs to them
			helper.getDBUtil().group.getGuildGroups(event.getGuild().getIdLong()).forEach(groupId -> 
				helper.getLogUtil().group.helperInformAction(groupId, event.getGuild(), event.getEntry())
			);
		}
		// Alerts
		if (watchedTypes.contains(event.getEntry().getType())) {
			UserSnowflake admin = UserSnowflake.fromId(event.getEntry().getUserIdLong());
			// Ignore actions made by both bots
			if (admin.equals(helper.getJDA().getSelfUser()) || admin.equals(helper.getMainJDA().getSelfUser())) return;
			// Check if anticrash enabled in this guild or group's master guild
			long guildId = event.getGuild().getIdLong();
			Boolean enabled = helper.getDBUtil().guildSettings.isAnticrashEnabled(guildId);
			if (enabled == null) {
				enabled = helper.getDBUtil().getGuildSettings(guildId).anticrashEnabled()
					|| helper.getDBUtil().group.getGuildGroups(guildId)
						.stream()
						.map(group -> helper.getDBUtil().group.getOwner(group))
						.map(owner -> helper.getDBUtil().getGuildSettings(owner).anticrashEnabled())
						.filter(on -> on)
						.findFirst()
						.orElse(false);

				helper.getDBUtil().guildSettings.anticrashCache.put(guildId, enabled);
			}
			if (!enabled) return;

			// add 1 point for action
			helper.getDBUtil().alerts.addPoint(guildId, admin.getIdLong());
			int amount = helper.getDBUtil().alerts.getPoints(guildId, admin.getIdLong());
			if (amount >= triggerAmount && amount < triggerAmount+3) {
				// Threshold amount reached - possible harmful behaviour
				event.getGuild().retrieveMember(admin).queue(member -> {
					try {
						member.ban(0, TimeUnit.DAYS).reason("Possible misconduct, staff notified!").queue(done -> {
							// Get master guilds IDs and send logs to them
							helper.getDBUtil().group.getGuildGroups(guildId).forEach(groupId -> 
								helper.getLogUtil().group.helperAlertTriggered(groupId, event.getGuild(), member, "User banned!", event.getEntry().getType().name())
							);
						});
					} catch (Exception ex) {
						// Get master guilds IDs and send logs to them
						helper.getDBUtil().group.getGuildGroups(guildId).forEach(groupId -> 
							helper.getLogUtil().group.helperAlertTriggered(groupId, event.getGuild(), member, Constants.WARNING+" Unable to ban user!\n"+ex.getMessage(), event.getEntry().getType().name())
						);
					}
				});
			}
		}
	}

	@Override
	public void onGuildUnban(GuildUnbanEvent event) {
		// Check if users is in group's blacklist
		helper.getDBUtil().group.getGuildGroups(event.getGuild().getIdLong()).forEach(groupId -> {
			if (helper.getDBUtil().blacklist.inGroupUser(groupId, event.getUser().getIdLong())) {
				event.getGuild().ban(event.getUser(), 0, TimeUnit.SECONDS).reason("~ BLACKLIST! Unban forbidden ~ group ID: "+groupId).queueAfter(10, TimeUnit.SECONDS,
					null,
					failure -> new ErrorHandler().ignore(ErrorResponse.MISSING_PERMISSIONS)
				);
			}
		});
	}

	@Override
	public void onGuildLeave(GuildLeaveEvent event) {
		helper.getDBUtil().group.getGuildGroups(event.getGuild().getIdLong()).forEach(groupId -> 
			helper.getLogUtil().group.helperInformLeave(groupId, event.getGuild(), event.getGuild().getId())
		);
	}

	@Override
	public void onUnavailableGuildLeave(UnavailableGuildLeaveEvent event) {
		helper.getDBUtil().group.getGuildGroups(event.getGuildIdLong()).forEach(groupId -> 
			helper.getLogUtil().group.helperInformLeave(groupId, null, event.getGuildId())
		);
	}

	@Override
	public void onGuildMemberJoin(GuildMemberJoinEvent event) {
		long guildId = event.getGuild().getIdLong();
		helper.getDBUtil().group.getGuildGroups(guildId).forEach(groupId -> {
			if (helper.getDBUtil().group.verifyEnabled(groupId, guildId)) {
				// Check if user is verified, else send pm and kick/ban from this server
				String ownerInvite;
				if (!helper.getDBUtil().verifyCache.isVerified(event.getUser().getIdLong())
					&& (ownerInvite = helper.getDBUtil().group.getSelfInvite(groupId)) != null) {
					// Not verified
					event.getUser().openPrivateChannel().queue(pm -> {
						StringBuilder builder = new StringBuilder(helper.getLocaleUtil()
							.getLocalized(event.getGuild().getLocale(), "misc.verify_instruct")
							.formatted(event.getGuild().getName(), ownerInvite)
						);
						Long ownerId = helper.getDBUtil().group.getOwner(groupId);
						String appealInvite;
						if (ownerId != null && (appealInvite = helper.getDBUtil().getGuildSettings(ownerId).getAppealLink()) != null) {
							builder.append(helper.getLocaleUtil()
								.getLocalized(event.getGuild().getLocale(), "misc.verify_reserve")
								.formatted(appealInvite)
							);
						}

						pm.sendMessage(builder.toString()).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
					});

					event.getMember().kick().reason("NOT VERIFIED! Join main server to verify").queueAfter(3, TimeUnit.SECONDS, done -> {
						// Log to master
						helper.getLogUtil().group.helperInformVerify(groupId, event.getGuild(), event.getUser(), "Inform and kick");
					});
				}
			}
		});
	}

}
