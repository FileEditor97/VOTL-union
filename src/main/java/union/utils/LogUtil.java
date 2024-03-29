package union.utils;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Optional;

import union.objects.CmdModule;
import union.objects.annotation.NotNull;
import union.objects.annotation.Nullable;
import union.objects.constants.Constants;
import union.utils.database.managers.CaseManager.CaseData;
import union.utils.message.LocaleUtil;
import union.utils.message.SteamUtil;
import union.utils.message.TimeUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.interactions.DiscordLocale;

public class LogUtil {

	private final LocaleUtil lu;

	private final String pathHeader = "logger_embed.";

	private final int GREEN_DARK = 0x277236;
	private final int GREEN_LIGHT = 0x67CB7B;
	private final int AMBER_DARK = 0xCA8B02;
	private final int AMBER_LIGHT = 0xFDBE35;
	private final int RED_DARK = 0xB31E22;
	private final int RED_LIGHT = 0xCC6666;
	private final int WHITE = 0xFFFFFF;
	private final int DEFAULT = Constants.COLOR_DEFAULT;

	public LogUtil(LocaleUtil localeUtil) {
		this.lu = localeUtil;
	}

	private String localized(DiscordLocale locale, String pathFooter) {
		return lu.getLocalized(locale, pathHeader+pathFooter);
	}

	private EmbedBuilder getEmbed() {
		return getEmbed(DEFAULT);
	}

	private EmbedBuilder getEmbed(int color) {
		return new EmbedBuilder().setColor(color).setTimestamp(ZonedDateTime.now());
	}

	
	// Moderation
	@NotNull
	private EmbedBuilder moderationEmbedBuilder(DiscordLocale locale, CaseData caseData) {
		return new EmbedBuilder()
			.setAuthor(localized(locale, "case").formatted(caseData.getCaseId(), lu.getLocalized(locale, caseData.getCaseType().getPath()), caseData.getTargetTag()))
			.addField(localized(locale, "user"), "<@"+caseData.getTargetId()+">", true)
			.addField(localized(locale, "mod"), caseData.getModId()>0 ? "<@"+caseData.getModId()+">" : "-", true)
			.setFooter("ID: "+caseData.getTargetId())
			.setTimestamp(caseData.getTimeStart());
	}

	@NotNull
	private EmbedBuilder moderationEmbedBuilder(DiscordLocale locale, CaseData caseData, String userIcon) {
		return new EmbedBuilder()
			.setAuthor(localized(locale, "case").formatted(caseData.getCaseId(), lu.getLocalized(locale, caseData.getCaseType().getPath()), caseData.getTargetTag()),
				null, userIcon)
			.addField(localized(locale, "user"), "<@"+caseData.getTargetId()+">", true)
			.addField(localized(locale, "mod"), caseData.getModId()>0 ? "<@"+caseData.getModId()+">" : "-", true)
			.setFooter("ID: "+caseData.getTargetId())
			.setTimestamp(caseData.getTimeStart());
	}

	@NotNull
	public MessageEmbed caseEmbed(DiscordLocale locale, CaseData caseData) {
		EmbedBuilder builder = moderationEmbedBuilder(locale, caseData)
			.setColor(DEFAULT)
			.setAuthor(localized(locale, "case").formatted(caseData.getCaseId(), lu.getLocalized(locale, caseData.getCaseType().getPath()), caseData.getTargetTag()))
			.addField(localized(locale, "reason"), caseData.getReason(), true);
		if (caseData.isActive() && !caseData.getDuration().isNegative())
			builder.addField(localized(locale, "duration"), caseData.getDuration().isZero() ? localized(locale, "permanently") : 
				localized(locale, "temporary").formatted(TimeUtil.formatTime(caseData.getTimeEnd(), false)), true);
		return builder.build();
	}

	//  Ban
	@NotNull
	public MessageEmbed banEmbed(DiscordLocale locale, CaseData caseData, String userIcon) {
		return moderationEmbedBuilder(locale, caseData, userIcon)
			.setColor(RED_DARK)
			.addField(localized(locale, "duration"), caseData.getDuration().isZero() ? localized(locale, "permanently") : 
				localized(locale, "temporary").formatted(TimeUtil.formatTime(caseData.getTimeEnd(), false)), true)
			.addField(localized(locale, "reason"), caseData.getReason(), true)
			.build();
	}

	@NotNull
	public MessageEmbed helperBanEmbed(DiscordLocale locale, int groupId, User target, String reason, int success, int max) {
		return getEmbed(RED_DARK)
			.setAuthor(localized(locale, "ban.title_synced").formatted(target.getName()), null, target.getAvatarUrl())
			.addField(localized(locale, "user"), target.getAsMention(), true)
			.addField(localized(locale, "reason"), reason, true)
			.addField(localized(locale, "success"), success+"/"+max, true)
			.setFooter("Group ID: "+groupId)
			.build();
	}

	//  Unban
	@NotNull
	public MessageEmbed unbanEmbed(DiscordLocale locale, CaseData caseData, String banReason) {
		return moderationEmbedBuilder(locale, caseData)
			.setColor(AMBER_DARK)
			.addField(localized(locale, "unban.ban_reason"), Optional.ofNullable(banReason).orElse("-"), true)
			.addField(localized(locale, "reason"), Optional.ofNullable(caseData.getReason()).orElse("-"), true)
			.build();
	}

	@NotNull
	public MessageEmbed helperUnbanEmbed(DiscordLocale locale, int groupId, User target, String reason, int success, int max) {
		return getEmbed(AMBER_DARK)
			.setAuthor(localized(locale, "unban.title_synced").formatted(target.getName()), null, target.getAvatarUrl())
			.addField(localized(locale, "user"), target.getAsMention(), true)
			.addField(localized(locale, "reason"), reason, true)
			.addField(localized(locale, "success"), success+"/"+max, true)
			.setFooter("Group ID: "+groupId)
			.build();
	}

	@NotNull
	public MessageEmbed autoUnbanEmbed(DiscordLocale locale, CaseData caseData) {
		return getEmbed(AMBER_DARK)
			.setAuthor(localized(locale, "unban.title_expired").formatted(caseData.getTargetTag()))
			.addField(localized(locale, "user"), "<@"+caseData.getTargetId()+">", true)
			.addField(localized(locale, "unban.ban_reason"), Optional.ofNullable(caseData.getReason()).orElse("-"), true)
			.addField(localized(locale, "duration"), TimeUtil.durationToString(caseData.getDuration()), true)
			.setFooter("ID: "+caseData.getTargetId())
			.build();
	}

	//  Kick
	@NotNull
	public MessageEmbed kickEmbed(DiscordLocale locale, CaseData caseData, String userIcon) {
		return moderationEmbedBuilder(locale, caseData, userIcon)
			.setColor(RED_DARK)
			.addField(localized(locale, "reason"), Optional.ofNullable(caseData.getReason()).orElse("-"), true)
			.build();
	}

	@NotNull
	public MessageEmbed helperKickEmbed(DiscordLocale locale, Integer groupId, User target, String reason, int success, int max) {
		return getEmbed(RED_DARK)
			.setAuthor(localized(locale, "kick.title_synced").formatted(target.getName()), null, target.getAvatarUrl())
			.addField(localized(locale, "user"), target.getAsMention(), true)
			.addField(localized(locale, "reason"), reason, true)
			.addField(localized(locale, "success"), success+"/"+max, true)
			.setFooter("Group ID: "+groupId)
			.build();
	}

	//  Mute
	@NotNull
	public MessageEmbed muteEmbed(DiscordLocale locale, CaseData caseData, String userIcon) {
		return moderationEmbedBuilder(locale, caseData, userIcon)
			.setColor(RED_DARK)
			.addField(localized(locale, "duration"), caseData.getDuration().isZero() ? localized(locale, "permanently") : 
				localized(locale, "temporary").formatted(TimeUtil.formatTime(caseData.getTimeEnd(), false)), true)
			.addField(localized(locale, "reason"), caseData.getReason(), true)
			.build();
	}

	@NotNull
	public MessageEmbed unmuteEmbed(DiscordLocale locale, CaseData caseData, String userIcon, String muteReason) {
		return moderationEmbedBuilder(locale, caseData, userIcon)
			.setColor(AMBER_DARK)
			.addField(localized(locale, "unmute.mute_reason"), Optional.ofNullable(muteReason).orElse("-"), true)
			.addField(localized(locale, "reason"), Optional.ofNullable(caseData.getReason()).orElse("-"), true)
			.build();
	}

	//  Strike
	public MessageEmbed strikeEmbed(DiscordLocale locale, CaseData caseData, String userIcon) {
		return moderationEmbedBuilder(locale, caseData, userIcon)
			.setColor(AMBER_LIGHT)
			.addField(localized(locale, "reason"), Optional.ofNullable(caseData.getReason()).orElse("-"), true)
			.build();
	}

	public MessageEmbed strikesClearedEmbed(DiscordLocale locale, String userTag, long userId, long modId) {
		return getEmbed(AMBER_LIGHT)
			.setAuthor(localized(locale, "strike.cleared").formatted(userTag))
			.addField(localized(locale, "user"), "<@"+userId+">", true)
			.addField(localized(locale, "mod"), "<@"+modId+">", true)
			.setFooter("ID: "+userId)
			.build();
	}

	public MessageEmbed strikeDeletedEmbed(DiscordLocale locale, String userTag, long userId, long modId, int caseId, int deletedAmount, int maxAmount) {
		return getEmbed(AMBER_LIGHT)
			.setAuthor(localized(locale, "strike.deleted").formatted(userTag))
			.addField(localized(locale, "strike.case"), String.valueOf(caseId), true)
			.addField(localized(locale, "strike.amount"), "/"+userId+">", true)
			.addField(localized(locale, "user"), "<@"+userId+">", true)
			.addField(localized(locale, "mod"), "<@"+modId+">", true)
			.setFooter("ID: "+userId)
			.build();
	}

	//  Reason
	@NotNull
	public MessageEmbed reasonChangedEmbed(DiscordLocale locale, CaseData caseData, long modId, String newReason) {
		return getEmbed()
			.setAuthor(localized(locale, "change.reason").formatted(caseData.getCaseId(), caseData.getTargetTag()))
			.setDescription("> %s\n\n🔴 ~~%s~~\n🟢 %s".formatted(lu.getLocalized(locale, caseData.getCaseType().getPath()), Optional.ofNullable(caseData.getReason()).orElse("None"), newReason))
			.addField(localized(locale, "user"), "<@"+caseData.getTargetId()+">", true)
			.addField(localized(locale, "mod"), "<@"+modId+">", true)
			.setFooter("ID: "+caseData.getTargetId())
			.build();
	}

	//  Duration
	@NotNull
	public MessageEmbed durationChangedEmbed(DiscordLocale locale, CaseData caseData, long modId, String newTime) {
		String oldTime = caseData.getDuration().isZero() ? localized(locale, "permanently") : localized(locale, "temporary")
			.formatted(TimeUtil.formatTime(caseData.getTimeEnd(), false));
		return getEmbed()
			.setAuthor(localized(locale, "change.duration").formatted(caseData.getCaseId(), caseData.getTargetTag()))
			.setDescription("> %s\n\n🔴 ~~%s~~\n🟢 %s".formatted(lu.getLocalized(locale, caseData.getCaseType().getPath()), oldTime, newTime))
			.addField(localized(locale, "user"), "<@"+caseData.getTargetId()+">", true)
			.addField(localized(locale, "mod"), "<@"+modId+">", true)
			.setFooter("ID: "+caseData.getTargetId())
			.build();
	}

	//  Blacklist
	@NotNull
	public MessageEmbed blacklistAddedEmbed(DiscordLocale locale, User enforcer, User target, String steamID, String groupInfo) {
		return getEmbed(RED_DARK)
			.setAuthor(localized(locale, "blacklist.added").formatted(target.getName()), null, target.getAvatarUrl())
			.addField(localized(locale, "user"), target.getAsMention(), true)
			.addField(localized(locale, "blacklist.steam"), steamID, true)
			.addField(localized(locale, "blacklist.group"), groupInfo, true)
			.addField(localized(locale, "enforcer"), enforcer.getName(), true)
			.setFooter("ID: "+target.getId())
			.build();
	}

	@NotNull
	public MessageEmbed blacklistRemovedEmbed(DiscordLocale locale, User enforcer, User target, String steamID, String groupInfo) {
		return getEmbed(GREEN_DARK)
			.setAuthor(localized(locale, "blacklist.removed").formatted(target==null ? steamID : target.getName()), null, target.getAvatarUrl())
			.addField(localized(locale, "user"), target==null ? "none" : target.getAsMention(), true)
			.addField(localized(locale, "blacklist.steam"), steamID, true)
			.addField(localized(locale, "blacklist.group"), groupInfo, true)
			.addField(localized(locale, "enforcer"), enforcer.getName(), true)
			.build();
	}

	// Roles
	@NotNull
	public MessageEmbed rolesApprovedEmbed(DiscordLocale locale, String ticketId, String memberMention, String memberId, String mentions, String modMention) {
		return getEmbed(GREEN_DARK)
			.setAuthor(localized(locale, "ticket.roles_title").replace("{ticket}", "role-"+ticketId), null, null)
			.addField(localized(locale, "user"), memberMention, false)
			.addField(localized(locale, "ticket.roles"), mentions, false)
			.addField(localized(locale, "enforcer"), modMention, false)
			.setFooter("ID: "+memberId)
			.build();
	}

	@NotNull
	public MessageEmbed checkRankEmbed(DiscordLocale locale, String modId, String roleId, String rankName) {
		return getEmbed(AMBER_LIGHT)
			.setAuthor(localized(locale, "roles.checkrank"), null, null)
			.addField(localized(locale, "roles.role"), "<@&"+roleId+">", true)
			.addField(localized(locale, "roles.rank"), rankName, true)
			.addField(localized(locale, "enforcer"), "<@"+modId+">", false)
			.setFooter("ID: "+modId)
			.build();
	}

	@NotNull
	public MessageEmbed roleAddedEmbed(DiscordLocale locale, String modId, String userId, String userUrl, String roleId) {
		return getEmbed(GREEN_LIGHT)
			.setAuthor(localized(locale, "roles.added"), null, userUrl)
			.addField(localized(locale, "user"), "<@%s>".formatted(userId), true)
			.addField(localized(locale, "roles.role"), "<@&%s>".formatted(roleId), true)
			.addField(localized(locale, "mod"), "<@%s>".formatted(modId), false)
			.setFooter("ID: "+userId)
			.build();
	}

	@NotNull
	public MessageEmbed roleRemovedEmbed(DiscordLocale locale, String modId, String userId, String userUrl, String roleId) {
		return getEmbed(RED_LIGHT)
			.setAuthor(localized(locale, "roles.removed"), null, userUrl)
			.addField(localized(locale, "user"), "<@%s>".formatted(userId), true)
			.addField(localized(locale, "roles.role"), "<@&%s>".formatted(roleId), true)
			.addField(localized(locale, "mod"), "<@%s>".formatted(modId), false)
			.setFooter("ID: "+userId)
			.build();
	}

	@NotNull
	public MessageEmbed roleRemovedAllEmbed(DiscordLocale locale, String modId, String roleId) {
		return getEmbed(RED_DARK)
			.setAuthor(localized(locale, "roles.removed_all"), null, null)
			.addField(localized(locale, "roles.role"), "<@&%s>".formatted(roleId), true)
			.addField(localized(locale, "enforcer"), "<@%s>".formatted(modId), false)
			.setFooter("ID: "+modId)
			.build();
	}

	@NotNull
	public MessageEmbed tempRoleAddedEmbed(DiscordLocale locale, User mod, User user, Role role, Duration duration) {
		return getEmbed(GREEN_LIGHT)
			.setAuthor(localized(locale, "roles.temp_added"), null, user.getAvatarUrl())
			.addField(localized(locale, "user"), user.getAsMention(), true)
			.addField(localized(locale, "roles.role"), role.getAsMention(), true)
			.addField(localized(locale, "duration"), TimeUtil.durationToLocalizedString(lu, locale, duration), true)
			.addField(localized(locale, "mod"), mod.getAsMention(), false)
			.setFooter("ID: "+user.getId())
			.build();
	}

	@NotNull
	public MessageEmbed tempRoleRemovedEmbed(DiscordLocale locale, User mod, User user, Role role) {
		return getEmbed(RED_LIGHT)
			.setAuthor(localized(locale, "roles.temp_removed"), null, user.getAvatarUrl())
			.addField(localized(locale, "user"), user.getAsMention(), true)
			.addField(localized(locale, "roles.role"), role.getAsMention(), true)
			.addField(localized(locale, "mod"), mod.getAsMention(), false)
			.setFooter("ID: "+user.getId())
			.build();
	}

	public MessageEmbed tempRoleUpdatedEmbed(DiscordLocale locale, User mod, User user, Role role, Instant until) {
		return getEmbed(GREEN_LIGHT)
			.setAuthor(localized(locale, "roles.temp_updated"), null, user.getAvatarUrl())
			.addField(localized(locale, "user"), user.getAsMention(), true)
			.addField(localized(locale, "roles.role"), role.getAsMention(), true)
			.addField(localized(locale, "duration"), TimeUtil.formatTime(until, false), true)
			.addField(localized(locale, "mod"), mod.getAsMention(), false)
			.setFooter("ID: "+user.getId())
			.build();
	}

	@NotNull
	public MessageEmbed tempRoleAutoRemovedEmbed(DiscordLocale locale, String targetId, Role role) {
		return getEmbed(RED_LIGHT)
			.setAuthor(localized(locale, "roles.temp_removed"), null, null)
			.addField(localized(locale, "user"), "<@%s>".formatted(targetId), true)
			.addField(localized(locale, "roles.role"), role.getAsMention(), true)
			.setFooter("ID: "+targetId)
			.build();
	}

	@NotNull
	public MessageEmbed checkRoleChildGuild(DiscordLocale locale, String modId, String roleId, String guildName, String guildId) {
		return getEmbed(AMBER_LIGHT)
			.setAuthor(localized(locale, "roles.checkrank"), null, null)
			.addField(localized(locale, "roles.role"), "<@&%s>".formatted(roleId), true)
			.addField(localized(locale, "roles.guild"), "`%s` (%s)".formatted(guildName, guildId), true)
			.addField(localized(locale, "enforcer"), "<@%s>".formatted(modId), false)
			.setFooter("ID: "+modId)
			.build();
	}


	// Groups
	@NotNull
	private EmbedBuilder groupLogEmbed(DiscordLocale locale, Long ownerId, String ownerIcon, Integer groupId, String name) {
		return getEmbed()
			.setAuthor(localized(locale, "group.title").replace("{group_name}", name).replace("{group_id}", groupId.toString()), null, ownerIcon)
			.setFooter(localized(locale, "group.master")+ownerId);
	}

	@NotNull
	public MessageEmbed groupCreatedEmbed(DiscordLocale locale, String adminMention, Long ownerId, String ownerIcon, Integer groupId, String name) {
		return groupLogEmbed(locale, ownerId, ownerIcon, groupId, name)
			.setColor(Constants.COLOR_SUCCESS)
			.setTitle(localized(locale, "group.created"))
			.addField(localized(locale, "group.admin"), adminMention, false)
			.build();
	}

	@NotNull
	public MessageEmbed groupMemberDeletedEmbed(DiscordLocale locale, Long ownerId, String ownerIcon, Integer groupId, String name) {
		return groupLogEmbed(locale, ownerId, ownerIcon, groupId, name)
			.setColor(Constants.COLOR_FAILURE)
			.setTitle(localized(locale, "group.deleted"))
			.build();
	}

	@NotNull
	public MessageEmbed groupOwnerDeletedEmbed(DiscordLocale locale, String adminMention, Long ownerId, String ownerIcon, Integer groupId, String name) {
		return groupLogEmbed(locale, ownerId, ownerIcon, groupId, name)
			.setColor(Constants.COLOR_FAILURE)
			.setTitle(localized(locale, "group.deleted"))
			.addField(localized(locale, "group.admin"), adminMention, false)
			.build();
	}

	@NotNull
	public MessageEmbed groupMemberJoinedEmbed(DiscordLocale locale, String adminMention, Long ownerId, String ownerIcon, Integer groupId, String name) {
		return groupLogEmbed(locale, ownerId, ownerIcon, groupId, name)
			.setColor(Constants.COLOR_SUCCESS)
			.setTitle(localized(locale, "group.join"))
			.addField(localized(locale, "group.admin"), adminMention, false)
			.build();
	}

	@NotNull
	public MessageEmbed groupOwnerJoinedEmbed(DiscordLocale locale, Long ownerId, String ownerIcon, String targetName, Long targetId, Integer groupId, String name) {
		return groupLogEmbed(locale, ownerId, ownerIcon, groupId, name)
			.setColor(Constants.COLOR_SUCCESS)
			.setTitle(localized(locale, "group.joined"))
			.addField(localized(locale, "group.guild"), "*%s* (`%s`)".formatted(targetName, targetId), false)
			.build();
	}

	@NotNull
	public MessageEmbed groupMemberAddedEmbed(DiscordLocale locale, Long ownerId, String ownerIcon, Integer groupId, String name) {
		return groupLogEmbed(locale, ownerId, ownerIcon, groupId, name)
			.setColor(Constants.COLOR_SUCCESS)
			.setTitle(localized(locale, "group.add"))
			.build();
	}

	@NotNull
	public MessageEmbed groupOwnerAddedEmbed(DiscordLocale locale, String adminMention, Long ownerId, String ownerIcon, String targetName, Long targetId, Integer groupId, String name) {
		return groupLogEmbed(locale, ownerId, ownerIcon, groupId, name)
			.setColor(Constants.COLOR_SUCCESS)
			.setTitle(localized(locale, "group.added"))
			.addField(localized(locale, "group.guild"), "*%s* (`%s`)".formatted(targetName, targetId), false)
			.addField(localized(locale, "group.admin"), adminMention, false)
			.build();
	}

	@NotNull
	public MessageEmbed groupMemberLeftEmbed(DiscordLocale locale, String adminMention, Long ownerId, String ownerIcon, Integer groupId, String name) {
		return groupLogEmbed(locale, ownerId, ownerIcon, groupId, name)
			.setColor(Constants.COLOR_FAILURE)
			.setTitle(localized(locale, "group.leave"))
			.addField(localized(locale, "group.admin"), adminMention, false)
			.build();
	}

	@NotNull
	public MessageEmbed groupOwnerLeftEmbed(DiscordLocale locale, Long ownerId, String ownerIcon, String targetName, Long targetId, Integer groupId, String name) {
		return groupLogEmbed(locale, ownerId, ownerIcon, groupId, name)
			.setColor(Constants.COLOR_FAILURE)
			.setTitle(localized(locale, "group.left"))
			.addField(localized(locale, "group.guild"), "*%s* (`%s`)".formatted(targetName, targetId), true)
			.build();
	}

	@NotNull
	public MessageEmbed groupOwnerRemovedEmbed(DiscordLocale locale, String adminMention, Long ownerId, String ownerIcon, String targetName, Long targetId, Integer groupId, String name) {
		return groupLogEmbed(locale, ownerId, ownerIcon, groupId, name)
			.setColor(Constants.COLOR_FAILURE)
			.setTitle(localized(locale, "group.removed"))
			.addField(localized(locale, "group.guild"), "*%s* (`%s`)".formatted(targetName, targetId), true)
			.addField(localized(locale, "group.admin"), adminMention, false)
			.build();
	}

	@NotNull
	public MessageEmbed groupMemberRenamedEmbed(DiscordLocale locale, Long ownerId, String ownerIcon, Integer groupId, String oldName, String newName) {
		return groupLogEmbed(locale, ownerId, ownerIcon, groupId, newName)
			.setTitle(localized(locale, "group.renamed"))
			.addField(localized(locale, "group.oldname"), oldName, true)
			.build();
	}

	@NotNull
	public MessageEmbed groupOwnerRenamedEmbed(DiscordLocale locale, String adminMention, Long ownerId, String ownerIcon, Integer groupId, String oldName, String newName) {
		return groupLogEmbed(locale, ownerId, ownerIcon, groupId, newName)
			.setTitle(localized(locale, "group.renamed"))
			.addField(localized(locale, "group.oldname"), oldName, true)
			.addField(localized(locale, "group.admin"), adminMention, false)
			.build();
	}

	// OTHER

	@NotNull
	public MessageEmbed auditLogEmbed(DiscordLocale locale, int groupId, Guild target, AuditLogEntry auditLogEntry) {
		String title = switch (auditLogEntry.getType()) {
			case BAN -> localized(locale, "audit.banned").formatted(target.getName());
			case UNBAN -> localized(locale, "audit.unbanned").formatted(target.getName());
			default -> localized(locale, "audit.default").formatted(target.getName());
		};
		String admin = UserSnowflake.fromId(auditLogEntry.getUserIdLong()).getAsMention();
		return getEmbed(AMBER_LIGHT)
			.setAuthor(title, null, target.getIconUrl())
			.addField(localized(locale, "audit.admin"), admin, true)
			.addField(localized(locale, "user"), UserSnowflake.fromId(auditLogEntry.getTargetId()).getAsMention(), true)
			.addField(localized(locale, "audit.reason"), Optional.ofNullable(auditLogEntry.getReason()).orElse("-") , false)
			.setFooter(localized(locale, "audit.group_id").formatted(groupId))
			.build();
	}

	@NotNull
	public MessageEmbed alertEmbed(DiscordLocale locale, int groupId, Guild guild, Member member, String actionTaken, String reason) {
		return getEmbed(AMBER_LIGHT)
			.setAuthor(localized(locale, "audit.alert").formatted(guild.getName()), null, guild.getIconUrl())
			.addField(localized(locale, "target"), "%s (%d)".formatted(member.getAsMention(), member.getIdLong()), true)
			.addField(localized(locale, "audit.reason"), reason, true)
			.addField(localized(locale, "audit.action"), actionTaken, false)
			.setFooter(localized(locale, "audit.group_id").formatted(groupId))
			.build();
	}

	@NotNull
	public MessageEmbed botLeftEmbed(DiscordLocale locale, int groupId, @Nullable Guild guild, String guildId) {
		return getEmbed(AMBER_LIGHT)
			.setAuthor(localized(locale, "audit.leave_guild").formatted(Optional.ofNullable(guild).map(Guild::getName).orElse("unknown")))
			.addField(localized(locale, "audit.guild_id"), guildId, true)
			.setFooter(localized(locale, "audit.group_id").formatted(groupId))
			.build();
	}


	// Verification
	@NotNull
	public MessageEmbed verifiedEmbed(DiscordLocale locale, String memberTag, String memberId, String memberIcon, String steamName, Long steam64) {
		return getEmbed(GREEN_DARK)
			.setAuthor(localized(locale, "verify.added").formatted(memberTag), null, memberIcon)
			.addField(localized(locale, "verify.steam"), (steam64 == null ? "None" :
				"%s `%s`\n[UnionTeams](https://unionteams.ru/player/%s)\n[Steam](https://steamcommunity.com/profiles/%<s)".formatted(steamName, SteamUtil.convertSteam64toSteamID(steam64), steam64)
				), true)
			.addField(localized(locale, "verify.discord"), User.fromId(memberId).getAsMention(), true)
			.setFooter("ID: "+memberId)
			.build();
	}

	@NotNull
	public MessageEmbed unverifiedEmbed(DiscordLocale locale, String memberTag, String memberId, String memberIcon, String steamName, Long steam64, String reason) {
		return getEmbed(RED_DARK)
			.setAuthor(localized(locale, "verify.removed").formatted(memberTag), null, memberIcon)
			.addField(localized(locale, "verify.steam"), (steam64 == null ? "None" :
				"%s `%s`\n[UnionTeams](https://unionteams.ru/player/%s)\n[Steam](https://steamcommunity.com/profiles/%<s)".formatted(steamName, SteamUtil.convertSteam64toSteamID(steam64), steam64)
				), false)
			.addField(localized(locale, "verify.discord"), User.fromId(memberId).getAsMention(), true)
			.addField(localized(locale, "verify.reason"), reason, false)
			.setFooter("ID: "+memberId)
			.build();
	}

	@NotNull
	public MessageEmbed verifyAttempt(DiscordLocale locale, String memberTag, String memberId, String memberIcon, Long steam64, String reason) {
		return getEmbed(AMBER_DARK)
			.setAuthor(localized(locale, "verify.added").formatted(memberTag), null, memberIcon)
			.setDescription(localized(locale, "verify.reason")+":\n>"+reason)
			.addField(localized(locale, "verify.steam"), (steam64 == null ? "None" :
				"`%s`\n[UnionTeams](https://unionteams.ru/player/%s)\n[Steam](https://steamcommunity.com/profiles/%<s)".formatted(SteamUtil.convertSteam64toSteamID(steam64), steam64)
				), true)
			.addField(localized(locale, "verify.discord"), User.fromId(memberId).getAsMention(), true)
			.setFooter("ID: "+memberId)
			.build();
	}

	// Tickets
	@NotNull
	public MessageEmbed ticketCreatedEmbed(DiscordLocale locale, GuildMessageChannel channel, User author) {
		return getEmbed(GREEN_LIGHT)
			.setTitle(localized(locale, "ticket.created"))
			.addField(localized(locale, "user"), author.getAsMention(), false)
			.addField(localized(locale, "ticket.name"), channel.getName(), false)
			.setFooter("Channel ID: "+channel.getId())
			.build();
	}

	@NotNull
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

	@NotNull
	public MessageEmbed ticketClosedPmEmbed(DiscordLocale locale, GuildMessageChannel channel, Instant timeClosed, User userClosed, String reasonClosed) {
		return getEmbed(WHITE)
			.setDescription(localized(locale, "ticket.closed_pm")
				.replace("{guild}", channel.getGuild().getName())
				.replace("{closed}", Optional.ofNullable(userClosed).map(User::getAsMention).orElse(localized(locale, "ticket.autoclosed")))
				.replace("{time}", TimeUtil.formatTime(timeClosed, false))
				.replace("{reason}", reasonClosed)
			)
			.setFooter("Channel ID: "+channel.getId())
			.build();
	}


	// Server
	@NotNull
	public MessageEmbed accessAdded(DiscordLocale locale, User mod, User userTarget, Role roleTarget, String levelName) {
		String targetMention = userTarget!=null ? userTarget.getAsMention() : roleTarget.getAsMention();
		String targetId = userTarget!=null ? userTarget.getId() : roleTarget.getId();
		return getEmbed(GREEN_DARK)
			.setAuthor(localized(locale, "guild.access_added"), null, Optional.ofNullable(userTarget).map(User::getAvatarUrl).orElse(null))
			.addField(localized(locale, "target"), targetMention, true)
			.addField(localized(locale, "guild.access_level"), levelName, true)
			.addField(localized(locale, "enforcer"), mod.getAsMention(), false)
			.setFooter("ID: "+targetId)
			.build();
	}

	@NotNull
	public MessageEmbed accessRemoved(DiscordLocale locale, User mod, User userTarget, Role roleTarget, String levelName) {
		String targetMention = userTarget!=null ? userTarget.getAsMention() : roleTarget.getAsMention();
		String targetId = userTarget!=null ? userTarget.getId() : roleTarget.getId();
		return getEmbed(RED_DARK)
			.setAuthor(localized(locale, "guild.access_removed"), null, Optional.ofNullable(userTarget).map(User::getAvatarUrl).orElse(null))
			.addField(localized(locale, "target"), targetMention, true)
			.addField(localized(locale, "guild.access_level"), levelName, true)
			.addField(localized(locale, "enforcer"), mod.getAsMention(), false)
			.setFooter("ID: "+targetId)
			.build();
	}

	@NotNull
	public MessageEmbed moduleEnabled(DiscordLocale locale, User mod, CmdModule module) {
		return getEmbed(GREEN_DARK)
			.setAuthor(localized(locale, "guild.module_enabled"), null, null)
			.addField(localized(locale, "guild.module"), lu.getLocalized(locale, module.getPath()), true)
			.addField(localized(locale, "enforcer"), mod.getAsMention(), false)
			.setFooter("ID: "+mod.getId())
			.build();
	}

	@NotNull
	public MessageEmbed moduleDisabled(DiscordLocale locale, User mod, CmdModule module) {
		return getEmbed(RED_DARK)
			.setAuthor(localized(locale, "guild.module_disabled"), null, null)
			.addField(localized(locale, "guild.module"), lu.getLocalized(locale, module.getPath()), true)
			.addField(localized(locale, "enforcer"), mod.getAsMention(), false)
			.setFooter("ID: "+mod.getId())
			.build();
	}
	

}