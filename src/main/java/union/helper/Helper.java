package union.helper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.jetbrains.annotations.NotNull;
import union.App;
import union.objects.CaseType;
import union.utils.database.DBUtil;
import union.utils.file.lang.LocaleUtil;
import union.utils.logs.LoggingUtil;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;

public class Helper {

	private static Helper instance;
	private final Logger logger = (Logger) LoggerFactory.getLogger(Helper.class);

	private final JDA JDA;
	private final DBUtil db;
	private final LoggingUtil logUtil;

    private Helper() {
		this.db = App.getInstance().getDBUtil();
		this.logUtil = App.getInstance().getLogger();

        GuildListener guildListener = new GuildListener(this);
		
		JDABuilder helperBuilder = JDABuilder.createLight(
				App.getInstance().getFileManager().getNullableString("config", "helper-token")
			)
			.setActivity(Activity.customStatus("Слежу за собой \uD83D\uDE22"))
			.enableIntents(GatewayIntent.GUILD_MEMBERS)
			.addEventListeners(guildListener);

        this.JDA = helperBuilder.build();
		logger.info("Helper started");
    }

	public static Helper getInstance() {
		return instance;
	}

	public static void start() {
		instance = new Helper();
	}

	public JDA getJDA() {
		return JDA;
	}

	public JDA getMainJDA() {
		return App.getInstance().JDA;
	}

	public DBUtil getDBUtil() {
		return db;
	}

	public LoggingUtil getLogUtil() {
		return logUtil;
	}

	public LocaleUtil getLocaleUtil() {
		return App.getInstance().getLocaleUtil();
	}

	public Logger getLogger() {
		return logger;
	}

	private void banUser(int groupId, @NotNull Guild master, @NotNull User user, @NotNull String reason, @NotNull User mod) {
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
		final String newReason = "Sync #%s: %s, by %s".formatted(groupId, reason, mod.getName());
		for (long guildId : guildIds) {
			Guild guild = JDA.getGuildById(guildId);
			if (guild == null) continue;
			// fail-safe check if user has temporal ban (to prevent auto unban)
			if (getMainJDA().getGuildById(guildId) != null)
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
				logUtil.mod.onHelperSyncBan(groupId, master, user, reason, banned, maxCount);
			});
	}

	private void unbanUser(int groupId, @NotNull Guild master, @NotNull User user, @NotNull String reason, @NotNull User mod) {
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
		final String newReason = "Sync #%s: %s, by %s".formatted(groupId, reason, mod.getName());
		for (long guildId : guildIds) {
			Guild guild = JDA.getGuildById(guildId);
			if (guild == null) continue;
			// Remove temporal ban case
			if (getMainJDA().getGuildById(guildId) != null)
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

	private void kickUser(int groupId, @NotNull Guild master, @NotNull User user, @NotNull String reason, @NotNull User mod) {
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
		final String newReason = "Sync #%s: %s, by %s".formatted(groupId, reason, mod.getName());
		for (long guildId : guildIds) {
			Guild guild = JDA.getGuildById(guildId);
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

	public void runBan(int groupId, Guild master, User user, @NotNull String reason, User mod) {
		CompletableFuture.runAsync(() -> banUser(groupId, master, user, reason, mod));
	}

	public void runUnban(int groupId, Guild master, User user, @NotNull String reason, User mod) {
		CompletableFuture.runAsync(() -> unbanUser(groupId, master, user, reason, mod));
	}

	public void runKick(int groupId, Guild master, User user, @NotNull String reason, User mod) {
		CompletableFuture.runAsync(() -> kickUser(groupId, master, user, reason, mod));
	}

	public void unban(long guildId, long userId, String reason) {
		Guild guild = JDA.getGuildById(guildId);
		if (guild == null) {
			logger.warn("Guild not found by ID '{}'", guildId);
			return;
		}
		guild.unban(UserSnowflake.fromId(userId)).reason(reason).queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_BAN));
	}

	public void removeRoles(long targetId, List<Long> roleIds, Guild guild) {
		UserSnowflake target = UserSnowflake.fromId(targetId);
		String reason = "from %s".formatted(guild.getName());

		roleIds.forEach(mainRoleId -> {
			db.connectedRoles.getConnectedRoles(mainRoleId).forEach(groupRoleId -> {
				Role role = JDA.getRoleById(groupRoleId);
				if (role == null) return;
				try {
					role.getGuild().removeRoleFromMember(target, role).reason(reason).queue(null, (ignored) -> {});
				} catch (Exception ignored) {}
			});
		});
	}

}
