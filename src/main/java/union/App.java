package union;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import union.base.command.CommandClient;
import union.base.command.CommandClientBuilder;
import union.base.waiter.EventWaiter;
import union.commands.guild.*;
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
import union.objects.constants.Constants;
import union.objects.constants.Links;
import union.services.CountingThreadFactory;
import union.services.ScheduledCheck;
import union.utils.CheckUtil;
import union.utils.ModerationUtil;
import union.utils.TicketUtil;
import union.utils.WebhookAppender;
import union.utils.database.DBUtil;
import union.utils.file.FileManager;
import union.utils.file.lang.LocaleUtil;
import union.utils.logs.LogEmbedUtil;
import union.utils.logs.LoggingUtil;
import union.utils.logs.WebhookLogger;
import union.utils.message.EmbedUtil;
import union.utils.message.MessageUtil;

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

public class App {
	
	private final Logger logger = (Logger) LoggerFactory.getLogger(App.class);

    private static Helper helper;

	public final String VERSION = Optional.ofNullable(App.class.getPackage().getImplementationVersion()).map(ver -> "v"+ver).orElse("DEVELOPMENT");

	public final JDA JDA;
	public final EventWaiter WAITER;
	private final CommandClient commandClient;

	private final FileManager fileManager = new FileManager();

    private final LoggingUtil logUtil;

    private final DBUtil dbUtil;
	private final MessageUtil messageUtil;
	private final EmbedUtil embedUtil;
	private final CheckUtil checkUtil;
	private final LocaleUtil localeUtil;
	private final LogEmbedUtil logEmbedUtil;
	private final TicketUtil ticketUtil;
	private final WebhookLogger webhookLogger;
	private final ModerationUtil moderationUtil;

	@SuppressWarnings("BusyWait")
	public App() {

		try {
			fileManager.addFile("config", "/config.json", Constants.DATA_PATH + "config.json")
				.addFile("database", "/server.db", Constants.DATA_PATH + "server.db")
				.addLang("en-GB")
				.addLang("ru");
		} catch (Exception ex) {
			logger.error("Error while interacting with File Manager", ex);
			System.exit(0);
		}
		
		// Define for default
		dbUtil		= new DBUtil(fileManager);
		localeUtil	= new LocaleUtil(this, DiscordLocale.ENGLISH_UK);
		messageUtil	= new MessageUtil(localeUtil);
		embedUtil	= new EmbedUtil(localeUtil);
		checkUtil	= new CheckUtil(this);
		ticketUtil	= new TicketUtil(this);
		logEmbedUtil = new LogEmbedUtil(localeUtil);
		logUtil		= new LoggingUtil(this);
		webhookLogger = new WebhookLogger(dbUtil);
		moderationUtil = new ModerationUtil(dbUtil, localeUtil);

		WAITER = new EventWaiter();
        GuildListener guildListener				= new GuildListener(this);
        InteractionListener interactionListener = new InteractionListener(this, WAITER);
        VoiceListener voiceListener				= new VoiceListener(this);
        MessageListener messageListener			= new MessageListener(this);
        MemberListener memberListener			= new MemberListener(this);
        ModerationListener moderationListener	= new ModerationListener(this);
        AuditListener auditListener				= new AuditListener(this);

        ScheduledExecutorService scheduledExecutor = new ScheduledThreadPoolExecutor(4, new CountingThreadFactory("UTB", "Scheduler", false));
        ScheduledCheck scheduledCheck = new ScheduledCheck(this);

		scheduledExecutor.scheduleAtFixedRate(scheduledCheck::timedChecks, 3, 10, TimeUnit.MINUTES);
		scheduledExecutor.scheduleAtFixedRate(scheduledCheck::regularChecks, 2, 3, TimeUnit.MINUTES);

		// Define a command client
		commandClient = new CommandClientBuilder()
			.setOwnerId(fileManager.getString("config", "owner-id"))
			.setServerInvite(Links.DISCORD)
			.setScheduleExecutor(scheduledExecutor)
			.setStatus(OnlineStatus.ONLINE)
			.setActivity(Activity.customStatus(">>>  /help  <<<"))
			.addSlashCommands(
				// guild
				new SetupCmd(this),
				new ModuleCmd(this, WAITER),
				new AccessCmd(this),
				new LogsCmd(this),
				new AutopunishCmd(this),
				new ThreadsCmd(this),
				// owner
				new ShutdownCmd(this),
				new EvalCmd(this),
				new GenerateListCmd(this),
				new ForceAccessCmd(this),
				// webhook
				new WebhookCmd(this),
				// moderation
				new BanCmd(this),
				new UnbanCmd(this),
				new KickCmd(this, WAITER),
				new SyncCmd(this, WAITER),
				new CaseCmd(this),
				new ReasonCmd(this),
				new DurationCmd(this),
				new GroupCmd(this, WAITER),
				new MuteCmd(this),
				new UnmuteCmd(this),
				new ModLogsCmd(this),
				new ModStatsCmd(this),
				new BlacklistCmd(this),
				// strikes
				new StrikeCmd(this),
				new DeleteStikeCmd(this, WAITER),
				new ClearStrikesCmd(this),
				new StrikesCmd(this),
				// other
				new PingCmd(this),
				new AboutCmd(this),
				new HelpCmd(this),
				new StatusCmd(this),
				// verify
				new VerifyPanelCmd(this),
				new VerifyRoleCmd(this),
				new VerifyCmd(this),
				new UnverifyCmd(this),
				new AccountCmd(this),
				new VerifyCheckCmd(this),
				// ticketing
				new RolesPanelCmd(this),
				new TicketCountCmd(this),
				new RolesSetupCmd(this),
				new TicketPanelCmd(this),
				new CloseCmd(this),
				new RcloseCmd(this),
				new AddUserCmd(this),
				new RemoveUserCmd(this),
				// voice
				new VoiceCmd(this),
				// roles
				new CheckRankCmd(this),
				new TempRoleCmd(this),
				new RoleCmd(this),
				new CheckServerCmd(this)
			)
			.addContextMenus(
				new AccountContext(this),
				new ReportContext(this),
				new ModlogsContext(this),
				new ActiveModlogsContext(this)
			)
			.setListener(new CommandListener(localeUtil))
			.setDevGuildIds(fileManager.getStringList("config", "dev-servers").toArray(new String[0]))
			.build();

		// Build
        AutoCompleteListener acListener = new AutoCompleteListener(commandClient, dbUtil);

		final Set<GatewayIntent> intents = Set.of(
			GatewayIntent.GUILD_EMOJIS_AND_STICKERS,
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
                    guildListener, memberListener, messageListener, moderationListener, voiceListener
			);

		JDA tempJda;

		int retries = 4; // how many times will it try to build
		int cooldown = 8; // in seconds; cooldown amount, will doubles after each retry
		while (true) {
			try {
				tempJda = mainBuilder.build();
				break;
			} catch (InvalidTokenException ex) {
				logger.error("Login failed due to Token", ex);
				System.exit(0);
			} catch (ErrorResponseException ex) { // Tries to reconnect to discord x times with some delay, else exits
				if (retries > 0) {
					retries--;
                    logger.info("Retrying connecting in {} seconds... {} more attempts", cooldown, retries);
					try {
						Thread.sleep(cooldown*1000L);
					} catch (InterruptedException e) {
						logger.error("Thread sleep interrupted", e);
					}
					cooldown*=2;
				} else {
					logger.error("No network connection or couldn't connect to DNS", ex);
					System.exit(0);
				}
			}
		}

		this.JDA = tempJda;
	}

	public CommandClient getClient() {
		return commandClient;
	}

	public Logger getAppLogger() {
		return logger;
	}

	public FileManager getFileManager() {
		return fileManager;
	}

	public DBUtil getDBUtil() {
		return dbUtil;
	}

	public MessageUtil getMessageUtil() {
		return messageUtil;
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
 
	public WebhookLogger getGuildLogger() {
		return webhookLogger;
	}

	public ModerationUtil getModerationUtil() {
		return moderationUtil;
	}

	public Helper getHelper() {
		return helper;
	}

	public static void main(String[] args) {
        App instance = new App();
		instance.createWebhookAppender();
		instance.logger.info("Success start");

		try {
			helper = new Helper(instance, instance.getFileManager().getNullableString("config", "helper-token"));
			helper.getLogger().info("Helper started");
		} catch (Throwable ex) {
			instance.logger.info("Was unable to start helper");
			helper = null;
		}
	}


	private void createWebhookAppender() {
		String url = getFileManager().getNullableString("config", "webhook");
		if (url == null) return;
		
		LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
		PatternLayoutEncoder ple = new PatternLayoutEncoder();
		ple.setPattern("%d{dd.MM.yyyy HH:mm:ss} [%thread] [%logger{0}] %msg%n");
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
