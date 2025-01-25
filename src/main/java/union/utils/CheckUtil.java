package union.utils;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import net.dv8tion.jda.api.entities.Role;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import union.App;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.Constants;
import union.utils.exception.CheckException;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

public class CheckUtil {

	private final App bot;
	private final String ownerId;

	public CheckUtil(App bot, String ownerId) {
		this.bot = bot;
		this.ownerId = ownerId;
	}

	public boolean isDeveloper(UserSnowflake user) {
		return user.getId().equals(Constants.DEVELOPER_ID);
	}

	public boolean isBotOwner(UserSnowflake user) {
		return user.getId().equals(ownerId);
	}

	public CmdAccessLevel getAccessLevel(Member member) {
		// Is bot developer
		if (isDeveloper(member) || isBotOwner(member))
			return CmdAccessLevel.DEV;
		
		// Is guild's owner
		if (member.isOwner())
			return CmdAccessLevel.OWNER;

		// Check if is operator
		if (bot.getDBUtil().access.isOperator(member.getGuild().getIdLong(), member.getIdLong()))
			return CmdAccessLevel.OPERATOR;
		
		// Check if user has Administrator privileges
		if (member.hasPermission(Permission.ADMINISTRATOR))
			return CmdAccessLevel.ADMIN;

		// Check for role level
		Map<Long, CmdAccessLevel> roleIds = bot.getDBUtil().access.getAllRoles(member.getGuild().getIdLong());
		if (roleIds.isEmpty()) return CmdAccessLevel.ALL;

        return member.getRoles()
			.stream()
			.filter(role -> roleIds.containsKey(role.getIdLong()))
			.map(role -> roleIds.get(role.getIdLong()))
			.max(CmdAccessLevel::compareTo)
			.orElse(CmdAccessLevel.ALL);
	}

	public boolean hasHigherAccess(Member who, Member than) {
		return getAccessLevel(who).isHigherThan(getAccessLevel(than));
	}

	public boolean hasAccess(Member member, CmdAccessLevel accessLevel) {
		if (accessLevel.equals(CmdAccessLevel.ALL)) return true;
		return !accessLevel.isHigherThan(getAccessLevel(member));
    }

	public CheckUtil hasAccess(IReplyCallback replyCallback, Member member, CmdAccessLevel accessLevel) throws CheckException {
		if (accessLevel.equals(CmdAccessLevel.ALL)) return this;
		if (accessLevel.isHigherThan(getAccessLevel(member)))
			throw new CheckException(bot.getEmbedUtil().getError(replyCallback, "errors.interaction.no_access", "Required access: "+accessLevel.getName()));
		return this;
	}

	public CheckUtil moduleEnabled(IReplyCallback replyCallback, Guild guild, CmdModule module) throws CheckException {
		if (module == null)
			return this;
		if (bot.getDBUtil().getGuildSettings(guild).isDisabled(module)) 
			throw new CheckException(bot.getEmbedUtil().getError(replyCallback, "modules.module_disabled"));
		return this;
	}

	public CheckUtil hasPermissions(IReplyCallback replyCallback, Guild guild, Member member, Permission[] permissions) throws CheckException {
		return hasPermissions(replyCallback, guild, member, false, null, permissions);
	}

	public CheckUtil hasPermissions(IReplyCallback replyCallback, Guild guild, Member member, boolean isSelf, Permission[] permissions) throws CheckException {
		return hasPermissions(replyCallback, guild, member, isSelf, null, permissions);
	}

	public CheckUtil hasPermissions(IReplyCallback replyCallback, Guild guild, Member member, boolean isSelf, GuildChannel channel, Permission[] permissions) throws CheckException {
		if (permissions == null || permissions.length == 0)
			return this;
		if (guild == null || (!isSelf && member == null))
			return this;

		MessageCreateData msg = null;
		if (isSelf) {
			Member self = guild.getSelfMember();
			if (channel == null) {
				for (Permission perm : permissions) {
					if (!self.hasPermission(perm)) {
						msg = bot.getEmbedUtil().createPermError(replyCallback, perm, true);
						break;
					}
				}
			} else {
				for (Permission perm : permissions) {
					if (!self.hasPermission(channel, perm)) {
						msg = bot.getEmbedUtil().createPermError(replyCallback, channel, perm, true);
						break;
					}
				}
			}
		} else {
			if (channel == null) {
				for (Permission perm : permissions) {
					if (!member.hasPermission(perm)) {
						msg = bot.getEmbedUtil().createPermError(replyCallback, perm, false);
						break;
					}
				}
			} else {
				for (Permission perm : permissions) {
					if (!member.hasPermission(channel, perm)) {
						msg = bot.getEmbedUtil().createPermError(replyCallback, channel, perm, false);
						break;
					}
				}
			}
		}
		if (msg != null) {
			throw new CheckException(msg);
		}
		return this;
	}

	private final Set<Permission> adminPerms = Set.of(Permission.ADMINISTRATOR, Permission.MANAGE_CHANNEL, Permission.MANAGE_ROLES, Permission.MANAGE_SERVER, Permission.BAN_MEMBERS);

	@Nullable
	public String denyRole(@NotNull Role role, @NotNull Guild guild, @NotNull Member member, boolean checkPerms) {
		if (role.isPublicRole()) return "`@everyone` is public";
		else if (role.isManaged()) return "Bot's role";
		else if (!member.canInteract(role)) return "You can't interact with this role";
		else if (!guild.getSelfMember().canInteract(role)) return "Bot can't interact with this role";
		else if (checkPerms) {
			EnumSet<Permission> rolePerms = EnumSet.copyOf(role.getPermissions());
			rolePerms.retainAll(adminPerms);
			if (!rolePerms.isEmpty()) return "This role has Administrator/Manager permissions";
		}
		return null;
	}

}
