package union;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import union.base.command.CommandClient;
import union.base.command.CommandClientBuilder;
import union.base.waiter.EventWaiter;
import union.commands.games.GameCmd;
import union.commands.games.GameStrikeCmd;
import union.commands.guild.*;
import union.commands.level.LeaderboardCmd;
import union.commands.level.UserProfileCmd;
import union.commands.moderation.*;
import union.commands.other.*;
import union.commands.owner.*;
import union.commands.roles.*;
import union.commands.strikes.*;
import union.commands.ticketing.*;
import union.commands.verification.*;
import union.commands.voice.VoiceCmd;
import union.commands.webhook.WebhookCmd;
import union.helper.Helper;
import union.listeners.*;
import union.menus.*;
import union.metrics.Metrics;
import union.objects.constants.Constants;
import union.services.CountingThreadFactory;
import union.services.ScheduledCheck;
import union.services.ScheduledMetrics;
import union.utils.*;
import union.utils.database.DBUtil;
import union.utils.file.FileManager;
import union.utils.file.SettingsManager;
import union.utils.file.lang.LocaleUtil;
import union.utils.imagegen.UserBackgroundHandler;
import union.utils.level.LevelUtil;
import union.utils.logs.LogEmbedUtil;
import union.utils.logs.LoggingUtil;
import union.utils.message.EmbedUtil;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;

import static java.lang.Long.parseLong;

public class App {
	protected static App instance;
	
	private final Logger LOG = (Logger) LoggerFactory.getLogger(App.class);

	public final String VERSION = Optional.ofNullable(App.class.getPackage().getImplementationVersion()).map(ver -> "v"+ver).orElse("DEVELOPMENT");

	public final JDA JDA;
	private final CommandClient commandClient;

	private final FileManager fileManager = new FileManager();

    private final LoggingUtil logUtil;

	private final SettingsManager settings;
    private final DBUtil dbUtil;
	private final LocaleUtil localeUtil;

	private final EmbedUtil embedUtil;
	private final CheckUtil checkUtil;
	private final LogEmbedUtil logEmbedUtil;
	private final TicketUtil ticketUtil;
	private final ModerationUtil moderationUtil;
	private final LevelUtil levelUtil;
	private final AlertUtil alertUtil;

	@SuppressWarnings("BusyWait")
	public App() {
		App.instance = this;

		try {
			fileManager.addFile("config", "/config.json", Constants.DATA_PATH + "config.json")
				.addFile("database", "/server.db", Constants.DATA_PATH + "server.db")
				.addFile("banlist", "/banlist.db", Constants.DATA_PATH + "banlist.db")
				.addFileUpdate("backgrounds", "/backgrounds/index.json", Constants.DATA_PATH+"backgrounds"+Constants.SEPAR+"main.json")
				.addSettings("settings", Constants.DATA_PATH + "settings.json")
				.addLang("en-GB")
				.addLang("ru");
		} catch (Exception ex) {
			System.out.println(ex.getMessage());
			System.exit(0);
		}

		final long ownerId = parseLong(fileManager.getString("config", "owner-id"));
		
		// Define for default
		settings	= new SettingsManager(fileManager);
		dbUtil		= new DBUtil(fileManager, settings);
		localeUtil	= new LocaleUtil(this, DiscordLocale.ENGLISH_UK);

		embedUtil	= new EmbedUtil(localeUtil);
		checkUtil	= new CheckUtil(this, ownerId);
		ticketUtil	= new TicketUtil(this);
		logEmbedUtil = new LogEmbedUtil(localeUtil);
		logUtil		= new LoggingUtil(this);
		moderationUtil = new ModerationUtil(dbUtil, localeUtil);
		levelUtil	= new LevelUtil(this);
		alertUtil	= new AlertUtil();

		ScheduledExecutorService scheduledExecutor = new ScheduledThreadPoolExecutor(4, new CountingThreadFactory("UTB", "Scheduler", false));

		final EventWaiter WAITER = new EventWaiter();

        GuildListener guildListener				= new GuildListener(this);
        InteractionListener interactionListener = new InteractionListener(this, WAITER);
        VoiceListener voiceListener				= new VoiceListener(this, scheduledExecutor);
		MessageListener messageListener 		= new MessageListener(this);
        MemberListener memberListener			= new MemberListener(this);
        ModerationListener moderationListener	= new ModerationListener(this);
        AuditListener auditListener				= new AuditListener(this);
		EventListener eventListener				= new EventListener(dbUtil);

		ScheduledCheck scheduledCheck = new ScheduledCheck(this);
		scheduledExecutor.scheduleAtFixedRate(scheduledCheck::timedChecks, 3, 10, TimeUnit.MINUTES);
		scheduledExecutor.scheduleAtFixedRate(scheduledCheck::regularChecks, 2, 3, TimeUnit.MINUTES);

		ScheduledMetrics scheduledMetrics = new ScheduledMetrics(this);
		scheduledExecutor.scheduleAtFixedRate(scheduledMetrics::recordMetrics, 5, 5, TimeUnit.MINUTES);

		// Define a command client
		commandClient = new CommandClientBuilder()
			.setOwnerId(ownerId)
			.setScheduleExecutor(scheduledExecutor)
			.setStatus(OnlineStatus.ONLINE)
			.setActivity(Activity.customStatus("/about | unionteams.ru"))
			.addSlashCommands(
				// guild
				new SetupCmd(),
				new ModuleCmd(WAITER),
				new AccessCmd(),
				new LogsCmd(),
				new AutopunishCmd(),
				new ThreadsCmd(),
				new PersistentRoleCmd(),
				new ConnectedRoleCmd(),
				// owner
				new ShutdownCmd(),
				new EvalCmd(),
				new ForceAccessCmd(),
				new SettingsCmd(),
				new DebugCmd(),
				new MessageCmd(),
				new ViewMetricsCmd(),
				new SetStatusCmd(),
				new ImportBanlistCmd(),
				new CheckAccessCmd(),
				new ExperienceCmd(),
				// webhook
				new WebhookCmd(),
				// moderation
				new BanCmd(),
				new UnbanCmd(),
				new KickCmd(),
				new SyncCmd(WAITER),
				new CaseCmd(),
				new ReasonCmd(),
				new DurationCmd(),
				new GroupCmd(WAITER),
				new MuteCmd(),
				new UnmuteCmd(),
				new ModLogsCmd(),
				new ModStatsCmd(),
				new ModReportCmd(),
				new BlacklistCmd(),
				new PurgeCmd(),
				// strikes
				new StrikeCmd(),
				new DeleteStikeCmd(WAITER),
				new ClearStrikesCmd(),
				new StrikesCmd(),
				// other
				new PingCmd(),
				new AboutCmd(),
				new HelpCmd(),
				new StatusCmd(),
				// verify
				new VerifyPanelCmd(),
				new VerifyRoleCmd(),
				new VerifyCmd(),
				new UnverifyCmd(),
				new AccountCmd(),
				new VerifyCheckCmd(),
				new CommentCmd(),
				new BulkAccountCmd(WAITER),
				// ticketing
				new RolesPanelCmd(),
				new TicketCountCmd(),
				new RolesSetupCmd(),
				new TicketCmd(),
				new CloseCmd(),
				new RcloseCmd(),
				new AddUserCmd(),
				new RemoveUserCmd(),
				// voice
				new VoiceCmd(),
				// roles
				new CheckRankCmd(),
				new TempRoleCmd(WAITER),
				new RoleCmd(),
				new CheckServerCmd(),
				// games
				new GameCmd(),
				new GameStrikeCmd(),
				// level
				new UserProfileCmd(),
				new LeaderboardCmd()
			)
			.addContextMenus(
				new AccountContext(),
				new ReportContext(),
				new ModlogsContext(),
				new ActiveModlogsContext()
			)
			.setListener(new CommandListener(localeUtil))
			.setDevGuildIds(fileManager.getStringList("config", "dev-servers").toArray(new String[0]))
			.build();

		// Build
        AutoCompleteListener acListener = new AutoCompleteListener(commandClient, dbUtil);

		final Set<GatewayIntent> intents = Set.of(
			GatewayIntent.GUILD_EXPRESSIONS,
			GatewayIntent.GUILD_INVITES,
			GatewayIntent.GUILD_MEMBERS,
			GatewayIntent.GUILD_MESSAGES,
			GatewayIntent.GUILD_MODERATION,
			GatewayIntent.GUILD_VOICE_STATES,
			GatewayIntent.GUILD_WEBHOOKS,
			GatewayIntent.MESSAGE_CONTENT
		);
		final Set<CacheFlag> enabledCacheFlags = Set.of(
			CacheFlag.EMOJI,
			CacheFlag.MEMBER_OVERRIDES,
			CacheFlag.STICKER,
			CacheFlag.ROLE_TAGS,
			CacheFlag.VOICE_STATE
		);
		final Set<CacheFlag> disabledCacheFlags = Set.of(
			CacheFlag.ACTIVITY,
			CacheFlag.CLIENT_STATUS,
			CacheFlag.ONLINE_STATUS,
			CacheFlag.SCHEDULED_EVENTS
		);

		JDABuilder mainBuilder = JDABuilder.create(fileManager.getString("config", "bot-token"), intents)
			.setMemberCachePolicy(MemberCachePolicy.ALL)	// cache all members
			.setChunkingFilter(ChunkingFilter.ALL)		// chunk all guilds
			.enableCache(enabledCacheFlags)
			.disableCache(disabledCacheFlags)
			.setBulkDeleteSplittingEnabled(false)
			.addEventListeners(
				commandClient, WAITER, acListener, auditListener, interactionListener,
				guildListener, memberListener, messageListener, moderationListener, voiceListener,
				eventListener
			);

		JDA tempJda;

		// Try to log in
		int retries = 4; // how many times will it try to build
		int cooldown = 8; // in seconds; cooldown amount, will doubles after each retry
		while (true) {
			try {
				tempJda = mainBuilder.build();
				break;
			} catch (InvalidTokenException ex) {
				LOG.error("Login failed due to Token", ex);
				System.exit(0);
			} catch (ErrorResponseException ex) { // Tries to reconnect to discord x times with some delay, else exits
				if (retries > 0) {
					retries--;
                    LOG.info("Retrying connecting in {} seconds... {} more attempts", cooldown, retries);
					try {
						Thread.sleep(cooldown*1000L);
					} catch (InterruptedException e) {
						LOG.error("Thread sleep interrupted", e);
					}
					cooldown*=2;
				} else {
					LOG.error("No network connection or couldn't connect to DNS", ex);
					System.exit(0);
				}
			}
		}

		this.JDA = tempJda;

		createWebhookAppender();

		LOG.info("Preparing and setting up metrics.");
		Metrics.setup();

		LOG.info("Creating user backgrounds...");
		try {
			UserBackgroundHandler.getInstance().start();
		} catch (Throwable ex) {
			LOG.error("Error starting background handler", ex);
		}

		LOG.info("Starting helper...");
		try {
			Helper.start();
		} catch (Throwable ex) {
			LOG.info("Unable to start helper: {}", ex.getMessage());
		}

		LOG.info("Success start");
	}

	public static App getInstance() {
		return instance;
	}

	public CommandClient getClient() {
		return commandClient;
	}

	public Logger getAppLogger() {
		return LOG;
	}

	public FileManager getFileManager() {
		return fileManager;
	}

	public DBUtil getDBUtil() {
		return dbUtil;
	}

	public EmbedUtil getEmbedUtil() {
		return embedUtil;
	}

	public CheckUtil getCheckUtil() {
		return checkUtil;
	}

	public LocaleUtil getLocaleUtil() {
		return localeUtil;
	}

	public LogEmbedUtil getLogEmbedUtil() {
		return logEmbedUtil;
	}

	public LoggingUtil getLogger() {
		return logUtil;
	}

	public TicketUtil getTicketUtil() {
		return ticketUtil;
	}

	public ModerationUtil getModerationUtil() {
		return moderationUtil;
	}

	public SettingsManager getSettings() {
		return settings;
	}

	public LevelUtil getLevelUtil() {
		return levelUtil;
	}

	public AlertUtil getAlertUtil() {
		return alertUtil;
	}

	public void shutdownUtils() {
		// ignore
	}

	private void createWebhookAppender() {
		String url = getFileManager().getNullableString("config", "webhook");
		if (url == null) return;
		
		LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
		PatternLayoutEncoder ple = new PatternLayoutEncoder();
		ple.setPattern("%d{dd.MM.yyyy HH:mm:ss} [%thread] [%logger{0}] %ex{10}%n");
		ple.setContext(lc);
		ple.start();
		WebhookAppender webhookAppender = new WebhookAppender();
		webhookAppender.setUrl(url);
		webhookAppender.setEncoder(ple);
		webhookAppender.setContext(lc);
		webhookAppender.start();

		Logger logbackLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		logbackLogger.addAppender(webhookAppender);
		logbackLogger.setAdditive(false);
	}
}
