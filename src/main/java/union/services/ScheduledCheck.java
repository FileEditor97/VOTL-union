package union.services;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import union.App;
import union.utils.database.DBUtil;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;

public class ScheduledCheck {

	private final App bot;
	private final DBUtil db;
	public Instant lastAccountCheck;

	public ScheduledCheck(App bot) {
		this.bot = bot;
		this.db = bot.getDBUtil();
		this.lastAccountCheck = Instant.now();
	}
	
	public void checkUnbans() {
		List<Map<String, Object>> bans = db.ban.getExpirable();
		if (bans.isEmpty()) return;
		bans.stream().filter(ban ->
			Duration.between(Instant.parse(ban.get("timeStart").toString()), Instant.now()).compareTo(Duration.parse(ban.get("duration").toString())) >= 0
		).forEach(ban -> {
			Integer banId = Integer.parseInt(ban.get("banId").toString());
			Guild guild = bot.JDA.getGuildById(ban.get("guildId").toString());
			if (guild == null || !guild.getSelfMember().hasPermission(Permission.BAN_MEMBERS)) return;
			guild.unban(User.fromId(ban.get("userId").toString())).reason("Temporary ban expired").queue(
				s -> bot.getLogListener().onAutoUnban(ban, banId, guild),
				f -> bot.getLogger().warn("Exception at unban attempt", f.getMessage())
			);
			db.ban.setInactive(banId);
		});
	}

	public void checkAccountUpdates() {
		try {
			lastAccountCheck = Instant.now();
			List<Map<String, String>> data = db.verifyRequest.updatedAccounts();
			if (data.isEmpty()) return;

			List<Map<String, String>> removeRoles = new ArrayList<Map<String, String>>();
			for (Map<String, String> account : data) {
				String steam64 = account.get("steam_id");
				db.verifyRequest.clearUpdated(steam64);
				
				String cacheDiscordId = db.verifyCache.getDiscordId(steam64);
				
				
				if (Objects.isNull(account.get("discord_id"))) {
					// if not cached - cant track
					if (cacheDiscordId == null) return;
					// if forced - skip
					if (db.verifyCache.isForced(cacheDiscordId)) {
						db.verifyCache.removeSteam64(cacheDiscordId);
						return;
					};

					db.verifyCache.removeByDiscord(cacheDiscordId);
					removeRoles.add(Map.of("discord_id", cacheDiscordId, "steam_id", steam64));
				} else {
					String discordId = account.get("discord_id");
					// if exists in cache
					if (cacheDiscordId != null) {
						// if same - skip
						if (cacheDiscordId.equals(discordId)) return;
						// duplicate, remove roles from previous discord account
						removeRoles.add(account);
						db.verifyCache.removeByDiscord(cacheDiscordId);
					} else if (db.verifyCache.isVerified(discordId)) {
						// exist discordId, but no steam64 (probably forced user)
						db.verifyCache.setSteam64(discordId, steam64);
						return;
					}

					// Add user to cache
					db.verifyCache.addUser(discordId, steam64);
				}
			};
			if (removeRoles.isEmpty()) return;

			bot.JDA.getGuilds().forEach(guild -> {
				String roleId = db.verify.getVerifyRole(guild.getId());
				if (roleId == null) return;
				Role role = guild.getRoleById(roleId);
				if (role == null) return;

				removeRoles.forEach(account -> {
					String steam64 = account.get("steam_id");
					String userId = account.get("discord_id");
					guild.retrieveMemberById(userId).queue(member -> {
						if (!member.getRoles().contains(role)) return;
						guild.removeRoleFromMember(member, role).reason("Autocheck: Account unlinked").queue(success -> {
							bot.getLogListener().onUnverified(member.getUser(), steam64, guild, "Autocheck: Account unlinked");
						});
					}, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MEMBER));
				});
			});
		} catch (Exception ex) {
			bot.getLogger().error("Exception caught during verify checking.", ex);
		}
	}

}
