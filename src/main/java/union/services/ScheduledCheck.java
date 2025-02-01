package union.services;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import union.App;
import union.helper.Helper;
import union.objects.CaseType;
import union.objects.ReportData;
import union.utils.database.DBUtil;

import static union.utils.CastUtil.castLong;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.internal.utils.tuple.Pair;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import union.utils.database.managers.LevelManager;
import union.utils.encoding.EncodingUtil;
import union.utils.imagegen.renders.ModReportRender;
import union.utils.level.PlayerObject;
import union.utils.message.TimeUtil;


public class ScheduledCheck {

	private final Logger log = (Logger) LoggerFactory.getLogger(ScheduledCheck.class);

	private final App bot;
	private final DBUtil db;

	private final Integer CLOSE_AFTER_DELAY = 12; // hours

	public ScheduledCheck(App bot) {
		this.bot = bot;
		this.db = bot.getDBUtil();
	}

	// each 10-15 minutes
	public void timedChecks() {
		CompletableFuture.runAsync(this::checkTicketStatus)
			.thenRunAsync(this::checkExpiredTempRoles)
			.thenRunAsync(this::checkExpiredStrikes)
			.thenRunAsync(this::generateReport)
			.thenRunAsync(this::checkExpiredPersistentRoles);
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

				if (net.dv8tion.jda.api.utils.TimeUtil.getTimeCreated(channel.getLatestMessageIdLong()).isBefore(OffsetDateTime.now().minusHours(autocloseTime))) {
					Guild guild = channel.getGuild();
					UserSnowflake user = User.fromId(db.ticket.getUserId(channelId));
					Instant closeTime = Instant.now().plus(CLOSE_AFTER_DELAY, ChronoUnit.HOURS);

					MessageEmbed embed = new EmbedBuilder()
						.setColor(db.getGuildSettings(guild).getColor())
						.setDescription(bot.getLocaleUtil().getLocalized(guild.getLocale(), "bot.ticketing.listener.close_auto")
							.replace("{user}", user.getAsMention())
							.replace("{time}", TimeUtil.formatTime(closeTime, false)))
						.build();
					Button close = Button.primary("ticket:close", bot.getLocaleUtil().getLocalized(guild.getLocale(), "ticket.close"));
					Button cancel = Button.secondary("ticket:cancel", bot.getLocaleUtil().getLocalized(guild.getLocale(), "ticket.cancel"));
					
					db.ticket.setRequestStatus(channelId, closeTime.getEpochSecond());
					channel.sendMessage("||%s||".formatted(user.getAsMention())).addEmbeds(embed).addActionRow(close, cancel).queue();
				}
			});

			db.ticket.getCloseMarkedTickets().forEach(channelId -> {
				if (bot.JDA.getGuildChannelById(channelId) == null) {
					bot.getDBUtil().ticket.forceCloseTicket(channelId);
					return;
				}
				bot.getTicketUtil().closeTicket(channelId, null, "time", failure -> {
					db.ticket.setRequestStatus(channelId, -1L);
					if (ErrorResponse.UNKNOWN_MESSAGE.test(failure)) return;
					log.error("Failed to delete ticket channel, either already deleted or unknown error", failure);
				});
			});

			db.ticket.getReplyExpiredTickets().forEach(channelId -> {
				GuildMessageChannel channel = bot.JDA.getChannelById(GuildMessageChannel.class, channelId);
				if (channel == null) {
					bot.getDBUtil().ticket.forceCloseTicket(channelId);
					return;
				}
				channel.getIterableHistory()
					.takeAsync(1)
					.thenAcceptAsync(list -> {
						Message msg = list.get(0);
						if (msg.getAuthor().isBot()) {
							if (bot.JDA.getGuildChannelById(channelId) == null) {
								bot.getDBUtil().ticket.forceCloseTicket(channelId);
								return;
							}
							// Last message is bot - close ticket
							bot.getTicketUtil().closeTicket(channelId, null, "activity", failure -> {
								db.ticket.setWaitTime(channelId, -1L);
								if (ErrorResponse.UNKNOWN_MESSAGE.test(failure)) return;
								log.error("Failed to delete ticket channel, either already deleted or unknown error", failure);
							});
						} else {
							// There is human reply
							db.ticket.setWaitTime(channelId, -1L);
						}
					});
			});
		} catch (Throwable t) {
			log.error("Exception caught during tickets checks.", t);
		}
	}

	private void checkExpiredTempRoles() {
		try {
			List<Map<String, Object>> expired = db.tempRole.expiredRoles(Instant.now());
			if (expired.isEmpty()) return;

			expired.forEach(data -> {
				long roleId = castLong(data.get("roleId"));
				Role role = bot.JDA.getRoleById(roleId);
				if (role == null) {
					db.tempRole.removeRole(roleId);
					return;
				}
				
				long userId = castLong(data.get("userId"));
				if (db.tempRole.shouldDelete(roleId)) {
					try {
						role.delete().reason("Role expired for '"+userId+"'").queue();
					} catch (InsufficientPermissionException | HierarchyException ex) {
						log.warn("Was unable to delete temporary role '{}' during scheduled check.", roleId, ex);
					}
					db.tempRole.removeRole(roleId);
					
				} else {
					role.getGuild().removeRoleFromMember(User.fromId(userId), role).reason("Role expired").queue(null, failure -> {
						if (ErrorResponse.UNKNOWN_MEMBER.test(failure)) return;
						log.warn("Was unable to remove temporary role '{}' from '{}' during scheduled check.", roleId, userId, failure);
					});
					db.tempRole.remove(roleId, userId);
				}
				// Log
				bot.getLogger().role.onTempRoleAutoRemoved(role.getGuild(), castLong(userId), role);
			});
		} catch (Throwable t) {
			log.error("Exception caught during expired roles check.", t);
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
						String caseRowId = caseInfo[0];
						int newCount = Integer.parseInt(caseInfo[1]) - 1;

						StringBuilder newData = new StringBuilder();
						if (newCount > 0) {
							newData.append(caseRowId).append("-").append(newCount);
							if (cases.length > 1)
								newData.append(";");
						} else {
							// Set case inactive
							db.cases.setInactive(Integer.parseInt(caseRowId));
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
			}
		} catch (Throwable t) {
			log.error("Exception caught during expired warns check.", t);
		}
	}

	private void generateReport() {
		try {
			List<Map<String, Object>> expired = db.modReport.getExpired(LocalDateTime.now());
			if (expired.isEmpty()) return;

			expired.forEach(data -> {
				long channelId = castLong(data.get("channelId"));
				TextChannel channel = bot.JDA.getTextChannelById(channelId);
				if (channel == null) {
					long guildId = castLong(data.get("guildId"));
					log.warn("Channel for modReport @ '{}' not found. Deleting.", guildId);
					db.modReport.removeGuild(guildId);
					return;
				}

				Guild guild = channel.getGuild();
				String[] roleIds = ((String) data.get("roleIds")).split(";");
				List<Role> roles = Stream.of(roleIds)
					.map(guild::getRoleById)
					.toList();
				if (roles.isEmpty()) {
					log.warn("Roles for modReport @ '{}' not found. Deleting.", guild.getId());
					db.modReport.removeGuild(guild.getIdLong());
					return;
				}

				int interval = (Integer) data.get("interval");
				LocalDateTime nextReport = LocalDateTime.ofEpochSecond(castLong(data.get("nextReport")), 0, ZoneOffset.UTC);
				nextReport = interval==30 ? nextReport.plusMonths(1) : nextReport.plusDays(interval);
				// Update next report date
				db.modReport.updateNext(channelId, nextReport);

				// Search for members with any of required roles (Mod, Admin, ...)
				guild.findMembers(m -> !Collections.disjoint(m.getRoles(), roles)).setTimeout(10, TimeUnit.SECONDS).onSuccess(members -> {
					if (members.isEmpty() || members.size() > 20) return; // TODO normal reply - too much users
					Instant now = Instant.now();
					Instant previous = (interval==30 ?
						now.minus(Period.ofMonths(1)) :
						now.minus(Period.ofDays(interval))
					).atZone(ZoneId.systemDefault()).toInstant();

					List<ReportData> reportData = new ArrayList<>(members.size());
					members.forEach(m -> {
						int countRoles = bot.getDBUtil().ticket.countTicketsByMod(guild.getIdLong(), m.getIdLong(), previous, now, true);
						Map<Integer, Integer> countCases = bot.getDBUtil().cases.countCasesByMod(guild.getIdLong(), m.getIdLong(), previous, now);
						reportData.add(new ReportData(m, countRoles, countCases));
					});

					ModReportRender render = new ModReportRender(guild.getLocale(), bot.getLocaleUtil(),
						previous, now, reportData);

					final String attachmentName = EncodingUtil.encodeModreport(guild.getIdLong(), now.getEpochSecond());

					try {
						channel.sendFiles(FileUpload.fromData(
							new ByteArrayInputStream(render.renderToBytes()),
							attachmentName
						)).queue();
					} catch (IOException e) {
						log.error("Exception caught during rendering of modReport.", e);
					}
				});
			});
		} catch (Throwable t) {
			log.error("Exception caught during modReport schedule check.", t);
		}
	}

	private void checkExpiredPersistentRoles() {
		try {
			db.persistent.removeExpired();
		} catch (Throwable t) {
			log.error("Exception caught during expired persistent roles check.", t);
		}
	}

	// Each 2-5 minutes
	public void regularChecks() {
		CompletableFuture.runAsync(this::checkUnbans)
			.thenRunAsync(this::checkAccountUpdates)
			.thenRunAsync(this::removeAlertPoints)
			.thenRunAsync(this::updateDbQueue);
	}

	private void checkAccountUpdates() {
		try {
			List<Map<String, String>> data = db.unionVerify.updatedAccounts();
			if (data.isEmpty()) return;

			List<Pair<Long, Long>> removeRoles = new ArrayList<>(); // DiscordId, Steam64

			for (Map<String, String> account : data) {
				String steam64Str = account.get("steam_id");
				db.unionVerify.clearUpdated(steam64Str);
				
				Long steam64 = Long.valueOf(steam64Str);
				Long cacheDiscordId = db.verifyCache.getDiscordId(steam64);
				
				String discordIdStr = account.get("discord_id");
				if (discordIdStr == null) {
					// if not cached - cant track
					if (cacheDiscordId == null) continue;
					// if forced - skip
					if (db.verifyCache.isForced(cacheDiscordId)) {
						db.verifyCache.forceRemoveSteam64(cacheDiscordId);
						continue;
					}

					db.verifyCache.removeByDiscord(cacheDiscordId);
					removeRoles.add(Pair.of(cacheDiscordId, steam64));
				} else {
					Long discordId = Long.valueOf(discordIdStr);
					// if exists in cache
					if (cacheDiscordId != null) {
						// if same - skip
						if (cacheDiscordId.equals(discordId)) continue;
						// duplicate, remove roles from previous discord account
						removeRoles.add(Pair.of(cacheDiscordId, steam64));
						db.verifyCache.removeByDiscord(cacheDiscordId);
					}

					// Add user to cache
					//db.verifyCache.addUser(discordId, steam64);
				}
			}

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
			log.error("Exception caught during verify checking.", t);
		}
	}

	private void checkUnbans() {
		try {
			db.cases.getExpired().forEach(caseData -> {
				if (caseData.getCaseType().equals(CaseType.MUTE)) {
					db.cases.setInactive(caseData.getRowId());
					return;
				}
				Guild guild = bot.JDA.getGuildById(caseData.getGuildId());
				if (guild == null || !guild.getSelfMember().hasPermission(Permission.BAN_MEMBERS)) return;
				guild.unban(User.fromId(caseData.getTargetId())).reason(bot.getLocaleUtil().getLocalized(guild.getLocale(), "misc.ban_expired")).queue(
					s -> bot.getLogger().mod.onAutoUnban(caseData, guild),
					f -> log.warn("Exception at unban attempt. {}", f.getMessage())
				);
				db.cases.setInactive(caseData.getRowId());
			});

			db.tempBan.getExpired().forEach(data -> {
				db.tempBan.remove(data.getLeft(), data.getRight());
				Optional.ofNullable(Helper.getInstance()).ifPresent(h -> h.unban(data.getLeft(), data.getRight(), "Remove temp ban"));
			});
		} catch (Throwable t) {
			log.error("Exception caught during scheduled unban.", t);
		}
	}

	private void removeAlertPoints() {
		try {
			bot.getAlertUtil().decrease();
		} catch (Throwable t) {
			log.error("Exception caught during points removal.", t);
		}
	}


	private void updateDbQueue() {
		try {
			// level data
			Iterator<PlayerObject> itr = bot.getLevelUtil().getUpdateQueue().iterator();
			int updatedCount = 0;
			while (itr.hasNext()) {
				PlayerObject player = itr.next();
				LevelManager.PlayerData playerData = db.levels.getPlayer(player);
				if (playerData == null) continue;

				db.levels.updatePlayer(player, playerData);
				itr.remove();
				updatedCount++;
			}
			if (updatedCount != 0) log.debug("Updated data for {} players", updatedCount);
		} catch (Throwable t) {
			log.error("Exception caught during DB queue update.", t);
		}
	}
}
