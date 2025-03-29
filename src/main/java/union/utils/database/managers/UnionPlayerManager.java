package union.utils.database.managers;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import union.App;
import union.objects.constants.Constants;
import union.utils.database.ConnectionUtil;
import union.utils.database.SqlDBBase;
import union.utils.encoding.EncodingUtil;
import union.utils.file.SettingsManager;

import static union.utils.CastUtil.castLong;

@SuppressWarnings("FieldCanBeLocal")
public class UnionPlayerManager extends SqlDBBase {
	private final String SAM_PLAYERS = "sam_players";
	private final String AXE_PLAYERS = "axe_players";
	private final SettingsManager settings;

	public UnionPlayerManager(ConnectionUtil cu, SettingsManager settings, String url, String user, String password) {
		super(cu, "%s?user=%s&password=%s".formatted(url, user, password));
		this.settings = settings;
	}

	public List<String> getPlayerRank(long guildId, @NotNull String steamId) {
		if (settings.isDbPlayerDisabled()) return List.of();
		// Find corresponding database
		Map<String, SettingsManager.GameServerInfo> servers = getServers(guildId);
		if (servers.isEmpty()) return List.of();
		// Get data from database tables
		List<String> data = new ArrayList<>(servers.size());
		for (String db : servers.keySet()) {
			String rank = selectOne(db, SAM_PLAYERS, "rank", "steamid", steamId);
			if (rank != null) data.add(rank);
		}
		return data;
	}

	public Long getPlayTime(long guildId, @NotNull String steamId) throws Exception {
		if (settings.isDbPlayerDisabled()) throw new Exception("Disabled.");
		// Find corresponding database
		Map<String, SettingsManager.GameServerInfo> servers = getServers(guildId);
		if (servers.isEmpty()) throw new Exception("Database not found.");
		// Get data from database tables
		Long playtime = null;
		for (String db : servers.keySet()) {
			Long time = castLong(selectOne(db, SAM_PLAYERS, "play_time", "steamid", steamId));
			if (time != null) playtime = playtime==null ? time : playtime+time;
		}
		return playtime;
	}

	public List<PlayerInfo> getPlayerInfo(long guildId, @NotNull String steamId) {
		if (settings.isDbPlayerDisabled()) return List.of();
		// Find corresponding database
		Map<String, SettingsManager.GameServerInfo> servers = getServers(guildId);
		if (servers.isEmpty()) return List.of();
		// Get data from database table
		Map<String, PlayerInfo> data = selectPlayerInfoList(servers, SAM_PLAYERS, steamId);
		List<PlayerInfo> newData = new ArrayList<>(servers.size());
		servers.forEach((db,info) -> {
			if (data.containsKey(db)) newData.add(data.get(db));
			else newData.add(new PlayerInfo(info));
		});
		return newData;
	}

	public boolean existsAxePlayer(long guildId, @NotNull String steamId) throws Exception {
		if (settings.isDbPlayerDisabled()) throw new Exception("Disabled.");
		// Find corresponding database
		Map<String, SettingsManager.GameServerInfo> servers = getServers(guildId);
		if (servers.isEmpty()) throw new Exception("Database not found.");
		// Get data from database table
		for (String db : servers.keySet()) {
			if (selectOne(db, AXE_PLAYERS, "steamid", "steamid", steamId) != null) return true;
		}
		return false;
	}

	public static class PlayerInfo {
		private final SettingsManager.GameServerInfo serverInfo;
		private final String rank;
		private final Long playedHours; // in hours

		public PlayerInfo(SettingsManager.GameServerInfo serverInfo) {
			this.serverInfo = serverInfo;
			this.rank = null;
			this.playedHours = null;
		}

		public PlayerInfo(SettingsManager.GameServerInfo serverInfo, String rank, Long playTimeSeconds) {
			this.serverInfo = serverInfo;
			this.rank = rank;
			this.playedHours = Math.floorDiv(playTimeSeconds, 3600);
		}

		public SettingsManager.GameServerInfo getServerInfo() {
			return serverInfo;
		}

		public String getRank() {
			return Objects.requireNonNullElse(rank, "-");
		}

		public Long getPlayTime() {
			return Objects.requireNonNullElse(playedHours, 0L);
		}

		public boolean exists() {
			return rank != null;
		}
	}

	@NotNull
	private Map<String, SettingsManager.GameServerInfo> getServers(long guildId) {
		return settings.getGameServers(guildId);
	}

	public void startPlayerBulkFetcher(InteractionHook hook, DiscordLocale locale, long guildId, EmbedBuilder builder, Set<String> steamIds) {
		new PlayerBulkFetcher(hook, locale, guildId, builder, steamIds).startFetching();
	}

	private class PlayerBulkFetcher {
		private final static Logger logger = LoggerFactory.getLogger(PlayerBulkFetcher.class);
		private final static int MAX_REQUESTS = 5;
		private final static int DELAY = 4;

		private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
		private final Set<String> steamIds;
		private final long guildId;
		private final DiscordLocale locale;
		private final EmbedBuilder embedBuilder;
		private final InteractionHook hook;
		private final StringBuilder results = new StringBuilder();

		public PlayerBulkFetcher(InteractionHook hook, DiscordLocale locale, long guildId, EmbedBuilder builder, Set<String> steamIds) {
			this.hook = hook;
			this.locale = locale;
			this.guildId = guildId;
			this.embedBuilder = builder;
			this.steamIds = steamIds;
		}

		public void startFetching() {
			final AtomicInteger counter = new AtomicInteger(0);
			final AtomicInteger lastUpdated = new AtomicInteger(0);
			final long startTime = System.currentTimeMillis();

			Runnable task = new Runnable() {
				private final Iterator<String> iterator = steamIds.iterator();

				@Override
				public void run() {
					if (!iterator.hasNext()) {
						scheduler.shutdown();
						File resultFile = createResultsFile();
						long elapsed = (System.currentTimeMillis() - startTime) / 1000;
						if (resultFile != null) {
							hook.editOriginalEmbeds(
								embedBuilder.setColor(Constants.COLOR_SUCCESS)
									.setDescription(
										App.getInstance().getLocaleUtil()
											.getLocalized(locale, "bot.verification.bulk-account.done")
											.formatted(elapsed)
									).build()
								).setFiles(FileUpload.fromData(
									resultFile, EncodingUtil.encodeBulkAccount(Instant.now().getEpochSecond())
								)).queue();
						} else {
							hook.editOriginalEmbeds(
								embedBuilder.setColor(Constants.COLOR_FAILURE)
									.setDescription(
										App.getInstance().getLocaleUtil()
											.getLocalized(locale, "bot.verification.bulk-account.failed")
									).build()
								).queue();
						}
						return;
					}
					List<CompletableFuture<Void>> futures = new ArrayList<>();
					for (int i = 0; i<MAX_REQUESTS && iterator.hasNext(); i++) {
						String steamId = iterator.next();
						futures.add(CompletableFuture.runAsync(() -> fetchPlayerInfo(steamId)));
					}
					counter.addAndGet(futures.size());
					CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((done, throwable) -> {
						int elapsed = (int) (System.currentTimeMillis() - startTime) / 1000;
						if (elapsed >= lastUpdated.get() + 10) {
							embedBuilder.appendDescription("\n> `%2d/%2d` %6s seconds".formatted(counter.get(), steamIds.size(), elapsed));
							hook.editOriginalEmbeds(embedBuilder.build()).queue();
							lastUpdated.set(elapsed);
						}
						scheduler.schedule(this, DELAY, TimeUnit.SECONDS);
					});
				}
			};

			scheduler.submit(task);
		}

		private void fetchPlayerInfo(String steamId) {
			try {
				List<PlayerInfo> data = getPlayerInfo(guildId, steamId);

				StringBuilder temp = new StringBuilder("### ")
					.append(steamId);
				data.stream()
					.filter(PlayerInfo::exists)
					.forEach(playerInfo -> temp.append("\n> ")
						.append(playerInfo.serverInfo.getTitle())
						.append(": ")
						.append(playerInfo.rank)
					);
				temp.append("\n\n");
				results.append(temp);
			} catch (Exception e) {
				logger.warn("Error fetching bulk player info for '{}'.", steamId, e);
			}
		}

		private File createResultsFile() {
			try {
				File file = File.createTempFile("bulk_account_results", ".txt");
				try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
					writer.append(results);
				}
				return file;
			} catch (IOException e) {
				logger.warn("Error creating bulk account results file.", e);
				return null;
			}
		}
	}
}
