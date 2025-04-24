package union.helper;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import union.App;
import union.objects.AnticrashAction;
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
import union.utils.AlertUtil;
import union.utils.database.managers.GuildSettingsManager;

import static union.utils.AlertUtil.DEFAULT_TRIGGER_AMOUNT;
import static union.utils.AlertUtil.WATCHED_ACTIONS;

public class GuildListener extends ListenerAdapter {

	private final Helper helper;

	public GuildListener(Helper helper) {
		this.helper = helper;
	}
	
	@Override
	public void onGuildAuditLogEntryCreate(GuildAuditLogEntryCreateEvent event) {
		AuditLogEntry entry = event.getEntry();

		if (entry.getType() == ActionType.BAN || entry.getType() == ActionType.UNBAN) {
			UserSnowflake admin = UserSnowflake.fromId(entry.getUserIdLong());
			// Ignore actions made by both bots
			if (admin.equals(helper.getJDA().getSelfUser()) || admin.equals(helper.getMainJDA().getSelfUser())) return;

			// Get master guilds IDs and send logs to them
			helper.getDBUtil().group.getGuildGroups(event.getGuild().getIdLong()).forEach(groupId -> 
				helper.getLogUtil().group.helperInformAction(groupId, event.getGuild(), entry)
			);
		}

		// Alerts
		if (WATCHED_ACTIONS.contains(entry.getType())) {
			UserSnowflake admin = UserSnowflake.fromId(entry.getUserIdLong()); // Do not change! .getUser() returns null
			// Ignore actions made by both bots
			if (entry.getUserIdLong()==helper.getJDA().getSelfUser().getIdLong()
				|| entry.getUserIdLong()==helper.getMainJDA().getSelfUser().getIdLong()
				|| entry.getUserId().equals(Constants.DEVELOPER_ID)
			) return;

			// Check if anticrash enabled in this guild or group's master guild
			long guildId = event.getGuild().getIdLong();
			AnticrashAction action = helper.getDBUtil().guildSettings.getCachedAnticrashAction(guildId);
			if (action == null) {
				// Not cached
				// Check this guild settings for enabled anticrash
				GuildSettingsManager.GuildSettings settings = helper.getDBUtil().getGuildSettings(guildId);
				if (settings.isBlank()) {
					// Check group for enabled anticrash
					action = helper.getDBUtil().group.getGuildGroups(guildId)
						.stream()
						.map(id -> helper.getDBUtil().group.getAnticrashAction(id))
						.max(Comparator.comparingInt(a -> a.value))
						.orElse(AnticrashAction.DISABLED);
				} else {
					// Load setting
					action = settings.getAnticrashAction();
				}

				// Cache
				helper.getDBUtil().guildSettings.addAnticrashCache(guildId, action);
			}
			if (action.isDisabled()) return;

			AlertUtil.Data data = App.getInstance().getAlertUtil().addToWatched(guildId, admin.getIdLong());
			if (data != null) {
				int triggerAmount = helper.getDBUtil().getGuildSettings(event.getGuild()).getAnticrashTrigger();
				if (Instant.now().toEpochMilli() - data.startTime() < AlertUtil.FIRST_STAGE_DURATION) {
					// Instant action
					executeAction(event, admin, action, "Possible misconduct [1st stage]", false);
				} else if (data.amount() >= triggerAmount && data.amount() < triggerAmount+2) {
					executeAction(event, admin, action, "Possible misconduct [2nd stage]", false);
				}
			}

			// add 1 point for action
			int points = App.getInstance().getAlertUtil().addPoint(guildId, admin.getIdLong());
			int triggerAmount = helper.getDBUtil().group.getGuildGroups(guildId)
				.stream()
				.map(id -> helper.getDBUtil().group.getAnticrashTrigger(id))
				.min(Integer::compare)
				.orElse(DEFAULT_TRIGGER_AMOUNT);
			if (points >= triggerAmount && points < triggerAmount+2) {
				// Threshold amount reached - possible harmful behaviour
				executeAction(event, admin, action, "Possible misconduct, staff notified!", true);
			}
		}
	}

	private void executeAction(GuildAuditLogEntryCreateEvent event, UserSnowflake admin, AnticrashAction action, String reason, boolean groupAlert) {
		final Guild guild = event.getGuild();
		event.getGuild().retrieveMember(admin).queue(member -> {
			try {
				switch (action) {
					case BAN ->
						member.ban(0, TimeUnit.DAYS).reason(reason).queue(done -> {
							sendAlert(guild, member, "User banned!", event.getEntry().getType(), groupAlert);
						}, failure -> {
							helper.getLogger().warn("Unable to ban user", failure);
							sendAlert(guild, member, Constants.WARNING+" Failed to ban!\n"+failure.getMessage(), event.getEntry().getType(), groupAlert);
						});
					case KICK ->
						member.kick().reason(reason).queue(done -> {
							sendAlert(guild, member, "User kicked!", event.getEntry().getType(), groupAlert);
						}, failure -> {
							helper.getLogger().warn("Unable to kick user", failure);
							sendAlert(guild, member, Constants.WARNING+" Failed to kick!\n"+failure.getMessage(), event.getEntry().getType(), groupAlert);
						});
					case ROLES ->
						guild.modifyMemberRoles(member, List.of()).reason(reason).queue(done -> {
							sendAlert(guild, member, "User's roles cleared!", event.getEntry().getType(), groupAlert);
						}, failure -> {
							helper.getLogger().warn("Unable to clear roles", failure);
							sendAlert(guild, member, Constants.WARNING+" Failed to clear roles!\n"+failure.getMessage(), event.getEntry().getType(), groupAlert);
						});
					default -> {} //ignore
				}
			} catch (Exception ex) {
				// Report exception
				sendAlert(guild, member, Constants.WARNING+" Unable to punish user!\n"+ex.getMessage(), event.getEntry().getType(), groupAlert);
			}
		});
	}

	private void sendAlert(Guild guild, Member trigerMember, String text, ActionType actionType, boolean groupAlert) {
		if (groupAlert) {
			sendGroupAlert(guild, trigerMember, text, actionType);
		} else {
			sendGuildAlert(guild, trigerMember, text, actionType);
		}
	}

	private void sendGroupAlert(Guild trigerGuild, Member trigerMember, String text, ActionType actionType) {
		// Get master guilds IDs and send log
		helper.getDBUtil().group.getGuildGroups(trigerGuild.getIdLong()).forEach(groupId ->
			helper.getLogUtil().group.helperAlertTriggered(groupId, trigerGuild, trigerMember, text, actionType.name())
		);
	}

	private void sendGuildAlert(Guild guild, Member trigerMember, String text, ActionType actionType) {
		helper.getLogUtil().botLog.alertTriggered(guild, trigerMember, text, actionType.name());
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

		try {
			helper.getDBUtil().tempBan.removeGuild(event.getGuild().getIdLong());
		} catch (SQLException ignored) {}
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
					try {
						// Add to DB
						helper.getDBUtil().tempBan.add(event.getGuild().getIdLong(), event.getMember().getIdLong(), Instant.now().plus(verifyValue, ChronoUnit.MINUTES));
						// Log to master
						helper.getLogUtil().group.helperInformVerify(groupId, event.getGuild(), event.getUser(), "Inform and ban for %s minutes".formatted(verifyValue));
					} catch (SQLException ignored) {}
				});
			}
		}
	}

}
