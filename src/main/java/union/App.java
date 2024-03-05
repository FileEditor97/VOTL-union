package union;

import java.util.Optional;
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
import union.utils.LogUtil;
import union.utils.TicketUtil;
import union.utils.WebhookAppender;
import union.utils.database.DBUtil;
import union.utils.file.FileManager;
import union.utils.message.EmbedUtil;
import union.utils.message.LocaleUtil;
import union.utils.message.MessageUtil;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Message;
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

	private static App instance;
	private static Helper helper;

	public final String VERSION = Optional.ofNullable(App.class.getPackage().getImplementationVersion()).map(ver -> "v"+ver).orElse("DEVELOPMENT");

	public final JDA JDA;
	public final EventWaiter WAITER;
	private final CommandClient commandClient;

	private final FileManager fileManager = new FileManager();

	private final GuildListener guildListener;
	private final AutoCompleteListener acListener;
	private final InteractionListener interactionListener;
	private final VoiceListener voiceListener;
	private final MessageListener messagesListener;

	private final LogListener logListener;
	
	private final ScheduledExecutorService scheduledExecutor;
	private final ScheduledCheck scheduledCheck;
	
	private final DBUtil dbUtil;
	private final MessageUtil messageUtil;
	private final EmbedUtil embedUtil;
	private final CheckUtil checkUtil;
	private final LocaleUtil localeUtil;
	private final LogUtil logUtil;
	private final TicketUtil ticketUtil;

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
		localeUtil	= new LocaleUtil(this, "en-GB", DiscordLocale.ENGLISH_UK);
		messageUtil	= new MessageUtil(localeUtil);
		embedUtil	= new EmbedUtil(localeUtil);
		checkUtil	= new CheckUtil(this);
		logUtil		= new LogUtil(localeUtil);
		ticketUtil	= new TicketUtil(this);

		WAITER				= new EventWaiter();
		guildListener		= new GuildListener(this);
		logListener			= new LogListener(this);
		interactionListener	= new InteractionListener(this, WAITER);
		voiceListener		= new VoiceListener(this);
		messagesListener	= new MessageListener(this);

		scheduledExecutor	= new ScheduledThreadPoolExecutor(4, new CountingThreadFactory("UTB", "Scheduler", false));
		scheduledCheck		= new ScheduledCheck(this);

		scheduledExecutor.scheduleAtFixedRate(() -> scheduledCheck.timedChecks(), 3, 10, TimeUnit.MINUTES);
		scheduledExecutor.scheduleAtFixedRate(() -> scheduledCheck.regularChecks(), 2, 3, TimeUnit.MINUTES);

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
				new LogCmd(this, WAITER),
				new AutopunishCmd(this),
				// owner
				new ShutdownCmd(this),
				new EvalCmd(this),
				new GenerateListCmd(this),
				new ForceAccessCmd(this),
				// webhook
				new WebhookCmd(this),
				// moderation
				new BanCmd(this),
				new UnbanCmd(this, WAITER),
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
				new ReportContext(this)
			)
			.setListener(new CommandListener(localeUtil))
			.setDevGuildIds(fileManager.getStringList("config", "dev-servers").toArray(new String[0]))
			.build();

		// Build
		MemberCachePolicy policy = MemberCachePolicy.any(MemberCachePolicy.VOICE, MemberCachePolicy.OWNER);	// if in voice or server owner
		
		acListener = new AutoCompleteListener(commandClient, dbUtil);

		JDABuilder mainBuilder = JDABuilder.createLight(fileManager.getString("config", "bot-token"))
			.setEnabledIntents(
				GatewayIntent.GUILD_MEMBERS,			// required for updating member profiles and ChunkingFilter
				GatewayIntent.GUILD_MESSAGES,			// checks for verified
				GatewayIntent.GUILD_VOICE_STATES		// required for CF VOICE_STATE and CP VOICE
			)
			.setMemberCachePolicy(policy)
			.setChunkingFilter(ChunkingFilter.ALL)		// chunk all guilds
			.enableCache(
				CacheFlag.MEMBER_OVERRIDES,		// channel permission overrides
				CacheFlag.ROLE_TAGS,			// role search
				CacheFlag.VOICE_STATE			// get members voice status
			)
			.addEventListeners(commandClient, WAITER, guildListener, acListener, interactionListener, voiceListener, messagesListener);

		JDA jda = null;

		Integer retries = 4; // how many times will it try to build
		Integer cooldown = 8; // in seconds; cooldown amount, will doubles after each retry
		while (true) {
			try {
				jda = mainBuilder.build();
				break;
			} catch (InvalidTokenException ex) {
				logger.error("Login failed due to Token", ex);
				System.exit(0);
			} catch (ErrorResponseException ex) { // Tries to reconnect to discord x times with some delay, else exits
				if (retries > 0) {
					retries--;
					logger.info("Retrying connecting in "+cooldown+" seconds... "+retries+" more attempts");
					try {
						Thread.sleep(cooldown*1000);
					} catch (InterruptedException e) {
						logger.error("Thread sleep interupted", e);
					}
					cooldown*=2;
				} else {
					logger.error("No network connection or couldn't connect to DNS", ex);
					System.exit(0);
				}
			}
		}

		this.JDA = jda;
	}

	public CommandClient getClient() {
		return commandClient;
	}

	public Logger getLogger() {
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

	public LogUtil getLogUtil() {
		return logUtil;
	}

	public TicketUtil getTicketUtil() {
		return ticketUtil;
	}

	public LogListener getLogListener() {
		return logListener;
	}

	public Helper getHelper() {
		return helper;
	}

	public static void main(String[] args) {
		Message.suppressContentIntentWarning();

		instance = new App();
		instance.createWebhookAppender();
		instance.logger.info("Success start");

		try {
			helper = new Helper(instance.JDA, instance.getDBUtil(), instance.getLogListener(), instance.getFileManager().getNullableString("config", "helper-token"));
			helper.getLogger().info("Helper started");
		} catch (Exception ex) {
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
