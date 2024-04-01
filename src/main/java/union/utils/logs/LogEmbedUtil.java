package union.utils.logs;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import union.objects.CmdModule;
import union.objects.annotation.NotNull;
import union.objects.annotation.Nullable;
import union.objects.constants.Constants;
import union.utils.SteamUtil;
import union.utils.database.managers.CaseManager.CaseData;
import union.utils.file.lang.LocaleUtil;
import union.utils.message.TimeUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.interactions.DiscordLocale;

public class LogEmbedUtil {

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

	public LogEmbedUtil(LocaleUtil localeUtil) {
		this.lu = localeUtil;
	}

	private String localized(DiscordLocale locale, String pathFooter) {
		return lu.getLocalized(locale, pathHeader+pathFooter);
	}

	private class LogEmbedBuilder {

		private final DiscordLocale locale;
		private final EmbedBuilder builder;

		public LogEmbedBuilder(DiscordLocale locale) {
			this.locale = locale;
			this.builder = new EmbedBuilder().setColor(DEFAULT).setTimestamp(Instant.now());
		}

		public LogEmbedBuilder(DiscordLocale locale, int color) {
			this.locale = locale;
			this.builder = new EmbedBuilder().setColor(color).setTimestamp(Instant.now());
		}

		public LogEmbedBuilder(DiscordLocale locale, Instant timestamp) {
			this.locale = locale;
			this.builder = new EmbedBuilder().setColor(DEFAULT).setTimestamp(timestamp);
		}

		public LogEmbedBuilder setId(Object id) {
			builder.setFooter("ID: "+id);
			return this;
		}

		public LogEmbedBuilder setHeader(String path) {
			builder.setAuthor(localized(locale, path));
			return this;
		}

		public LogEmbedBuilder setHeader(String path, String arg) {
			return setHeader(path, arg);
		}

		public LogEmbedBuilder setHeader(String path, Object... args) {
			builder.setAuthor(localized(locale, path).formatted(args));
			return this;
		}

		public LogEmbedBuilder setHeaderIcon(String path, String iconUrl, Object... args) {
			builder.setAuthor(localized(locale, path).formatted(args), null, iconUrl);
			return this;
		}

		public LogEmbedBuilder addField(String path, String value) {
			return addField(path, value, true);
		}

		public LogEmbedBuilder addField(String path, String value, boolean inline) {
			builder.addField(localized(locale, path), value == null ? "-" : value, inline);
			return this;
		}

		public LogEmbedBuilder setUser(Long userId) {
			return addField(localized(locale, "user"), userId == null ? "-" : "<@"+userId+">");
		}

		public LogEmbedBuilder setMod(Long modId) {
			return addField(localized(locale, "mod"), modId == null ? "-" : "<@"+modId+">");
		}

		public LogEmbedBuilder setEnforcer(Long userId) {
			return addField(localized(locale, "enforcer"), userId == null ? "-" : "<@"+userId+">");
		}

		public LogEmbedBuilder setReason(String reason) {
			return addField(localized(locale, "reason"), reason == null ? "-" : reason);
		}

		public LogEmbedBuilder setColor(int color) {
			builder.setColor(color);
			return this;
		}

		public LogEmbedBuilder setFooter(String text) {
			builder.setFooter(text);
			return this;
		}

		public LogEmbedBuilder setTitle(String path) {
			builder.setTitle(localized(locale, path));
			return this;
		}

		public LogEmbedBuilder setDescription(String text) {
			builder.setDescription(text);
			return this;
		}

		public MessageEmbed build() {
			return builder.build();
		}
	}

	
	// Moderation
	@NotNull
	private LogEmbedBuilder moderationEmbedBuilder(DiscordLocale locale, CaseData caseData) {
		return moderationEmbedBuilder(locale, caseData, null);
	}

	@NotNull
	private LogEmbedBuilder moderationEmbedBuilder(DiscordLocale locale, CaseData caseData, String userIcon) {
		LogEmbedBuilder builder = new LogEmbedBuilder(locale, caseData.getTimeStart())
			.setHeaderIcon("case", userIcon, caseData.getCaseId(), lu.getLocalized(locale, caseData.getCaseType().getPath()), caseData.getTargetTag())
			.setUser(caseData.getTargetId())
			.setMod(caseData.getModId()>0 ? caseData.getModId() : null)
			.setReason(caseData.getReason())
			.setId(caseData.getTargetId());
		if (!caseData.getDuration().isNegative())
			builder.addField("duration", TimeUtil.formatDuration(lu, locale, caseData.getTimeStart(), caseData.getDuration()));
		return builder;
	}

	public MessageEmbed getCaseEmbed(DiscordLocale locale, CaseData caseData) {
		return moderationEmbedBuilder(locale, caseData).build();
	}

	//  Ban
	@NotNull
	public MessageEmbed banEmbed(DiscordLocale locale, CaseData caseData, String userIcon) {
		return moderationEmbedBuilder(locale, caseData, userIcon)
			.setColor(RED_DARK)
			.build();
	}

	@NotNull
	public MessageEmbed helperBanEmbed(DiscordLocale locale, int groupId, User target, String reason, int success, int max) {
		return new LogEmbedBuilder(locale, RED_DARK)
			.setHeaderIcon("ban.title_synced", target.getAvatarUrl(), target.getName())
			.setUser(target.getIdLong())
			.setReason(reason)
			.addField("success", success+"/"+max)
			.setFooter("Group ID: "+groupId)
			.build();
	}

	//  Unban
	@NotNull
	public MessageEmbed unbanEmbed(DiscordLocale locale, CaseData caseData, String banReason) {
		return moderationEmbedBuilder(locale, caseData)
			.setColor(AMBER_DARK)
			.addField("unban.ban_reason", banReason)
			.build();
	}

	@NotNull
	public MessageEmbed helperUnbanEmbed(DiscordLocale locale, int groupId, User target, String reason, int success, int max) {
		return new LogEmbedBuilder(locale, AMBER_DARK)
			.setHeaderIcon("unban.title_synced", target.getAvatarUrl(), target.getName())
			.setUser(target.getIdLong())
			.setReason(reason)
			.addField("success", success+"/"+max)
			.setFooter("Group ID: "+groupId)
			.build();
	}

	@NotNull
	public MessageEmbed autoUnbanEmbed(DiscordLocale locale, CaseData caseData) {
		return new LogEmbedBuilder(locale, AMBER_DARK)
			.setHeader("unban.title_expired", caseData.getTargetTag())
			.setUser(caseData.getTargetId())
			.addField("unban.ban_reason", caseData.getReason())
			.addField("duration", TimeUtil.durationToString(caseData.getDuration()))
			.setId(caseData.getTargetId())
			.build();
	}

	//  Kick
	@NotNull
	public MessageEmbed kickEmbed(DiscordLocale locale, CaseData caseData, String userIcon) {
		return moderationEmbedBuilder(locale, caseData, userIcon)
			.setColor(RED_DARK)
			.build();
	}

	@NotNull
	public MessageEmbed helperKickEmbed(DiscordLocale locale, Integer groupId, User target, String reason, int success, int max) {
		return new LogEmbedBuilder(locale, RED_DARK)
			.setHeaderIcon("kick.title_synced", target.getAvatarUrl(), target.getName())
			.setUser(target.getIdLong())
			.setReason(reason)
			.addField("success", success+"/"+max)
			.setFooter("Group ID: "+groupId)
			.build();
	}

	//  Mute
	@NotNull
	public MessageEmbed muteEmbed(DiscordLocale locale, CaseData caseData, String userIcon) {
		return moderationEmbedBuilder(locale, caseData, userIcon)
			.setColor(RED_DARK)
			.build();
	}

	@NotNull
	public MessageEmbed unmuteEmbed(DiscordLocale locale, CaseData caseData, String userIcon, String muteReason) {
		return moderationEmbedBuilder(locale, caseData, userIcon)
			.setColor(AMBER_DARK)
			.addField("unmute.mute_reason", muteReason)
			.build();
	}

	//  Strike
	public MessageEmbed strikeEmbed(DiscordLocale locale, CaseData caseData, String userIcon) {
		return moderationEmbedBuilder(locale, caseData, userIcon)
			.setColor(AMBER_LIGHT)
			.build();
	}

	public MessageEmbed strikesClearedEmbed(DiscordLocale locale, String userTag, long userId, long modId) {
		return new LogEmbedBuilder(locale, AMBER_LIGHT)
			.setHeader("strike.cleared", userTag)
			.setUser(userId)
			.setMod(modId)
			.setId(userId)
			.build();
	}

	public MessageEmbed strikeDeletedEmbed(DiscordLocale locale, String userTag, long userId, long modId, int caseId, int deletedAmount, int maxAmount) {
		return new LogEmbedBuilder(locale, AMBER_LIGHT)
			.setHeader("strike.deleted", userTag)
			.addField("strike.case", String.valueOf(caseId))
			.addField("strike.amount", deletedAmount+"/"+maxAmount)
			.setUser(userId)
			.setMod(modId)
			.setId(userId)
			.build();
	}

	//  Reason
	@NotNull
	public MessageEmbed reasonChangedEmbed(DiscordLocale locale, CaseData caseData, long modId, String newReason) {
		return new LogEmbedBuilder(locale)
			.setHeader("change.reason", caseData.getCaseId(), caseData.getTargetTag())
			.setDescription("> %s\n\n🔴 ~~%s~~\n🟢 %s".formatted(lu.getLocalized(locale, caseData.getCaseType().getPath()), Optional.ofNullable(caseData.getReason()).orElse("None"), newReason))
			.setUser(caseData.getTargetId())
			.setMod(modId)
			.setId(caseData.getTargetId())
			.build();
	}

	//  Duration
	@NotNull
	public MessageEmbed durationChangedEmbed(DiscordLocale locale, CaseData caseData, long modId, String newTime) {
		String oldTime = TimeUtil.formatDuration(lu, locale, caseData.getTimeStart(), caseData.getDuration());
		return new LogEmbedBuilder(locale)
			.setHeader("change.duration", caseData.getCaseId(), caseData.getTargetTag())
			.setDescription("> %s\n\n🔴 ~~%s~~\n🟢 %s".formatted(lu.getLocalized(locale, caseData.getCaseType().getPath()), oldTime, newTime))
			.setUser(caseData.getTargetId())
			.setMod(modId)
			.setId(caseData.getTargetId())
			.build();
	}

	//  Blacklist
	@NotNull
	public MessageEmbed blacklistAddedEmbed(DiscordLocale locale, User enforcer, User target, String steamID, String groupInfo) {
		return new LogEmbedBuilder(locale, RED_DARK)
			.setHeaderIcon( "blacklist.added", target.getAvatarUrl(), target.getName())
			.setUser(target.getIdLong())
			.addField("blacklist.steam", steamID)
			.addField("blacklist.group", groupInfo)
			.setEnforcer(enforcer.getIdLong())
			.setId(target.getId())
			.build();
	}

	@NotNull
	public MessageEmbed blacklistRemovedEmbed(DiscordLocale locale, User enforcer, User target, String steamID, String groupInfo) {
		return new LogEmbedBuilder(locale, GREEN_DARK)
			.setHeaderIcon( "blacklist.removed", target != null ? target.getAvatarUrl() : null, target != null ? target.getName() : steamID)
			.setUser(target != null ? target.getIdLong() : null)
			.addField("blacklist.steam", steamID)
			.addField("blacklist.group", groupInfo)
			.setEnforcer(enforcer.getIdLong())
			.setId(target.getId())
			.build();
	}

	// Roles
	@NotNull
	public MessageEmbed rolesApprovedEmbed(DiscordLocale locale, String ticketId, long memberId, String mentions, long modId) {
		return new LogEmbedBuilder(locale, GREEN_DARK)
			.setHeader("ticket.roles_title", "role-"+ticketId)
			.setUser(memberId)
			.addField("ticket.roles", mentions)
			.setEnforcer(modId)
			.setId(memberId)
			.build();
	}

	@NotNull
	public MessageEmbed checkRankEmbed(DiscordLocale locale, long modId, long roleId, String rankName) {
		return new LogEmbedBuilder(locale, AMBER_LIGHT)
			.setHeader("roles.checkrank")
			.addField("roles.role", "<@&"+roleId+">")
			.addField("roles.rank", rankName)
			.setEnforcer(modId)
			.build();
	}

	@NotNull
	public MessageEmbed roleAddedEmbed(DiscordLocale locale, long modId, long userId, String userUrl, long roleId) {
		return new LogEmbedBuilder(locale, GREEN_LIGHT)
			.setHeaderIcon("roles.added", userUrl)
			.setUser(userId)
			.addField("roles.role", "<@&"+roleId+">")
			.setMod(modId)
			.setId(userId)
			.build();
	}

	@NotNull
	public MessageEmbed roleRemovedEmbed(DiscordLocale locale, long modId, long userId, String userUrl, long roleId) {
		return new LogEmbedBuilder(locale, RED_LIGHT)
			.setHeaderIcon("roles.removed", userUrl)
			.setUser(userId)
			.addField("roles.role", "<@&"+roleId+">")
			.setMod(modId)
			.setId(userId)
			.build();
	}

	@NotNull
	public MessageEmbed roleRemovedAllEmbed(DiscordLocale locale, long modId, long roleId) {
		return new LogEmbedBuilder(locale, RED_DARK)
			.setHeader("roles.removed_all")
			.addField("roles.role", "<@&"+roleId+">")
			.setEnforcer(modId)
			.build();
	}

	@NotNull
	public MessageEmbed tempRoleAddedEmbed(DiscordLocale locale, User mod, User user, Role role, Duration duration) {
		return new LogEmbedBuilder(locale, GREEN_LIGHT)
			.setHeaderIcon("roles.temp_added", user.getAvatarUrl())
			.setUser(user.getIdLong())
			.addField("roles.role", role.getAsMention())
			.addField("duration", TimeUtil.durationToLocalizedString(lu, locale, duration))
			.setMod(mod.getIdLong())
			.setId(user.getIdLong())
			.build();
	}

	@NotNull
	public MessageEmbed tempRoleRemovedEmbed(DiscordLocale locale, User mod, User user, Role role) {
		return new LogEmbedBuilder(locale, RED_LIGHT)
			.setHeaderIcon("roles.temp_removed", user.getAvatarUrl())
			.setUser(user.getIdLong())
			.addField("roles.role", role.getAsMention())
			.setMod(mod.getIdLong())
			.setId(user.getIdLong())
			.build();
	}

	public MessageEmbed tempRoleUpdatedEmbed(DiscordLocale locale, User mod, User user, Role role, Instant until) {
		return new LogEmbedBuilder(locale, AMBER_LIGHT)
			.setHeaderIcon("roles.temp_updated", user.getAvatarUrl())
			.setUser(user.getIdLong())
			.addField("roles.role", role.getAsMention())
			.addField("duration", TimeUtil.formatTime(until, false))
			.setMod(mod.getIdLong())
			.setId(user.getIdLong())
			.build();
	}

	@NotNull
	public MessageEmbed tempRoleAutoRemovedEmbed(DiscordLocale locale, long targetId, Role role) {
		return new LogEmbedBuilder(locale, RED_LIGHT)
			.setHeader("roles.temp_removed")
			.setUser(targetId)
			.addField("roles.role", role.getAsMention())
			.setId(targetId)
			.build();
	}

	@NotNull
	public MessageEmbed checkRoleChildGuild(DiscordLocale locale, long modId, long roleId, String guildName, long guildId) {
		return new LogEmbedBuilder(locale, AMBER_LIGHT)
			.setHeader("roles.checkchild")
			.addField("roles.role", "<@&"+roleId+">")
			.addField("roles.guild", "`%s` (%s)".formatted(guildName, guildId))
			.setEnforcer(modId)
			.build();
	}


	// Groups
	@NotNull
	private LogEmbedBuilder groupLogBuilder(DiscordLocale locale, long ownerId, String ownerIcon, int groupId, String name) {
		return new LogEmbedBuilder(locale)
			.setHeaderIcon("group.title", ownerIcon, name, groupId)
			.setFooter(localized(locale, "group.master")+ownerId);
	}

	@NotNull
	public MessageEmbed groupCreatedEmbed(DiscordLocale locale, String adminMention, long ownerId, String ownerIcon, int groupId, String name) {
		return groupLogBuilder(locale, ownerId, ownerIcon, groupId, name)
			.setColor(GREEN_DARK)
			.setTitle("group.created")
			.addField("group.admin", adminMention)
			.build();
	}

	@NotNull
	public MessageEmbed groupMemberDeletedEmbed(DiscordLocale locale, long ownerId, String ownerIcon, int groupId, String name) {
		return groupLogBuilder(locale, ownerId, ownerIcon, groupId, name)
			.setColor(RED_DARK)
			.setTitle("group.deleted")
			.build();
	}

	@NotNull
	public MessageEmbed groupOwnerDeletedEmbed(DiscordLocale locale, String adminMention, long ownerId, String ownerIcon, int groupId, String name) {
		return groupLogBuilder(locale, ownerId, ownerIcon, groupId, name)
			.setColor(RED_DARK)
			.setTitle("group.deleted")
			.addField("group.admin", adminMention)
			.build();
	}

	@NotNull
	public MessageEmbed groupMemberJoinedEmbed(DiscordLocale locale, String adminMention, long ownerId, String ownerIcon, int groupId, String name) {
		return groupLogBuilder(locale, ownerId, ownerIcon, groupId, name)
			.setColor(GREEN_DARK)
			.setTitle("group.join")
			.addField("group.admin", adminMention)
			.build();
	}

	@NotNull
	public MessageEmbed groupOwnerJoinedEmbed(DiscordLocale locale, long ownerId, String ownerIcon, String targetName, long targetId, int groupId, String name) {
		return groupLogBuilder(locale, ownerId, ownerIcon, groupId, name)
			.setColor(GREEN_DARK)
			.setTitle("group.joined")
			.addField("group.guild", "*%s* (`%s`)".formatted(targetName, targetId))
			.build();
	}

	@NotNull
	public MessageEmbed groupMemberAddedEmbed(DiscordLocale locale, long ownerId, String ownerIcon, int groupId, String name) {
		return groupLogBuilder(locale, ownerId, ownerIcon, groupId, name)
			.setColor(GREEN_DARK)
			.setTitle("group.add")
			.build();
	}

	@NotNull
	public MessageEmbed groupOwnerAddedEmbed(DiscordLocale locale, String adminMention, long ownerId, String ownerIcon, String targetName, long targetId, int groupId, String name) {
		return groupLogBuilder(locale, ownerId, ownerIcon, groupId, name)
			.setColor(GREEN_DARK)
			.setTitle("group.added")
			.addField("group.guild", "*%s* (`%s`)".formatted(targetName, targetId))
			.addField("group.admin", adminMention)
			.build();
	}

	@NotNull
	public MessageEmbed groupMemberLeftEmbed(DiscordLocale locale, String adminMention, long ownerId, String ownerIcon, int groupId, String name) {
		return groupLogBuilder(locale, ownerId, ownerIcon, groupId, name)
			.setColor(RED_DARK)
			.setTitle("group.leave")
			.addField("group.admin", adminMention)
			.build();
	}

	@NotNull
	public MessageEmbed groupOwnerLeftEmbed(DiscordLocale locale, long ownerId, String ownerIcon, String targetName, long targetId, int groupId, String name) {
		return groupLogBuilder(locale, ownerId, ownerIcon, groupId, name)
			.setColor(RED_DARK)
			.setTitle("group.left")
			.addField("group.guild", "*%s* (`%s`)".formatted(targetName, targetId))
			.build();
	}

	@NotNull
	public MessageEmbed groupOwnerRemovedEmbed(DiscordLocale locale, String adminMention, long ownerId, String ownerIcon, String targetName, long targetId, int groupId, String name) {
		return groupLogBuilder(locale, ownerId, ownerIcon, groupId, name)
			.setColor(RED_DARK)
			.setTitle("group.removed")
			.addField("group.guild", "*%s* (`%s`)".formatted(targetName, targetId))
			.addField("group.admin", adminMention)
			.build();
	}

	@NotNull
	public MessageEmbed groupMemberRenamedEmbed(DiscordLocale locale, long ownerId, String ownerIcon, int groupId, String oldName, String newName) {
		return groupLogBuilder(locale, ownerId, ownerIcon, groupId, newName)
			.setTitle("group.renamed")
			.addField("group.oldname", oldName)
			.build();
	}

	@NotNull
	public MessageEmbed groupOwnerRenamedEmbed(DiscordLocale locale, String adminMention, long ownerId, String ownerIcon, int groupId, String oldName, String newName) {
		return groupLogBuilder(locale, ownerId, ownerIcon, groupId, newName)
			.setTitle("group.renamed")
			.addField("group.oldname", oldName)
			.addField("group.admin", adminMention)
			.build();
	}

	// OTHER

	@NotNull
	public MessageEmbed auditLogEmbed(DiscordLocale locale, int groupId, Guild target, AuditLogEntry auditLogEntry) {
		String titlePath = switch (auditLogEntry.getType()) {
			case BAN -> "audit.banned";
			case UNBAN -> "audit.unbanned";
			default -> "audit.default";
		};
		return new LogEmbedBuilder(locale, AMBER_LIGHT)
			.setHeaderIcon(titlePath, target.getIconUrl(), target.getName())
			.setUser(auditLogEntry.getTargetIdLong())
			.setReason(auditLogEntry.getReason())
			.setEnforcer(auditLogEntry.getUserIdLong())
			.setFooter("Group ID: "+groupId)
			.build();
	}

	@NotNull
	public MessageEmbed alertEmbed(DiscordLocale locale, int groupId, Guild guild, Member member, String actionTaken, String reason) {
		return new LogEmbedBuilder(locale, AMBER_LIGHT)
			.setHeaderIcon("audit.alert", guild.getIconUrl(), guild.getName())
			.addField("target", "%s (%d)".formatted(member.getAsMention(), member.getIdLong()))
			.setReason(reason)
			.addField("audit.action", actionTaken, false)
			.setFooter("Group ID: "+groupId)
			.build();
	}

	@NotNull
	public MessageEmbed botLeftEmbed(DiscordLocale locale, int groupId, @Nullable Guild guild, String guildId) {
		return new LogEmbedBuilder(locale, AMBER_LIGHT)
			.setHeader("audit.leave_guild", Optional.ofNullable(guild).map(Guild::getName).orElse("unknown"))
			.addField("audit.guild_id", guildId)
			.setFooter("Group ID: "+groupId)
			.build();
	}


	// Verification
	@NotNull
	public MessageEmbed verifiedEmbed(DiscordLocale locale, String memberTag, long memberId, String memberIcon, String steamName, Long steam64) {
		return new LogEmbedBuilder(locale, GREEN_DARK)
			.setHeaderIcon("verify.added", memberIcon, memberTag)
			.addField("verify.steam", (steam64 == null ? "None" :
				"%s `%s`\n[UnionTeams](https://unionteams.ru/player/%s)\n[Steam](https://steamcommunity.com/profiles/%<s)".formatted(steamName, SteamUtil.convertSteam64toSteamID(steam64), steam64)
				))
			.addField("verify.discord", "<@"+memberId+">")
			.setId(memberId)
			.build();
	}

	@NotNull
	public MessageEmbed unverifiedEmbed(DiscordLocale locale, String memberTag, long memberId, String memberIcon, String steamName, Long steam64, String reason) {
		return new LogEmbedBuilder(locale, RED_DARK)
			.setHeaderIcon("verify.removed", memberIcon, memberTag)
			.addField("verify.steam", (steam64 == null ? "None" :
				"%s `%s`\n[UnionTeams](https://unionteams.ru/player/%s)\n[Steam](https://steamcommunity.com/profiles/%<s)".formatted(steamName, SteamUtil.convertSteam64toSteamID(steam64), steam64)
				))
			.addField("verify.discord", "<@"+memberId+">")
			.setReason(reason)
			.setId(memberId)
			.build();
	}

	@NotNull
	public MessageEmbed verifyAttempt(DiscordLocale locale, String memberTag, long memberId, String memberIcon, Long steam64, String reason) {
		return new LogEmbedBuilder(locale, AMBER_DARK)
			.setHeaderIcon("verify.attempt", memberIcon, memberTag)
			.setDescription(localized(locale, "reason")+":\n> "+reason)
			.addField("verify.steam", (steam64 == null ? "None" :
				"`%s`\n[UnionTeams](https://unionteams.ru/player/%s)\n[Steam](https://steamcommunity.com/profiles/%<s)".formatted(SteamUtil.convertSteam64toSteamID(steam64), steam64)
				))
			.addField("verify.discord", "<@"+memberId+">")
			.setId(memberId)
			.build();
	}

	// Tickets
	@NotNull
	public MessageEmbed ticketCreatedEmbed(DiscordLocale locale, GuildMessageChannel channel, User author) {
		return new LogEmbedBuilder(locale, GREEN_LIGHT)
			.setHeader("ticket.created")
			.setUser(author.getIdLong())
			.addField("ticket.name", channel.getName())
			.setFooter("Channel ID: "+channel.getId())
			.build();
	}

	@NotNull
	public MessageEmbed ticketClosedEmbed(DiscordLocale locale, GuildMessageChannel channel, User userClosed, String authorId, String claimerId) {
		return new LogEmbedBuilder(locale, RED_LIGHT)
			.setHeader("ticket.closed_title")
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
		return new LogEmbedBuilder(locale, WHITE)
			.setDescription(localized(locale, "ticket.closed_pm")
				.replace("{guild}", channel.getGuild().getName())
				.replace("{closed}", Optional.ofNullable(userClosed).map(User::getAsMention).orElse(localized(locale, "ticket.autoclosed")))
				.replace("{time}", TimeUtil.formatTime(timeClosed, false))
				.replace("{reason}", reasonClosed)
			)
			.setFooter(channel.getName())
			.build();
	}


	// Server
	@NotNull
	public MessageEmbed accessAdded(DiscordLocale locale, User mod, User userTarget, Role roleTarget, String levelName) {
		String targetMention = userTarget!=null ? userTarget.getAsMention() : roleTarget.getAsMention();
		String targetId = userTarget!=null ? userTarget.getId() : roleTarget.getId();
		return new LogEmbedBuilder(locale, GREEN_DARK)
			.setHeaderIcon("ticket.access_added", userTarget != null ? userTarget.getAvatarUrl() : null)
			.addField("target", targetMention)
			.addField("guild.access_level", levelName)
			.setEnforcer(mod.getIdLong())
			.setId(targetId)
			.build();
	}

	@NotNull
	public MessageEmbed accessRemoved(DiscordLocale locale, User mod, User userTarget, Role roleTarget, String levelName) {
		String targetMention = userTarget!=null ? userTarget.getAsMention() : roleTarget.getAsMention();
		String targetId = userTarget!=null ? userTarget.getId() : roleTarget.getId();
		return new LogEmbedBuilder(locale, RED_DARK)
			.setHeaderIcon("ticket.access_removed", userTarget != null ? userTarget.getAvatarUrl() : null)
			.addField("target", targetMention)
			.addField("guild.access_level", levelName)
			.setEnforcer(mod.getIdLong())
			.setId(targetId)
			.build();
	}

	@NotNull
	public MessageEmbed moduleEnabled(DiscordLocale locale, User mod, CmdModule module) {
		return new LogEmbedBuilder(locale, GREEN_DARK)
			.setHeader("ticket.module_enabled")
			.addField("guild.module", lu.getLocalized(locale, module.getPath()))
			.setEnforcer(mod.getIdLong())
			.build();
	}

	@NotNull
	public MessageEmbed moduleDisabled(DiscordLocale locale, User mod, CmdModule module) {
		return new LogEmbedBuilder(locale, RED_DARK)
			.setHeader("ticket.module_disabled")
			.addField("guild.module", lu.getLocalized(locale, module.getPath()))
			.setEnforcer(mod.getIdLong())
			.build();
	}
	

}