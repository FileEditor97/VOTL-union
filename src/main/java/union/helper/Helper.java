package union.helper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import union.listeners.LogListener;
import union.utils.database.DBUtil;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;

public class Helper {

	private final JDA JDA;
	private final JDA botJDA;
	private final DBUtil db;
	private final LogListener logListener;
	private final Logger logger = (Logger) LoggerFactory.getLogger(Helper.class);

	private final GuildListener guildListener;
	
	public Helper(JDA botJDA, DBUtil dbUtil, LogListener logListener, final String token) throws Exception {
		this.botJDA = botJDA;
		this.db = dbUtil;
		this.logListener = logListener;

		guildListener = new GuildListener(this);
		
		JDABuilder helperBuilder = JDABuilder.createLight(token).setActivity(Activity.streaming("Слежу за вами", "https://www.youtube.com/watch?v=rpAQib0T5v0"))
			.addEventListeners(guildListener);
		this.JDA = helperBuilder.build();
	}

	public JDA getJDA() {
		return JDA;
	}

	public JDA getBotJDA() {
		return botJDA;
	}

	public DBUtil getDBUtil() {
		return db;
	}

	public LogListener getLogListener() {
		return logListener;
	}

	public Logger getLogger() {
		return logger;
	}

	private void banUser(Integer groupId, Guild master, User user, String reason) {
		List<String> guildIds = db.group.getGroupGuildIds(groupId, false);
		if (guildIds.isEmpty()) return;

		Integer maxCount = guildIds.size();
		List<String> success = new ArrayList<>();
		for (int i = 0; i < maxCount; i++) {
			Boolean last = i + 1 == maxCount;
			Guild guild = getJDA().getGuildById(guildIds.get(i));
			if (guild == null) {
				if (last) logListener.helperOnSyncBan(groupId, master, user, reason, success.size(), maxCount);
				continue;
			};
			// fail-safe check if has expirable ban (to prevent auto unban)
			Map<String, Object> banData = db.ban.getMemberExpirable(user.getId(), guild.getId());
			if (!banData.isEmpty()) {
				db.ban.setInactive((Integer) banData.get("banId"));
			}
			
			guild.ban(user, 0, TimeUnit.SECONDS).reason("Sync: "+reason).queue(
				done -> {
					success.add(guild.getId());
					if (last) logListener.helperOnSyncBan(groupId, master, user, reason, success.size(), maxCount);
				},
				failure -> {
					if (last) logListener.helperOnSyncBan(groupId, master, user, reason, success.size(), maxCount);
				}
			);
		}
	}

	private void unbanUser(Integer groupId, Guild master, User user, String reason) {
		List<String> guildIds = db.group.getGroupGuildIds(groupId, false);
		if (guildIds.isEmpty()) return;

		Integer maxCount = guildIds.size();
		List<String> success = new ArrayList<>();
		for (int i = 0; i < maxCount; i++) {
			Boolean last = i + 1 == maxCount;
			Guild guild = getJDA().getGuildById(guildIds.get(i));
			if (guild == null) {
				if (last) logListener.helperOnSyncBan(groupId, master, user, reason, success.size(), maxCount);
				continue;
			};
			
			guild.unban(user).reason("Sync: "+reason).queue(
				done -> {
					success.add(guild.getId());
					if (last) logListener.helperOnSyncUnban(groupId, master, user, reason, success.size(), maxCount);
				},
				failure -> {
					if (last) logListener.helperOnSyncUnban(groupId, master, user, reason, success.size(), maxCount);
				}
			);
		}
	}

	private void kickUser(Integer groupId, Guild master, User user, String reason) {
		List<String> guildIds = db.group.getGroupGuildIds(groupId, false);
		if (guildIds.isEmpty()) return;

		Integer maxCount = guildIds.size();
		List<String> success = new ArrayList<>();
		for (int i = 0; i < maxCount; i++) {
			Boolean last = i + 1 == maxCount;
			Guild guild = getJDA().getGuildById(guildIds.get(i));
			if (guild == null) {
				if (last) logListener.helperOnSyncBan(groupId, master, user, reason, success.size(), maxCount);
				continue;
			};
			
			guild.kick(user).reason("Sync: "+reason).queue(
				done -> {
					success.add(guild.getId());
					if (last) logListener.helperOnSyncKick(groupId, master, user, reason, success.size(), maxCount);
				},
				failure -> {
					if (last) logListener.helperOnSyncKick(groupId, master, user, reason, success.size(), maxCount);
				}
			);
		}
	}

	public void runBan(Integer groupId, Guild master, User user, String reason) {
		CompletableFuture.runAsync(() -> {
			banUser(groupId, master, user, reason);
		});
	}

	public void runUnban(Integer groupId, Guild master, User user, String reason) {
		CompletableFuture.runAsync(() -> {
			unbanUser(groupId, master, user, reason);
		});
	}

	public void runKick(Integer groupId, Guild master, User user, String reason) {
		CompletableFuture.runAsync(() -> {
			kickUser(groupId, master, user, reason);
		});
	}

}
