package union;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import union.commands.guild.*;
import union.commands.moderation.*;
import union.commands.other.*;
import union.commands.owner.*;
import union.commands.ticketing.RolesCmd;
import union.commands.ticketing.TicketCountCmd;
import union.commands.ticketing.RolePanelCmd;
import union.commands.verification.*;
import union.commands.webhook.WebhookCmd;
import union.listeners.*;
import union.menus.AccountContext;
import union.objects.command.CommandClient;
import union.objects.command.CommandClientBuilder;
import union.objects.constants.Constants;
import union.objects.constants.Links;
import union.services.ScheduledCheck;
import union.utils.*;
import union.utils.database.DBUtil;
import union.utils.file.FileManager;
import union.utils.file.lang.LangUtil;
import union.utils.message.*;

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

import com.jagrosh.jdautilities.commons.waiter.EventWaiter;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import union.helper.Helper;

public class App {
	
	private final Logger logger = (Logger) LoggerFactory.getLogger(App.class);

	private static App instance;
	private static Helper helper;

	public final String VERSION = Optional.ofNullable(App.class.getPackage().getImplementationVersion()).map(ver -> "v"+ver).orElse("DEVELOPMENT");

	public final JDA JDA;
	public final EventWaiter WAITER;

	private final FileManager fileManager = new FileManager();

	private final Random random = new Random();

	private final GuildListener guildListener;
	private final AutoCompleteListener acListener;
	private final InteractionListener interactionListener;
	private final MessageListener messagesListener;
	private final CommandListener commandListener;

	private final LogListener logListener;
	
	private final ScheduledExecutorService executorService;
	private final ScheduledCheck scheduledCheck;
	
	private final DBUtil dbUtil;
	private final MessageUtil messageUtil;
	private final EmbedUtil embedUtil;
	private final LangUtil langUtil;
	private final CheckUtil checkUtil;
	private final LocaleUtil localeUtil;
	private final TimeUtil timeUtil;
	private final LogUtil logUtil;
	private final SteamUtil steamUtil;
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
		dbUtil		= new DBUtil(getFileManager());
		langUtil	= new LangUtil(this);
		localeUtil	= new LocaleUtil(this, langUtil, "en-GB", DiscordLocale.ENGLISH_UK);
		messageUtil	= new MessageUtil(this);
		embedUtil	= new EmbedUtil(localeUtil);
		checkUtil	= new CheckUtil(this);
		timeUtil	= new TimeUtil(this);
		logUtil		= new LogUtil(this);
		steamUtil	= new SteamUtil();
		ticketUtil	= new TicketUtil(this);

		WAITER				= new EventWaiter();
		guildListener		= new GuildListener(this);
		logListener			= new LogListener(this);
		interactionListener	= new InteractionListener(this, WAITER);
		messagesListener	= new MessageListener(this);
		commandListener		= new CommandListener();

		executorService = Executors.newScheduledThreadPool(4, r -> (new Thread(r, "UTB Scheduler")));
		scheduledCheck	= new ScheduledCheck(this);
		
		executorService.scheduleAtFixedRate(() -> scheduledCheck.moderationChecks(), 5, 10, TimeUnit.MINUTES);
		executorService.scheduleAtFixedRate(() -> scheduledCheck.regularChecks(), 1, 2, TimeUnit.MINUTES);

		// Define a command client
		CommandClient commandClient = new CommandClientBuilder()
			.setOwnerId(fileManager.getString("config", "owner-id"))
			.setServerInvite(Links.DISCORD)
			.useHelpBuilder(false)
			.setScheduleExecutor(executorService)
			.setStatus(OnlineStatus.ONLINE)
			.setActivity(Activity.watching("/help"))
			.addSlashCommands(
				// guild
				new SetupCmd(this),
				new ModuleCmd(this, WAITER),
				new AccessCmd(this),
				new LogCmd(this, WAITER),
				// owner
				new ShutdownCmd(this),
				new EvalCmd(this),
				new InviteCmd(this),
				new GenerateListCmd(this),
				// webhook
				new WebhookCmd(this),
				// moderation
				new BanCmd(this, WAITER),
				new UnbanCmd(this, WAITER),
				new KickCmd(this, WAITER),
				new SyncCmd(this, WAITER),
				new CaseCmd(this),
				new ReasonCmd(this),
				new DurationCmd(this),
				new GroupCmd(this, WAITER),
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
				new RolePanelCmd(this),
				new TicketCountCmd(this),
				new RolesCmd(this)
			)
			.addContextMenus(
				new AccountContext(this)
			)
			.setListener(commandListener)
			.setDevGuildIds(fileManager.getStringList("config", "dev-servers").toArray(new String[0]))
			.build();

		// Build
		MemberCachePolicy policy = MemberCachePolicy.any(MemberCachePolicy.OWNER);	// if pending or server owner
		
		acListener = new AutoCompleteListener(commandClient, dbUtil);

		JDABuilder mainBuilder = JDABuilder.createLight(fileManager.getString("config", "bot-token"))
			.setEnabledIntents(
				GatewayIntent.GUILD_MEMBERS,			// required for updating member profiles and ChunkingFilter
				GatewayIntent.GUILD_MESSAGES			// checks for verified
			)
			.setMemberCachePolicy(policy)
			.setChunkingFilter(ChunkingFilter.ALL)		// chunk all guilds
			.enableCache(
				CacheFlag.MEMBER_OVERRIDES,		// channel permission overrides
				CacheFlag.ROLE_TAGS				// role search
			)
			.addEventListeners(commandClient, WAITER, guildListener, acListener, interactionListener, messagesListener);

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
					logger.info("Retrying connecting in "+cooldown+" seconds..."+retries+" more attempts");
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

	public Logger getLogger() {
		return logger;
	}

	public FileManager getFileManager() {
		return fileManager;
	}

	public Random getRandom() {
		return random;
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

	public TimeUtil getTimeUtil() {
		return timeUtil;
	}

	public LogUtil getLogUtil() {
		return logUtil;
	}

	public SteamUtil getSteamUtil() {
		return steamUtil;
	}

	public TicketUtil getTicketUtil() {
		return ticketUtil;
	}

	public LogListener getLogListener() {
		return logListener;
	}

	public String lastAccountCheck() {
		return Timestamp.from(scheduledCheck.lastAccountCheck).toString();
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
		String url = fileManager.getNullableString("config", "webhook");
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
