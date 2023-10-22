package union.utils;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import union.App;
import union.objects.CmdModule;
import union.objects.constants.Constants;
import union.utils.message.LocaleUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.Guild.Ban;
import net.dv8tion.jda.api.interactions.DiscordLocale;

public class LogUtil {

	private final App bot;
	//private final EmbedUtil embedUtil;
	private final LocaleUtil lu;

	private final String path = "logger.";

	private final int GREEN_DARK = 0x277236;
	private final int GREEN_LIGHT = 0x67CB7B;
	private final int AMBER_DARK = 0xCA8B02;
	private final int AMBER_LIGHT = 0xFDBE35;
	private final int RED_DARK = 0xB31E22;
	private final int RED_LIGHT = 0xCC6666;
	private final int WHITE = 0xFFFFFF;
	private final int DEFAULT = Constants.COLOR_DEFAULT;

	public LogUtil(App bot) {
		this.bot = bot;
		//this.embedUtil = bot.getEmbedUtil();
		this.lu = bot.getLocaleUtil();
	}

	private String localized(DiscordLocale locale, String pathFooter) {
		return lu.getLocalized(locale, path+pathFooter);
	}

	private EmbedBuilder getEmbed() {
		return getEmbed(DEFAULT);
	}

	private EmbedBuilder getEmbed(int color) {
		return new EmbedBuilder().setColor(color).setTimestamp(ZonedDateTime.now());
	}

	
	// Moderation
	//  Ban
	@Nonnull
	public MessageEmbed banEmbed(DiscordLocale locale, Map<String, Object> banMap) {
		return banEmbed(locale, banMap, null);
	}

	@Nonnull
	public MessageEmbed banEmbed(DiscordLocale locale, Map<String, Object> banMap, String userIcon) {
		return banEmbed(locale, (Integer) banMap.get("banId"), (String) banMap.get("userTag"), (String) banMap.get("userId"),
			(String) banMap.get("modTag"), (String) banMap.get("modId"), Timestamp.valueOf((String) banMap.get("timeStart")),
			Duration.parse((String) banMap.get("duration")), (String) banMap.get("reason"), userIcon);
	}

	@Nonnull
	private MessageEmbed banEmbed(DiscordLocale locale, Integer banId, String userTag, String userId, String modTag, String modId, Timestamp start, Duration duration, String reason, String userIcon) {
		Instant timeStart = start.toInstant();
		Instant timeEnd = timeStart.plus(duration);
		return getEmbed(RED_DARK)
			.setAuthor(localized(locale, "ban.title").replace("{case_id}", banId.toString()).replace("{user_tag}", userTag), null, userIcon)
			.addField(localized(locale, "user"), "<@%s>".formatted(userId), true)
			.addField(localized(locale, "mod"), "<@%s>".formatted(modId), true)
			.addField(localized(locale, "duration"), duration.isZero() ? localized(locale, "permanently") : 
				localized(locale, "temporary")
					.replace("{time}", bot.getTimeUtil().formatTime(timeEnd, false)), true)
			.addField(localized(locale, "ban.reason"), reason, true)
			.setFooter("ID: "+userId)
			.setTimestamp(timeStart)
			.build();
	}

	@Nonnull
	public MessageEmbed syncBanEmbed(DiscordLocale locale, Guild master, User enforcer, User target, String reason) {
		return getEmbed(RED_DARK)
			.setAuthor(localized(locale, "ban.title_synced").replace("{user_tag}", target.getName()), null, target.getAvatarUrl())
			.addField(localized(locale, "user"), target.getAsMention(), true)
			.addField(localized(locale, "ban.reason"), reason, true)
			.addField(localized(locale, "master"), "`%s` (#%s)".formatted(master.getName(), master.getId()), true)
			.addField(localized(locale, "enforcer"), enforcer.getName(), true)
			.setFooter("ID: "+target.getId())
			.build();
	}

	@Nonnull
	public MessageEmbed helperBanEmbed(DiscordLocale locale, Integer groupId, User target, String reason, Integer success, Integer max) {
		return getEmbed(RED_DARK)
			.setAuthor(localized(locale, "ban.title_synced").replace("{user_tag}", target.getName()), null, target.getAvatarUrl())
			.addField(localized(locale, "user"), target.getAsMention(), true)
			.addField(localized(locale, "ban.reason"), reason, true)
			.addField(localized(locale, "success"), "%s/%s".formatted(success, max), true)
			.setFooter("Group ID: "+groupId)
			.build();
	}

	//  Unban
	@Nonnull
	public MessageEmbed unbanEmbed(DiscordLocale locale, Ban banData, Member mod, String reason) {
		return unbanEmbed(locale, banData.getUser().getName(), banData.getUser().getId(), mod.getAsMention(), banData.getReason(), reason);
	}

	@Nonnull
	private MessageEmbed unbanEmbed(DiscordLocale locale, String userTag, String userId, String modMention, String banReason, String reason) {
		return getEmbed(AMBER_DARK)
			.setAuthor(localized(locale, "unban.title").replace("{user_tag}", userTag))
			.addField(localized(locale, "user"), "<@%s>".formatted(userId), true)
			.addField(localized(locale, "mod"), modMention, true)
			.addField(localized(locale, "unban.ban_reason"), Optional.ofNullable(banReason).orElse("-"), true)
			.addField(localized(locale, "unban.reason"), reason, true)
			.setFooter("ID: "+userId)
			.build();
	}

	@Nonnull
	public MessageEmbed syncUnbanEmbed(DiscordLocale locale, Guild master, User enforcer, User target, String banReason, String reason) {
		return getEmbed(AMBER_DARK)
			.setAuthor(localized(locale, "unban.title_synced").replace("{user_tag}", target.getName()), null, target.getAvatarUrl())
			.addField(localized(locale, "user"), target.getAsMention(), true)
			.addField(localized(locale, "unban.ban_reason"), Optional.ofNullable(banReason).orElse("-"), true)
			.addField(localized(locale, "unban.reason"), reason, true)
			.addField(localized(locale, "master"), "`%s` (#%s)".formatted(master.getName(), master.getId()), true)
			.addField(localized(locale, "enforcer"), enforcer.getName(), true)
			.setFooter("ID: "+target.getId())
			.build();
	}

	@Nonnull
	public MessageEmbed helperUnbanEmbed(DiscordLocale locale, Integer groupId, User target, String reason, Integer success, Integer max) {
		return getEmbed(AMBER_DARK)
			.setAuthor(localized(locale, "unban.title_synced").replace("{user_tag}", target.getName()), null, target.getAvatarUrl())
			.addField(localized(locale, "user"), target.getAsMention(), true)
			.addField(localized(locale, "unban.reason"), reason, true)
			.addField(localized(locale, "success"), "%s/%s".formatted(success, max), true)
			.setFooter("Group ID: "+groupId)
			.build();
	}

	@Nonnull
	public MessageEmbed autoUnbanEmbed(DiscordLocale locale, Map<String, Object> banMap) {
		return autoUnbanEmbed(locale, (String) banMap.get("userTag"), (String) banMap.get("userId"), (String) banMap.get("reason"), Duration.parse((String) banMap.get("duration")));
	}

	@Nonnull
	private MessageEmbed autoUnbanEmbed(DiscordLocale locale, String userTag, String userId, String banReason, Duration duration) {
		return getEmbed(AMBER_DARK)
			.setAuthor(localized(locale, "expired.unban.title").replace("{user_tag}", userTag))
			.addField(localized(locale, "user"), "<@%s>".formatted(userId), true)
			.addField(localized(locale, "expired.unban.ban_reason"), Optional.ofNullable(banReason).orElse("-"), true)
			.addField(localized(locale, "duration"), bot.getTimeUtil().durationToString(duration), true)
			.setFooter("ID: "+userId)
			.build();
	}

	//  Kick
	@Nonnull
	public MessageEmbed kickEmbed(DiscordLocale locale, String userTag, String userId, String modTag, String modId, String reason, String userIcon, Boolean formatMod) {
		return getEmbed(RED_DARK)
			.setAuthor(localized(locale, "kick.title").replace("{user_tag}", userTag), null, userIcon)
			.addField(localized(locale, "user"), "<@%s>".formatted(userId), true)
			.addField(localized(locale, "mod"), (formatMod ? "<@%s>".formatted(modId) : modTag), true)
			.addField(localized(locale, "kick.reason"), reason, true)
			.setFooter("ID: "+userId)
			.build();
	}

	@Nonnull
	public MessageEmbed syncKickEmbed(DiscordLocale locale, Guild master, User enforcer, User target, String reason) {
		return getEmbed(RED_DARK)
			.setAuthor(localized(locale, "kick.title_synced").replace("{user_tag}", target.getName()), null, target.getAvatarUrl())
			.addField(localized(locale, "user"), target.getAsMention(), true)
			.addField(localized(locale, "kick.reason"), reason, true)
			.addField(localized(locale, "master"), "`%s` (#%s)".formatted(master.getName(), master.getId()), true)
			.addField(localized(locale, "enforcer"), enforcer.getName(), true)
			.setFooter("ID: "+target.getId())
			.build();
	}

	@Nonnull
	public MessageEmbed helperKickEmbed(DiscordLocale locale, Integer groupId, User target, String reason, Integer success, Integer max) {
		return getEmbed(RED_DARK)
			.setAuthor(localized(locale, "kick.title_synced").replace("{user_tag}", target.getName()), null, target.getAvatarUrl())
			.addField(localized(locale, "user"), target.getAsMention(), true)
			.addField(localized(locale, "kick.reason"), reason, true)
			.addField(localized(locale, "success"), "%s/%s".formatted(success, max), true)
			.setFooter("Group ID: "+groupId)
			.build();
	}

	//  Reason
	@Nonnull
	public MessageEmbed reasonChangedEmbed(DiscordLocale locale, Integer caseId, String userTag, String userId, String modId, String oldReason, String newReason) {
		return getEmbed()
			.setAuthor(localized(locale, "change.reason").replace("{case_id}", caseId.toString()).replace("{user_tag}", userTag))
			.setDescription("🔴 ~~"+oldReason+"~~\n\n🟢 "+newReason)
			.addField(localized(locale, "user"), "<@%s>".formatted(userId), true)
			.addField(localized(locale, "mod"), "<@%s>".formatted(modId), true)
			.setFooter("ID: "+userId)
			.build();
	}

	//  Duration
	@Nonnull
	public MessageEmbed durationChangedEmbed(DiscordLocale locale, Integer caseId, String userTag, String userId, String modId, Instant timeStart, Duration oldDuration, String newTime) {
		String oldTime = oldDuration.isZero() ? localized(locale, "permanently") : localized(locale, "temporary")
			.replace("{time}", bot.getTimeUtil().formatTime(timeStart.plus(oldDuration), false));
		return getEmbed()
			.setAuthor(localized(locale, "change.duration").replace("{case_id}", caseId.toString()).replace("{user_tag}", userTag))
			.setDescription("🔴 ~~"+oldTime+"~~\n\n🟢 "+newTime)
			.addField(localized(locale, "user"), "<@%s>".formatted(userId), true)
			.addField(localized(locale, "mod"), "<@%s>".formatted(modId), true)
			.setFooter("ID: "+userId)
			.build();
	}


	// Roles
	@Nonnull
	public MessageEmbed rolesApprovedEmbed(DiscordLocale locale, String ticketId, String memberMention, String memberId, String mentions, String modMention) {
		return getEmbed(GREEN_DARK)
			.setAuthor(localized(locale, "ticket.roles_title").replace("{ticket}", "role-"+ticketId), null, null)
			.addField(localized(locale, "user"), memberMention, false)
			.addField(localized(locale, "ticket.roles"), mentions, false)
			.addField(localized(locale, "enforcer"), modMention, false)
			.setFooter("ID: "+memberId)
			.build();
	}

	@Nonnull
	public MessageEmbed checkRankEmbed(DiscordLocale locale, String modId, String roleId, String rankName) {
		return getEmbed(AMBER_LIGHT)
			.setAuthor(localized(locale, "roles.checkrank"), null, null)
			.addField(localized(locale, "roles.role"), "<@&%s>".formatted(roleId), true)
			.addField(localized(locale, "roles.rank"), rankName, true)
			.addField(localized(locale, "enforcer"), "<@%s>".formatted(modId), false)
			.setFooter("ID: "+modId)
			.build();
	}

	@Nonnull
	public MessageEmbed roleAddedEmbed(DiscordLocale locale, String modId, String userId, String userUrl, String roleId) {
		return getEmbed(GREEN_LIGHT)
			.setAuthor(localized(locale, "roles.added"), null, userUrl)
			.addField(localized(locale, "user"), "<@%s>".formatted(userId), true)
			.addField(localized(locale, "roles.role"), "<@&%s>".formatted(roleId), true)
			.addField(localized(locale, "mod"), "<@%s>".formatted(modId), false)
			.setFooter("ID: "+userId)
			.build();
	}

	@Nonnull
	public MessageEmbed roleRemovedEmbed(DiscordLocale locale, String modId, String userId, String userUrl, String roleId) {
		return getEmbed(RED_LIGHT)
			.setAuthor(localized(locale, "roles.removed"), null, userUrl)
			.addField(localized(locale, "user"), "<@%s>".formatted(userId), true)
			.addField(localized(locale, "roles.role"), "<@&%s>".formatted(roleId), true)
			.addField(localized(locale, "mod"), "<@%s>".formatted(modId), false)
			.setFooter("ID: "+userId)
			.build();
	}

	@Nonnull
	public MessageEmbed roleRemovedAllEmbed(DiscordLocale locale, String modId, String roleId) {
		return getEmbed(RED_DARK)
			.setAuthor(localized(locale, "roles.removed_all"), null, null)
			.addField(localized(locale, "roles.role"), "<@&%s>".formatted(roleId), true)
			.addField(localized(locale, "enforcer"), "<@%s>".formatted(modId), false)
			.setFooter("ID: "+modId)
			.build();
	}

	@Nonnull
	public MessageEmbed tempRoleAddedEmbed(DiscordLocale locale, User mod, User user, Role role, Duration duration) {
		return getEmbed(GREEN_LIGHT)
			.setAuthor(localized(locale, "roles.temp_added"), null, user.getAvatarUrl())
			.addField(localized(locale, "user"), user.getAsMention(), true)
			.addField(localized(locale, "roles.role"), role.getAsMention(), true)
			.addField(localized(locale, "duration"), bot.getTimeUtil().durationToLocalizedString(locale, duration), true)
			.addField(localized(locale, "mod"), mod.getAsMention(), false)
			.setFooter("ID: "+user.getId())
			.build();
	}

	@Nonnull
	public MessageEmbed tempRoleRemovedEmbed(DiscordLocale locale, User mod, User user, Role role) {
		return getEmbed(RED_LIGHT)
			.setAuthor(localized(locale, "roles.temp_removed"), null, user.getAvatarUrl())
			.addField(localized(locale, "user"), user.getAsMention(), true)
			.addField(localized(locale, "roles.role"), role.getAsMention(), true)
			.addField(localized(locale, "mod"), mod.getAsMention(), false)
			.setFooter("ID: "+user.getId())
			.build();
	}

	@Nonnull
	public MessageEmbed tempRoleAutoRemovedEmbed(DiscordLocale locale, String targetId, Role role) {
		return getEmbed(RED_LIGHT)
			.setAuthor(localized(locale, "roles.temp_removed"), null, null)
			.addField(localized(locale, "user"), "<@%s>".formatted(targetId), true)
			.addField(localized(locale, "roles.role"), role.getAsMention(), true)
			.setFooter("ID: "+targetId)
			.build();
	}


	// Groups
	@Nonnull
	private EmbedBuilder groupLogEmbed(DiscordLocale locale, String ownerId, String ownerIcon, Integer groupId, String name) {
		return getEmbed()
			.setAuthor(localized(locale, "group.title").replace("{group_name}", name).replace("{group_id}", groupId.toString()), null, ownerIcon)
			.setFooter(localized(locale, "group.master")+ownerId);
	}

	@Nonnull
	public MessageEmbed groupCreatedEmbed(DiscordLocale locale, String adminMention, String ownerId, String ownerIcon, Integer groupId, String name) {
		return groupLogEmbed(locale, ownerId, ownerIcon, groupId, name)
			.setColor(Constants.COLOR_SUCCESS)
			.setTitle(localized(locale, "group.created"))
			.addField(localized(locale, "group.admin"), adminMention, false)
			.build();
	}

	@Nonnull
	public MessageEmbed groupMemberDeletedEmbed(DiscordLocale locale, String ownerId, String ownerIcon, Integer groupId, String name) {
		return groupLogEmbed(locale, ownerId, ownerIcon, groupId, name)
			.setColor(Constants.COLOR_FAILURE)
			.setTitle(localized(locale, "group.deleted"))
			.build();
	}

	@Nonnull
	public MessageEmbed groupOwnerDeletedEmbed(DiscordLocale locale, String adminMention, String ownerId, String ownerIcon, Integer groupId, String name) {
		return groupLogEmbed(locale, ownerId, ownerIcon, groupId, name)
			.setColor(Constants.COLOR_FAILURE)
			.setTitle(localized(locale, "group.deleted"))
			.addField(localized(locale, "group.admin"), adminMention, false)
			.build();
	}

	@Nonnull
	public MessageEmbed groupMemberJoinedEmbed(DiscordLocale locale, String adminMention, String ownerId, String ownerIcon, Integer groupId, String name) {
		return groupLogEmbed(locale, ownerId, ownerIcon, groupId, name)
			.setColor(Constants.COLOR_SUCCESS)
			.setTitle(localized(locale, "group.join"))
			.addField(localized(locale, "group.admin"), adminMention, false)
			.build();
	}

	@Nonnull
	public MessageEmbed groupOwnerJoinedEmbed(DiscordLocale locale, String ownerId, String ownerIcon, String targetName, String targetId, Integer groupId, String name) {
		return groupLogEmbed(locale, ownerId, ownerIcon, groupId, name)
			.setColor(Constants.COLOR_SUCCESS)
			.setTitle(localized(locale, "group.joined"))
			.addField(localized(locale, "group.guild"), "*%s* (`%s`)".formatted(targetName, targetId), false)
			.build();
	}

	@Nonnull
	public MessageEmbed groupMemberAddedEmbed(DiscordLocale locale, String ownerId, String ownerIcon, Integer groupId, String name) {
		return groupLogEmbed(locale, ownerId, ownerIcon, groupId, name)
			.setColor(Constants.COLOR_SUCCESS)
			.setTitle(localized(locale, "group.add"))
			.build();
	}

	@Nonnull
	public MessageEmbed groupOwnerAddedEmbed(DiscordLocale locale, String adminMention, String ownerId, String ownerIcon, String targetName, String targetId, Integer groupId, String name) {
		return groupLogEmbed(locale, ownerId, ownerIcon, groupId, name)
			.setColor(Constants.COLOR_SUCCESS)
			.setTitle(localized(locale, "group.added"))
			.addField(localized(locale, "group.guild"), "*%s* (`%s`)".formatted(targetName, targetId), false)
			.addField(localized(locale, "group.admin"), adminMention, false)
			.build();
	}

	@Nonnull
	public MessageEmbed groupMemberLeftEmbed(DiscordLocale locale, String adminMention, String masterId, String masterIcon, Integer groupId, String name) {
		return groupLogEmbed(locale, masterId, masterIcon, groupId, name)
			.setColor(Constants.COLOR_FAILURE)
			.setTitle(localized(locale, "group.leave"))
			.addField(localized(locale, "group.admin"), adminMention, false)
			.build();
	}

	@Nonnull
	public MessageEmbed groupOwnerLeftEmbed(DiscordLocale locale, String masterId, String masterIcon, String targetName, String targetId, Integer groupId, String name) {
		return groupLogEmbed(locale, masterId, masterIcon, groupId, name)
			.setColor(Constants.COLOR_FAILURE)
			.setTitle(localized(locale, "group.left"))
			.addField(localized(locale, "group.guild"), "*%s* (`%s`)".formatted(targetName, targetId), true)
			.build();
	}

	@Nonnull
	public MessageEmbed groupOwnerRemovedEmbed(DiscordLocale locale, String adminMention, String masterId, String masterIcon, String targetName, String targetId, Integer groupId, String name) {
		return groupLogEmbed(locale, masterId, masterIcon, groupId, name)
			.setColor(Constants.COLOR_FAILURE)
			.setTitle(localized(locale, "group.removed"))
			.addField(localized(locale, "group.guild"), "*%s* (`%s`)".formatted(targetName, targetId), true)
			.addField(localized(locale, "group.admin"), adminMention, false)
			.build();
	}

	@Nonnull
	public MessageEmbed groupMemberRenamedEmbed(DiscordLocale locale, String masterId, String masterIcon, Integer groupId, String oldName, String newName) {
		return groupLogEmbed(locale, masterId, masterIcon, groupId, newName)
			.setTitle(localized(locale, "group.renamed"))
			.addField(localized(locale, "group.oldname"), oldName, true)
			.build();
	}

	@Nonnull
	public MessageEmbed groupOwnerRenamedEmbed(DiscordLocale locale, String adminMention, String masterId, String masterIcon, Integer groupId, String oldName, String newName) {
		return groupLogEmbed(locale, masterId, masterIcon, groupId, newName)
			.setTitle(localized(locale, "group.renamed"))
			.addField(localized(locale, "group.oldname"), oldName, true)
			.addField(localized(locale, "group.admin"), adminMention, false)
			.build();
	}

	@Nonnull
	public MessageEmbed auditLogEmbed(DiscordLocale locale, Integer groupId, Guild target, AuditLogEntry auditLogEntry) {
		String title = switch (auditLogEntry.getType()) {
			case BAN -> localized(locale, "audit.banned").formatted(target.getName());
			case UNBAN -> localized(locale, "audit.unbanned").formatted(target.getName());
			default -> localized(locale, "audit.default").formatted(target.getName());
		};
		String admin = UserSnowflake.fromId(auditLogEntry.getUserId()).getAsMention();
		return getEmbed(AMBER_LIGHT)
			.setAuthor(title, null, target.getIconUrl())
			.addField(localized(locale, "audit.admin"), admin, true)
			.addField(localized(locale, "user"), UserSnowflake.fromId(auditLogEntry.getTargetId()).getAsMention(), true)
			.addField(localized(locale, "audit.reason"), Optional.ofNullable(auditLogEntry.getReason()).orElse("-") , false)
			.setFooter(localized(locale, "audit.group_id").formatted(groupId.toString()))
			.build();
	}

	@Nonnull
	public MessageEmbed botLeftEmbed(DiscordLocale locale, Integer groupId, @Nullable Guild guild, String guildId) {
		return getEmbed(AMBER_LIGHT)
			.setAuthor(localized(locale, "audit.leave_guild").formatted(Optional.ofNullable(guild).map(Guild::getName).orElse("unknown")))
			.addField(localized(locale, "audit.guild_id"), guildId, true)
			.setFooter(localized(locale, "audit.group_id").formatted(groupId.toString()))
			.build();
	}


	// Verification
	@Nonnull
	public MessageEmbed verifiedEmbed(DiscordLocale locale, String memberTag, String memberId, String memberIcon, String steamName, String steam64) {
		return getEmbed(GREEN_DARK)
			.setAuthor(localized(locale, "verify.added").replace("{user_tag}", memberTag), null, memberIcon)
			.addField(localized(locale, "verify.steam"), (steam64 == null ? "None" :
				"%s `%s`\n[UnionTeams](https://unionteams.ru/player/%s)\n[Steam](https://steamcommunity.com/profiles/%s)".formatted(steamName, bot.getSteamUtil().convertSteam64toSteamID(steam64), steam64, steam64)
				), true)
			.addField(localized(locale, "verify.discord"), User.fromId(memberId).getAsMention(), true)
			.setFooter("ID: "+memberId)
			.build();
	}

	@Nonnull
	public MessageEmbed unverifiedEmbed(DiscordLocale locale, String memberTag, String memberId, String memberIcon, String steamName, String steam64, String reason) {
		return getEmbed(RED_DARK)
			.setAuthor(localized(locale, "verify.removed").replace("{user_tag}", memberTag), null, memberIcon)
			.addField(localized(locale, "verify.steam"), (steam64 == null ? "None" :
				"%s `%s`\n[UnionTeams](https://unionteams.ru/player/%s)\n[Steam](https://steamcommunity.com/profiles/%s)".formatted(steamName, bot.getSteamUtil().convertSteam64toSteamID(steam64), steam64, steam64)
				), false)
			.addField(localized(locale, "verify.discord"), User.fromId(memberId).getAsMention(), true)
			.addField(localized(locale, "verify.reason"), reason, false)
			.setFooter("ID: "+memberId)
			.build();
	}


	// Tickets
	@Nonnull
	public MessageEmbed ticketCreatedEmbed(DiscordLocale locale, GuildMessageChannel channel, User author) {
		return getEmbed(GREEN_LIGHT)
			.setTitle(localized(locale, "ticket.created"))
			.addField(localized(locale, "user"), author.getAsMention(), false)
			.addField(localized(locale, "ticket.name"), channel.getName(), false)
			.setFooter("Channel ID: "+channel.getId())
			.build();
	}

	@Nonnull
	public MessageEmbed ticketClosedEmbed(DiscordLocale locale, GuildMessageChannel channel, User userClosed, String authorId, String claimerId) {
		return getEmbed(RED_LIGHT)
			.setTitle(localized(locale, "ticket.closed_title"))
			.setDescription(localized(locale, "ticket.closed_value")
				.replace("{name}", channel.getName())
				.replace("{closed}", Optional.ofNullable(userClosed).map(User::getAsMention).orElse(localized(locale, "ticket.autoclosed")))
				.replace("{created}", User.fromId(authorId).getAsMention())
				.replace("{claimed}", Optional.ofNullable(claimerId).map(id -> "<@%s>".formatted(id)).orElse(localized(locale, "ticket.unclaimed")))
			)
			.setFooter("Channel ID: "+channel.getId())
			.build();
	}

	@Nonnull
	public MessageEmbed ticketClosedPmEmbed(DiscordLocale locale, GuildMessageChannel channel, Instant timeClosed, User userClosed, String reasonClosed) {
		return getEmbed(WHITE)
			.setDescription(localized(locale, "ticket.closed_pm")
				.replace("{guild}", channel.getGuild().getName())
				.replace("{closed}", Optional.ofNullable(userClosed).map(User::getAsMention).orElse(localized(locale, "ticket.autoclosed")))
				.replace("{time}", bot.getTimeUtil().formatTime(timeClosed, false))
				.replace("{reason}", reasonClosed)
			)
			.setFooter("Channel ID: "+channel.getId())
			.build();
	}


	// Server
	@Nonnull
	public MessageEmbed accessAdded(DiscordLocale locale, User mod, User userTarget, Role roleTarget, String levelName) {
		return getEmbed(GREEN_DARK)
			.setAuthor(localized(locale, "guild.access_added"), null, Optional.ofNullable(userTarget).map(User::getAvatarUrl).orElse(null))
			.addField(localized(locale, "target"), Optional.ofNullable(userTarget).map(User::getAsMention).orElse(roleTarget.getAsMention()), true)
			.addField(localized(locale, "guild.access_level"), levelName, true)
			.addField(localized(locale, "enforcer"), mod.getAsMention(), false)
			.setFooter("ID: "+Optional.ofNullable(userTarget).map(User::getId).orElse(roleTarget.getId()))
			.build();
	}

	@Nonnull
	public MessageEmbed accessRemoved(DiscordLocale locale, User mod, User userTarget, Role roleTarget, String levelName) {
		return getEmbed(RED_DARK)
			.setAuthor(localized(locale, "guild.access_removed"), null, Optional.ofNullable(userTarget).map(User::getAvatarUrl).orElse(null))
			.addField(localized(locale, "target"), Optional.ofNullable(userTarget).map(User::getAsMention).orElse(roleTarget.getAsMention()), true)
			.addField(localized(locale, "guild.access_level"), levelName, true)
			.addField(localized(locale, "enforcer"), mod.getAsMention(), false)
			.setFooter("ID: "+Optional.ofNullable(userTarget).map(User::getId).orElse(roleTarget.getId()))
			.build();
	}

	@Nonnull
	public MessageEmbed moduleEnabled(DiscordLocale locale, User mod, CmdModule module) {
		return getEmbed(GREEN_DARK)
			.setAuthor(localized(locale, "guild.module_enabled"), null, null)
			.addField(localized(locale, "guild.module"), lu.getLocalized(locale, module.getPath()), true)
			.addField(localized(locale, "enforcer"), mod.getAsMention(), false)
			.setFooter("ID: "+mod.getId())
			.build();
	}

	@Nonnull
	public MessageEmbed moduleDisabled(DiscordLocale locale, User mod, CmdModule module) {
		return getEmbed(RED_DARK)
			.setAuthor(localized(locale, "guild.module_disabled"), null, null)
			.addField(localized(locale, "guild.module"), lu.getLocalized(locale, module.getPath()), true)
			.addField(localized(locale, "enforcer"), mod.getAsMention(), false)
			.setFooter("ID: "+mod.getId())
			.build();
	}
	

}