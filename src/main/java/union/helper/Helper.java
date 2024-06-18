package union.helper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.requests.GatewayIntent;
import union.App;
import union.objects.CaseType;
import union.utils.database.DBUtil;
import union.utils.file.lang.LocaleUtil;
import union.utils.logs.LoggingUtil;

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
	private final LoggingUtil logUtil;
	private final LocaleUtil localeUtil;
	private final Logger logger = (Logger) LoggerFactory.getLogger(Helper.class);

    public Helper(App instance, final String token) {
		this.mainJDA = instance.JDA;
		this.db = instance.getDBUtil();
		this.logUtil = instance.getLogger();
		this.localeUtil = instance.getLocaleUtil();

        GuildListener guildListener = new GuildListener(this);
		
		JDABuilder helperBuilder = JDABuilder.createLight(token)
			.setActivity(Activity.streaming("Слежу за вами", "https://www.youtube.com/watch?v=RWU3o_kDixc"))
			.enableIntents(GatewayIntent.GUILD_MEMBERS)
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

	public LoggingUtil getLogUtil() {
		return logUtil;
	}

	public LocaleUtil getLocaleUtil() {
		return localeUtil;
	}

	public Logger getLogger() {
		return logger;
	}

	private void banUser(int groupId, Guild executedGuild, User user, String reason) {
		List<Long> guildIds = new ArrayList<>();
		for (long guildId : db.group.getGroupManagers(groupId)) {
			for (int subGroupId : db.group.getOwnedGroups(guildId)) {
				guildIds.addAll(db.group.getGroupMembers(subGroupId));
			}
		}
		guildIds.addAll(db.group.getGroupMembers(groupId));
		if (guildIds.isEmpty()) return;

		final int maxCount = guildIds.size();
		List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
		final String newReason = "Sync #"+groupId+": "+reason;
		for (long guildId : guildIds) {
			Guild guild = getJDA().getGuildById(guildId);
			if (guild == null) continue;
			// fail-safe check if user has temporal ban (to prevent auto unban)
			db.cases.setInactiveByType(user.getIdLong(), guildId, CaseType.BAN);

			completableFutures.add(guild.ban(user, 0, TimeUnit.SECONDS).reason(newReason).submit());
		}

		CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0]))
			.whenComplete((done, exception) -> {
				int banned = 0;
				for (CompletableFuture<Void> future : completableFutures) {
					if (!future.isCompletedExceptionally()) banned++;
				}
				// Log in server where 
				logUtil.mod.onHelperSyncBan(groupId, executedGuild, user, reason, banned, maxCount);
			});
	}

	private void unbanUser(int groupId, Guild master, User user, String reason) {
		List<Long> guildIds = new ArrayList<>();
		for (long guildId : db.group.getGroupManagers(groupId)) {
			for (int subGroupId : db.group.getOwnedGroups(guildId)) {
				guildIds.addAll(db.group.getGroupMembers(subGroupId));
			}
		}
		guildIds.addAll(db.group.getGroupMembers(groupId));
		if (guildIds.isEmpty()) return;

		final int maxCount = guildIds.size();
		List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
		final String newReason = "Sync #"+groupId+": "+reason;
		for (long guildId : guildIds) {
			Guild guild = getJDA().getGuildById(guildId);
			if (guild == null) continue;
			// Remove temporal ban case
			db.cases.setInactiveByType(user.getIdLong(), guildId, CaseType.BAN);

			completableFutures.add(guild.unban(user).reason(newReason).submit());
		}

		CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0]))
			.whenComplete((done, exception) -> {
				int unbanned = 0;
				for (CompletableFuture<Void> future : completableFutures) {
					if (!future.isCompletedExceptionally()) unbanned++;
				}
				logUtil.mod.onHelperSyncUnban(groupId, master, user, reason, unbanned, maxCount);
			});
	}

	private void kickUser(int groupId, Guild master, User user, String reason) {
		List<Long> guildIds = new ArrayList<>();
		for (long guildId : db.group.getGroupManagers(groupId)) {
			for (int subGroupId : db.group.getOwnedGroups(guildId)) {
				guildIds.addAll(db.group.getGroupMembers(subGroupId));
			}
		}
		guildIds.addAll(db.group.getGroupMembers(groupId));
		if (guildIds.isEmpty()) return;

		final int maxCount = guildIds.size();
		List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
		final String newReason = "Sync #"+groupId+": "+reason;
		for (long guildId : guildIds) {
			Guild guild = getJDA().getGuildById(guildId);
			if (guild == null) continue;

			completableFutures.add(guild.kick(user).reason(newReason).submit());
		}

		CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0]))
			.whenComplete((done, exception) -> {
				int kicked = 0;
				for (CompletableFuture<Void> future : completableFutures) {
					if (!future.isCompletedExceptionally()) kicked++;
				}
				logUtil.mod.onHelperSyncKick(groupId, master, user, reason, kicked, maxCount);
			});
	}

	public void runBan(int groupId, Guild executedGuild, User user, String reason) {
		CompletableFuture.runAsync(() -> banUser(groupId, executedGuild, user, Optional.ofNullable(reason).orElse("none")));
	}

	public void runUnban(int groupId, Guild master, User user, String reason) {
		CompletableFuture.runAsync(() -> unbanUser(groupId, master, user, Optional.ofNullable(reason).orElse("none")));
	}

	public void runKick(int groupId, Guild master, User user, String reason) {
		CompletableFuture.runAsync(() -> kickUser(groupId, master, user, Optional.ofNullable(reason).orElse("none")));
	}

}
