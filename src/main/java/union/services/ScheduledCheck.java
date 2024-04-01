package union.services;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import union.App;
import union.objects.CaseType;
import union.utils.database.DBUtil;
import union.utils.database.managers.CaseManager.CaseData;
import static union.utils.CastUtil.castLong;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.dv8tion.jda.api.utils.TimeUtil;
import net.dv8tion.jda.internal.utils.tuple.Pair;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;


public class ScheduledCheck {

	private final Logger logger = (Logger) LoggerFactory.getLogger(ScheduledCheck.class);

	private final App bot;
	private final DBUtil db;

	private final Integer CLOSE_AFTER_DELAY = 12; // hours

	public ScheduledCheck(App bot) {
		this.bot = bot;
		this.db = bot.getDBUtil();
	}

	// each 10-15 minutes
	public void timedChecks() {
		CompletableFuture.runAsync(() -> {
			checkTicketStatus();
		}).thenRunAsync(() -> {
			checkExpiredTempRoles();
		}).thenRunAsync(() -> {
			checkExpiredStrikes();
		});
	}

	private void checkTicketStatus() {
		try {
			db.ticket.getOpenedChannels().forEach(channelId -> {
				GuildMessageChannel channel = bot.JDA.getChannelById(GuildMessageChannel.class, channelId);
				if (channel == null) {
					// Should be closed???
					bot.getDBUtil().ticket.forceCloseTicket(channelId);
					return;
				}
				int autocloseTime = db.getTicketSettings(channel.getGuild()).getAutocloseTime();
				if (autocloseTime == 0) return;

				if (TimeUtil.getTimeCreated(channel.getLatestMessageIdLong()).isBefore(OffsetDateTime.now().minusHours(autocloseTime))) {
					Guild guild = channel.getGuild();
					UserSnowflake user = User.fromId(db.ticket.getUserId(channelId));
					Instant closeTime = Instant.now().plus(CLOSE_AFTER_DELAY, ChronoUnit.HOURS);

					MessageEmbed embed = new EmbedBuilder()
						.setColor(db.getGuildSettings(guild).getColor())
						.setDescription(bot.getLocaleUtil().getLocalized(guild.getLocale(), "bot.ticketing.listener.close_auto")
							.replace("{user}", user.getAsMention())
							.replace("{time}", TimeFormat.RELATIVE.atInstant(closeTime).toString()))
						.build();
					Button close = Button.primary("ticket:close", bot.getLocaleUtil().getLocalized(guild.getLocale(), "ticket.close"));
					Button cancel = Button.secondary("ticket:cancel", bot.getLocaleUtil().getLocalized(guild.getLocale(), "ticket.cancel"));
					
					db.ticket.setRequestStatus(channelId, closeTime.getEpochSecond());
					channel.sendMessage("||%s||".formatted(user.getAsMention())).addEmbeds(embed).addActionRow(close, cancel).queue();
				}
			});

			db.ticket.getExpiredTickets().forEach(channelId -> {
				GuildChannel channel = bot.JDA.getGuildChannelById(channelId);
				if (channel == null) {
					bot.getDBUtil().ticket.forceCloseTicket(channelId);
					return;
				}
				bot.getTicketUtil().closeTicket(channelId, null, "Autoclosure", failure -> {
					logger.error("Failed to delete ticket channel, either already deleted or unknown error", failure);
					db.ticket.setRequestStatus(channelId, -1L);
				});
			});
		} catch (Throwable t) {
			logger.error("Exception caught during tickets checks.", t);
		}
	}

	private void checkExpiredTempRoles() {
		try {
			List<Map<String, Object>> expired = db.tempRole.expiredRoles(Instant.now());
			if (expired.isEmpty()) return;

			expired.forEach(data -> {
				String roleId = (String) data.get("roleId");
				Role role = bot.JDA.getRoleById(roleId);
				if (role == null) {
					db.tempRole.removeRole(roleId);
					return;
				};
				
				if (db.tempRole.shouldDelete(roleId)) {
					try {
						role.delete().reason("Role expired").queue();
					} catch (InsufficientPermissionException | HierarchyException ex) {
						logger.warn("Was unable to delete temporary role '%s' during scheduled check.".formatted(roleId), ex);
					}
					db.tempRole.removeRole(roleId);
				} else {
					String userId = (String) data.get("userId");
					role.getGuild().removeRoleFromMember(User.fromId(userId), role).reason("Role expired").queue(null, failure -> {
						logger.warn("Was unable to remove temporary role '%s' from '%s' during scheduled check.".formatted(roleId, userId), failure);
					});
					db.tempRole.remove(roleId, userId);
					// Log
					bot.getLogger().role.onTempRoleAutoRemoved(role.getGuild(), castLong(userId), role);
				}
			});
		} catch (Throwable t) {
			logger.error("Exception caught during expired roles check.", t);
		}
	}

	private void checkExpiredStrikes() {
		try {
			List<Map<String, Object>> expired = db.strike.getExpired(Instant.now());
			if (expired.isEmpty()) return;

			for (Map<String, Object> data : expired) {
				Long guildId = castLong(data.get("guildId"));
				Long userId = castLong(data.get("userId"));
				Integer strikes = (Integer) data.get("count");

				if (strikes <= 0) {
					// Should not happen...
					db.strike.removeGuildUser(guildId, userId);
				} else if (strikes == 1) {
					// One strike left, remove user
					db.strike.removeGuildUser(guildId, userId);
					// set case inactive
					db.cases.setInactiveStrikeCases(userId, guildId);
				} else {
					String[] cases = ((String) data.getOrDefault("data", "")).split(";");
					// Update data
					if (!cases[0].isEmpty()) {
						String[] caseInfo = cases[0].split("-");
						String caseId = caseInfo[0];
						Integer newCount = Integer.valueOf(caseInfo[1]) - 1;

						StringBuffer newData = new StringBuffer();
						if (newCount > 0) {
							newData.append(caseId+"-"+newCount);
							if (cases.length > 1)
								newData.append(";");
						} else {
							// Set case inactive
							db.cases.setInactive(Integer.parseInt(caseId));
						}
						if (cases.length > 1) {
							List<String> list = new ArrayList<>(List.of(cases));
							list.remove(0);
							newData.append(String.join(";", list));
						}
						// Remove one strike and reset time
						db.strike.removeStrike(guildId, userId,
							Instant.now().plus(bot.getDBUtil().getGuildSettings(guildId).getStrikeExpires(), ChronoUnit.DAYS),
							1, newData.toString()
						);
					} else {
						db.strike.removeGuildUser(guildId, userId);
						throw new Exception("Strike data is empty. Deleted data for gid '%s' and uid '%s'".formatted(guildId, userId));
					}
				}
			};
		} catch (Throwable t) {
			logger.error("Exception caught during expired warns check.", t);
		}
	}

	// Each 2-5 minutes
	public void regularChecks() {
		CompletableFuture.runAsync(() -> {
			checkAccountUpdates();
		}).thenRunAsync(() -> {
			checkUnbans();
		}).thenRunAsync(() -> {
			removeAlertPoints();
		});
	}

	private void checkAccountUpdates() {
		try {
			List<Map<String, String>> data = db.unionVerify.updatedAccounts();
			if (data.isEmpty()) return;

			List<Pair<Long, Long>> removeRoles = new ArrayList<Pair<Long, Long>>(); // DiscordId, Steam64

			for (Map<String, String> account : data) {
				String steam64Str = account.get("steam_id");
				db.unionVerify.clearUpdated(steam64Str);
				
				Long steam64 = Long.valueOf(steam64Str);
				Long cacheDiscordId = db.verifyCache.getDiscordId(steam64);
				
				String discordIdStr = account.get("discord_id");
				if (discordIdStr == null) {
					// if not cached - cant track
					if (cacheDiscordId == null) return;
					// if forced - skip
					if (db.verifyCache.isForced(cacheDiscordId)) {
						db.verifyCache.forceRemoveSteam64(cacheDiscordId);
						return;
					};

					db.verifyCache.removeByDiscord(cacheDiscordId);
					removeRoles.add(Pair.of(cacheDiscordId, steam64));
				} else {
					Long discordId = Long.valueOf(discordIdStr);
					// if exists in cache
					if (cacheDiscordId != null) {
						// if same - skip
						if (cacheDiscordId.equals(discordId)) return;
						// duplicate, remove roles from previous discord account
						removeRoles.add(Pair.of(cacheDiscordId, steam64));
						db.verifyCache.removeByDiscord(cacheDiscordId);
					}

					// Add user to cache
					//db.verifyCache.addUser(discordId, steam64);
				}
			};

			if (removeRoles.isEmpty()) return;
			bot.JDA.getGuilds().forEach(guild -> {
				Long roleId = db.getVerifySettings(guild).getRoleId();
				if (roleId == null) return;
				Role role = guild.getRoleById(roleId);
				if (role == null) return;

				removeRoles.forEach(account -> {
					guild.retrieveMemberById(account.getLeft()).queue(member -> {
						if (!member.getRoles().contains(role)) return;
						guild.removeRoleFromMember(member, role).reason("Autocheck: Account unlinked").queue(success -> {
							bot.getLogger().verify.onUnverified(member.getUser(), account.getRight(), guild, "Autocheck: Account unlinked");
						});
					}, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MEMBER));
				});
			});
		} catch (Throwable t) {
			logger.error("Exception caught during verify checking.", t);
		}
	}

	private void checkUnbans() {
		List<CaseData> expired = db.cases.getExpired();
		if (expired.isEmpty()) return;
		
		expired.forEach(caseData -> {
			if (caseData.getCaseType().equals(CaseType.MUTE)) {
				db.cases.setInactive(caseData.getCaseIdInt());
				return;
			}
			Guild guild = bot.JDA.getGuildById(caseData.getGuildId());
			if (guild == null || !guild.getSelfMember().hasPermission(Permission.BAN_MEMBERS)) return;
			guild.unban(User.fromId(caseData.getTargetId())).reason(bot.getLocaleUtil().getLocalized(guild.getLocale(), "misc.ban_expired")).queue(
				s -> bot.getLogger().mod.onAutoUnban(caseData, guild),
				f -> logger.warn("Exception at unban attempt", f.getMessage())
			);
			db.cases.setInactive(caseData.getCaseIdInt());
		});
	}

	private void removeAlertPoints() {
		try {
			db.alerts.removePoint();
		} catch (Throwable t) {
			logger.error("Exception caught during points removal.", t);
		}
	}
	
}
