package union.listeners;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import union.App;
import union.objects.annotation.NotNull;
import union.utils.database.DBUtil;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateAvatarEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class MemberListener extends ListenerAdapter {

	private final App bot;
	private final DBUtil db;

	public MemberListener(App bot) {
		this.bot = bot;
		this.db = bot.getDBUtil();
	}
	
	@Override
	public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
		// Checks cache on local DB, if user is verified, gives out the role
		long userId = event.getUser().getIdLong();
		
		if (db.verifyCache.isVerified(userId)) {
			Guild guild = event.getGuild();
			// Check if user is blacklisted
			List<Integer> groupIds = new ArrayList<Integer>();
			groupIds.addAll(db.group.getOwnedGroups(guild.getIdLong()));
			groupIds.addAll(db.group.getGuildGroups(guild.getIdLong()));
			Long cachedSteam64 = db.verifyCache.getSteam64(userId);
			for (int groupId : groupIds) {
				// if user is blacklisted in group either by discordID or Steam64
				// and joined server is not appeal server - do not add verify role
				if (db.blacklist.inGroupUser(groupId, userId) && db.group.getAppealGuildId(groupId)!=guild.getIdLong()) return;
				if (cachedSteam64!=null && db.blacklist.inGroupSteam64(groupId, cachedSteam64) && db.group.getAppealGuildId(groupId)!=guild.getIdLong()) return;
			}

			Long roleId = db.getVerifySettings(guild).getRoleId();
			if (roleId == null) return;
			Role role = guild.getRoleById(roleId);
			if (role == null) return;

			guild.addRoleToMember(event.getUser(), role).reason(cachedSteam64 == null ? "Autocheck: Forced" : "Autocheck: Account linked - "+cachedSteam64).queue(success -> {
				bot.getLogger().verify.onVerified(event.getUser(), cachedSteam64, guild);
			});
		}
	}
	
	@Override
	public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
		// When user leaves guild, check if there are any records in DB that would be better to remove.
		// This does not consider clearing User DB, when bot leaves guild.
		String guildId = event.getGuild().getId();
		String userId = event.getUser().getId();

		if (db.access.getUserLevel(guildId, userId) != null) {
			db.access.removeUser(guildId, userId);
		}
		db.user.remove(event.getUser().getIdLong());
		if (db.getTicketSettings(event.getGuild()).autocloseLeftEnabled()) {
			db.ticket.getOpenedChannel(userId, guildId).stream().forEach(channelId -> {
				db.ticket.closeTicket(Instant.now(), channelId, "Ticket's author left the server");
				GuildChannel channel = event.getGuild().getGuildChannelById(channelId);
				if (channel != null) channel.delete().reason("Author left").queue();
			});
		}
	}

	@Override
	public void onGuildMemberUpdateNickname(@NotNull GuildMemberUpdateNicknameEvent event) {}

	@Override
	public void onGuildMemberUpdateAvatar(@NotNull GuildMemberUpdateAvatarEvent event) {}
	
}
