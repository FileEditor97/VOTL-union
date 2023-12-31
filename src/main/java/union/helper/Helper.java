package union.helper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
	private final JDA mainJDA;
	private final DBUtil db;
	private final LogListener logListener;
	private final Logger logger = (Logger) LoggerFactory.getLogger(Helper.class);

	private final GuildListener guildListener;
	
	public Helper(JDA mainJDA, DBUtil dbUtil, LogListener logListener, final String token) throws Exception {
		this.mainJDA = mainJDA;
		this.db = dbUtil;
		this.logListener = logListener;

		guildListener = new GuildListener(this);
		
		JDABuilder helperBuilder = JDABuilder.createLight(token).setActivity(Activity.streaming("Слежу за вами", "https://www.youtube.com/watch?v=nqQ0KkP0idc"))
			.addEventListeners(guildListener);
		this.JDA = helperBuilder.build();
	}

	public JDA getJDA() {
		return JDA;
	}

	public JDA getMainJDA() {
		return mainJDA;
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
		List<String> guildIds = new ArrayList<String>();
		for (String guildId : db.group.getGroupManagers(groupId)) {
			db.group.getOwnedGroups(guildId).forEach(subGroupId -> 
				guildIds.addAll(db.group.getGroupGuildIds(subGroupId, false))
			);
		}
		guildIds.addAll(db.group.getGroupGuildIds(groupId, false));
		if (guildIds.isEmpty()) return;

		Integer maxCount = guildIds.size();
		List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
		for (String guildId : guildIds) {
			Guild guild = getJDA().getGuildById(guildId);
			if (guild == null) continue;
			// fail-safe check if has expirable ban (to prevent auto unban)
			Map<String, Object> banData = db.ban.getMemberExpirable(user.getId(), guild.getId());
			if (!banData.isEmpty()) {
				db.ban.setInactive((Integer) banData.get("banId"));
			}
			completableFutures.add(guild.ban(user, 0, TimeUnit.SECONDS).reason("Sync: "+reason).submit().exceptionally(ex -> null));
		}

		CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0]))
			.whenComplete((done, exception) -> {
				if (exception != null) {
					logger.error("Bad thing at Helper ban logic", exception);
				} else {
					Integer banned = 0;
					for (CompletableFuture<Void> future : completableFutures) {
						if (!future.isCompletedExceptionally()) banned++;
					}
					logListener.mod.onHelperSyncBan(groupId, master, user, reason, banned, maxCount);
				}
			});
	}

	private void unbanUser(Integer groupId, Guild master, User user, String reason) {
		List<String> guildIds = new ArrayList<String>();
		for (String guildId : db.group.getGroupManagers(groupId)) {
			db.group.getOwnedGroups(guildId).forEach(subGroupId -> 
				guildIds.addAll(db.group.getGroupGuildIds(subGroupId, false))
			);
		}
		guildIds.addAll(db.group.getGroupGuildIds(groupId, false));
		if (guildIds.isEmpty()) return;

		Integer maxCount = guildIds.size();
		List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
		for (String guildId : guildIds) {
			Guild guild = getJDA().getGuildById(guildId);
			if (guild == null) continue;

			completableFutures.add(guild.unban(user).reason("Sync: "+reason).submit().exceptionally(ex -> null));
		}

		CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0]))
			.whenComplete((done, exception) -> {
				if (exception != null) {
					logger.error("Bad thing at Helper unban logic", exception);
				} else {
					Integer unbanned = 0;
					for (CompletableFuture<Void> future : completableFutures) {
						if (!future.isCompletedExceptionally()) unbanned++;
					}
					logListener.mod.onHelperSyncUnban(groupId, master, user, reason, unbanned, maxCount);
				}
			});
	}

	private void kickUser(Integer groupId, Guild master, User user, String reason) {
		List<String> guildIds = new ArrayList<String>();
		for (String guildId : db.group.getGroupManagers(groupId)) {
			db.group.getOwnedGroups(guildId).forEach(subGroupId -> 
				guildIds.addAll(db.group.getGroupGuildIds(subGroupId, false))
			);
		}
		guildIds.addAll(db.group.getGroupGuildIds(groupId, false));
		if (guildIds.isEmpty()) return;

		Integer maxCount = guildIds.size();
		List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
		for (String guildId : guildIds) {
			Guild guild = getJDA().getGuildById(guildId);
			if (guild == null) continue;

			completableFutures.add(guild.kick(user).reason("Sync: "+reason).submit().exceptionally(ex -> null));
		}

		CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0]))
			.whenComplete((done, exception) -> {
				if (exception != null) {
					logger.error("Bad thing at Helper unban logic", exception);
				} else {
					Integer kicked = 0;
					for (CompletableFuture<Void> future : completableFutures) {
						if (!future.isCompletedExceptionally()) kicked++;
					}
					logListener.mod.onHelperSyncKick(groupId, master, user, reason, kicked, maxCount);
				}
			});
	}

	public void runBan(Integer groupId, Guild master, User user, String reason) {
		CompletableFuture.runAsync(() -> {
			banUser(groupId, master, user, Optional.ofNullable(reason).orElse("none"));
		});
	}

	public void runUnban(Integer groupId, Guild master, User user, String reason) {
		CompletableFuture.runAsync(() -> {
			unbanUser(groupId, master, user, Optional.ofNullable(reason).orElse("none"));
		});
	}

	public void runKick(Integer groupId, Guild master, User user, String reason) {
		CompletableFuture.runAsync(() -> {
			kickUser(groupId, master, user, Optional.ofNullable(reason).orElse("none"));
		});
	}

}
