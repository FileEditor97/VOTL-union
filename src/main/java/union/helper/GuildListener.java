package union.helper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import union.App;
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
import union.utils.database.managers.GuildSettingsManager.AnticrashAction;

public class GuildListener extends ListenerAdapter {

	private final Helper helper;

	private final Set<ActionType> watchedTypes = Set.of(ActionType.BAN, ActionType.CHANNEL_DELETE, ActionType.ROLE_DELETE, ActionType.INTEGRATION_DELETE, ActionType.AUTO_MODERATION_RULE_DELETE);
	@SuppressWarnings("FieldCanBeLocal")
	private final int TRIGGER_AMOUNT = 6;

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
			AnticrashAction action = helper.getDBUtil().guildSettings.getCachedAnticrashAction(guildId);
			if (action == null) {
				// this guild's cache is empty, try caching
				action = helper.getDBUtil().getGuildSettings(guildId).getAnticrashAction();
				// if disabled - check group
				if (!action.isEnabled())
					action = helper.getDBUtil().group.getGuildGroups(guildId)
						.stream()
						.map(group -> helper.getDBUtil().group.getOwner(group))
						.map(owner -> helper.getDBUtil().getGuildSettings(owner).getAnticrashAction())
						.filter(AnticrashAction::isEnabled)
						.findFirst()
						.orElse(AnticrashAction.DISABLED);

				helper.getDBUtil().guildSettings.addAnticrashCache(guildId, action);
			}
			if (!action.isEnabled()) return;

			// add 1 point for action
			helper.getDBUtil().alerts.addPoint(guildId, admin.getIdLong());
			int amount = helper.getDBUtil().alerts.getPoints(guildId, admin.getIdLong());
			if (amount >= TRIGGER_AMOUNT && amount < TRIGGER_AMOUNT+3) {
				// Threshold amount reached - possible harmful behaviour
				AnticrashAction finalAction = action;
				event.getGuild().retrieveMember(admin).queue(member -> {
					try {
						switch (finalAction) {
							case BAN ->
								member.ban(0, TimeUnit.DAYS).reason("Possible misconduct, staff notified!").queue(done -> {
									sendAlert(event.getGuild(), member, "User banned!", event.getEntry().getType());
								}, failure -> {
									helper.getLogger().warn("Unable to ban user", failure);
									sendAlert(event.getGuild(), member, Constants.WARNING+" Failed to ban!\n"+failure.getMessage(), event.getEntry().getType());
								});
							case KICK ->
								member.kick().reason("Possible misconduct, staff notified!").queue(done -> {
									sendAlert(event.getGuild(), member, "User kicked!", event.getEntry().getType());
								}, failure -> {
									helper.getLogger().warn("Unable to kick user", failure);
									sendAlert(event.getGuild(), member, Constants.WARNING+" Failed to kick!\n"+failure.getMessage(), event.getEntry().getType());
								});
							case ROLES ->
								event.getGuild().modifyMemberRoles(member, List.of()).reason("Possible misconduct, staff notified!").queue(done -> {
									sendAlert(event.getGuild(), member, "User's roles cleared!", event.getEntry().getType());
								}, failure -> {
									helper.getLogger().warn("Unable to clear roles", failure);
									sendAlert(event.getGuild(), member, Constants.WARNING+" Failed to clear roles!\n"+failure.getMessage(), event.getEntry().getType());
								});
							default -> {} //ignore
						}
					} catch (Exception ex) {
						// Report exception
						sendAlert(event.getGuild(), member, Constants.WARNING+" Unable to punish user!\n"+ex.getMessage(), event.getEntry().getType());
					}
				});
			}
		}
	}

	private void sendAlert(Guild trigerGuild, Member trigerMember, String text, ActionType actionType) {
		// Get master guilds IDs and send log
		helper.getDBUtil().group.getGuildGroups(trigerGuild.getIdLong()).forEach(groupId ->
			helper.getLogUtil().group.helperAlertTriggered(groupId, trigerGuild, trigerMember, text, actionType.name())
		);
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

		helper.getDBUtil().tempBan.removeGuild(event.getGuild().getIdLong());
	}

	@Override
	public void onUnavailableGuildLeave(UnavailableGuildLeaveEvent event) {
		helper.getDBUtil().group.getGuildGroups(event.getGuildIdLong()).forEach(groupId -> 
			helper.getLogUtil().group.helperInformLeave(groupId, null, event.getGuildId())
		);
	}

	@Override
	public void onGuildMemberJoin(GuildMemberJoinEvent event) {
		long userId = event.getUser().getIdLong();
		if (event.getUser().isBot() && App.getInstance().getSettings().isBotWhitelisted(userId)) return;

		List<Integer> groupIds = helper.getDBUtil().group.getGuildGroups(event.getGuild().getIdLong());
		// Check for blacklist
		for (Integer groupId : groupIds) {
			if (App.getInstance().getDBUtil().blacklist.inGroupUser(groupId, userId)) {
				// Ban
				event.getMember().ban(0, TimeUnit.MINUTES).reason("Blacklisted in group "+groupId).queueAfter(2, TimeUnit.SECONDS);
				// Log
				helper.getLogUtil().group.helperInformBadUser(groupId, event.getGuild(), event.getUser());
				return;
			}
		}

		// Check if verified
		for (Integer groupId : groupIds) {
			int verifyValue = helper.getDBUtil().group.getVerifyValue(groupId);
			if (verifyValue < 0) continue;

			// Check if user is verified, else send pm and kick/ban from this server
			String ownerInvite;
			if (helper.getDBUtil().verifyCache.isVerified(userId)
				|| (ownerInvite = helper.getDBUtil().group.getSelfInvite(groupId)) == null) continue;

			// Send pm if not bot
			if (!event.getUser().isBot()) {
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
			}

			if (verifyValue == 0) {
				event.getMember().kick().reason("NOT VERIFIED! Join main server to verify").queueAfter(3, TimeUnit.SECONDS, done -> {
					// Log to master
					helper.getLogUtil().group.helperInformVerify(groupId, event.getGuild(), event.getUser(), "Inform and kick");
				});
			} else {
				event.getMember().ban(0, TimeUnit.MINUTES).reason("NOT VERIFIED! Join main server to verify").queueAfter(3, TimeUnit.SECONDS, done -> {
					// Add to DB
					helper.getDBUtil().tempBan.add(event.getGuild().getIdLong(), event.getMember().getIdLong(), Instant.now().plus(verifyValue, ChronoUnit.MINUTES));
					// Log to master
					helper.getLogUtil().group.helperInformVerify(groupId, event.getGuild(), event.getUser(), "Inform and ban for %s minutes".formatted(verifyValue));
				});
			}
		}
	}

}
