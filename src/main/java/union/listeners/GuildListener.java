package union.listeners;

import java.util.Map;
import java.util.Objects;

import jakarta.annotation.Nonnull;

import union.App;
import union.objects.CmdAccessLevel;
import union.utils.database.DBUtil;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class GuildListener extends ListenerAdapter {

	private final App bot;
	private final DBUtil db;

	public GuildListener(App bot) {
		this.bot = bot;
		this.db = bot.getDBUtil();
	}

	@Override
	public void onGuildJoin(@Nonnull GuildJoinEvent event) {
		String guildId = event.getGuild().getId();
		// check if not exists in DB and adds it
		if (db.guild.add(guildId)) {
			bot.getLogger().info("Automatically added guild '"+event.getGuild().getName()+"'("+guildId+") to db");
		}
		bot.getLogger().info("Joined guild '"+event.getGuild().getName()+"'("+guildId+")");
	}

	@Override
	public void onGuildLeave(@Nonnull GuildLeaveEvent event) {
		String guildId = event.getGuild().getId();

		// Deletes every information connected to this guild from bot's DB (except ban tables)
		// May be dangerous, but provides privacy
		for (Integer groupId : db.group.getGuildGroups(guildId)) {
			String groupName = db.group.getName(groupId);
			String guildName = event.getGuild().getName();
			String masterId = db.group.getMaster(groupId);
			String masterIcon = event.getJDA().getGuildById(masterId).getIconUrl();

			String masterChannelId = db.guild.getGroupLogChannel(masterId);
			if (masterChannelId != null) {
				TextChannel channel = event.getJDA().getTextChannelById(masterChannelId);
				if (channel != null) {
					try {
						MessageEmbed masterEmbed = bot.getLogUtil().getGroupOwnerLeaveEmbed(channel.getGuild().getLocale(), masterId, masterIcon, guildName, guildId, groupId, groupName);
						channel.sendMessageEmbeds(masterEmbed).queue();
					} catch (InsufficientPermissionException ex) {}
				}
			}
		}
		for (Integer groupId : db.group.getOwnedGroups(guildId)) {
			String groupName = db.group.getName(groupId);
			for (String gid : db.group.getGroupGuildIds(groupId)) {
				String channelId = db.guild.getGroupLogChannel(gid);
				if (channelId == null) {
					continue;
				}
				TextChannel channel = event.getJDA().getTextChannelById(channelId);
				if (channel == null) {
					continue;
				}

				try {
					MessageEmbed embed = bot.getLogUtil().getGroupMemberDeletedEmbed(channel.getGuild().getLocale(), guildId, event.getGuild().getIconUrl(), groupId, groupName);
					channel.sendMessageEmbeds(embed).queue();
				} catch (InsufficientPermissionException ex) {
					continue;
				}
			}
			db.group.clearGroup(groupId);
		}
		db.group.removeFromGroups(guildId);
		db.group.deleteAll(guildId);

		db.access.removeAll(guildId);
		db.module.removeAll(guildId);
		db.webhook.removeAll(guildId);
		db.verify.remove(guildId);
		db.ticketSettings.remove(guildId);
		db.role.removeAll(guildId);
		db.guildVoice.remove(guildId);
		db.panels.deleteAll(guildId);
		db.tags.deleteAll(guildId);
		db.tempRole.removeAll(guildId);
		
		db.guild.remove(guildId);

		bot.getLogger().info("Automatically removed guild '"+event.getGuild().getName()+"'("+guildId+") from db.");
		bot.getLogger().info("Left guild '"+event.getGuild().getName()+"'("+guildId+")");
	}

	@Override
	public void onGuildUnban(@Nonnull GuildUnbanEvent event) {
		Map<String, Object> banData = db.ban.getMemberExpirable(event.getUser().getId(), event.getGuild().getId());
		if (!banData.isEmpty()) {
			db.ban.setInactive(Integer.valueOf(banData.get("badId").toString()));
		}
	}

	@Override
	public void onGuildMemberJoin(@Nonnull GuildMemberJoinEvent event) {
		// Checks cache on local DB, if user is verified, gives out the role
		Guild guild = event.getGuild();
		String discordId = event.getUser().getId();
		
		if (db.verifyCache.isVerified(discordId)) {
			String cachedSteam64 = db.verifyCache.getSteam64(event.getUser().getId());
			String roleId = db.verify.getVerifyRole(guild.getId());
			if (Objects.isNull(roleId)) return;
			Role role = guild.getRoleById(roleId);
			if (Objects.isNull(role)) return;
			guild.addRoleToMember(event.getUser(), role).reason((Objects.isNull(cachedSteam64) ? "Autocheck: Forced" : "Autocheck: Account linked - "+cachedSteam64)).queue(success -> {
				bot.getLogListener().onVerified(event.getUser(), cachedSteam64, guild);
			});
		}
	}
	
	@Override
	public void onGuildMemberRemove(@Nonnull GuildMemberRemoveEvent event) {
		// When user leaves guild, check if there are any records in DB that would be better to remove.
		// This does not consider clearing User DB, when bot leaves guild.
		String guildId = event.getGuild().getId();
		String userId = event.getUser().getId();

		if (db.access.getUserLevel(guildId, userId).isHigherThan(CmdAccessLevel.ALL)) {
			db.access.removeUser(guildId, userId);
		}
		db.user.remove(userId);
	}
}
