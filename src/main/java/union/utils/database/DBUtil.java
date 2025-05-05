package union.utils.database;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import ch.qos.logback.classic.Level;
import union.App;
import union.utils.database.managers.*;
import union.utils.database.managers.GuildLogsManager.LogSettings;
import union.utils.database.managers.GuildSettingsManager.GuildSettings;
import union.utils.database.managers.TicketSettingsManager.TicketSettings;
import union.utils.database.managers.VerifySettingsManager.VerifySettings;
import union.utils.database.managers.GuildVoiceManager.VoiceSettings;
import union.utils.file.FileManager;

import net.dv8tion.jda.api.entities.Guild;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import union.utils.file.SettingsManager;

public class DBUtil {

	private final FileManager fileManager;
	private final ConnectionUtil connectionUtil;
	
	protected final Logger log = (Logger) LoggerFactory.getLogger(DBUtil.class);

	public final GuildSettingsManager guildSettings;
	public final WebhookManager webhook;
	public final AccessManager access;
	public final GroupManager group;
	public final VerifySettingsManager verifySettings;
	public final VerifyCacheManager verifyCache;
	public final TicketSettingsManager ticketSettings;
	public final TicketPanelManager panels;
	public final TicketTagManager tags;
	public final RoleManager role;
	public final TicketManager ticket;
	public final GuildVoiceManager guildVoice;
	public final UserSettingsManager user;
	public final VoiceChannelManager voice;
	public final TempRoleManager tempRole;
	public final CaseManager cases;
	public final StrikeManager strike;
	public final AutopunishManager autopunish;
	public final BlacklistManager blacklist;
	public final GuildLogsManager logs;
	public final LogExemptionManager logExemption;
	public final ModifyRoleManager modifyRole;
	public final ThreadControlManager threadControl;
	public final GameStrikeManager games;
	public final TempBanManager tempBan;
	public final ModReportManager modReport;
	public final PersistentManager persistent;
	public final CommentsManager comments;
	public final LevelManager levels;
	public final LevelRolesManager levelRoles;
	public final ConnectedRolesManager connectedRoles;
	
	public final UnionVerifyManager unionVerify;
	public final UnionPlayerManager unionPlayers;

	public final BanlistManager banlist;

	public DBUtil(FileManager fileManager, SettingsManager settings) {
		this.fileManager = fileManager;

		// Check if drivers are initiated
		try {
			Class.forName("org.sqlite.JDBC").getDeclaredConstructor().newInstance();
		} catch (Exception ex) {
			log.error("SQLite: Exiting!\nSQLite java client driver not found.\nPossibly, this OS/architecture is not supported or Driver has problems.", ex);
			System.exit(666);
		}
		try {
			Class.forName("org.mariadb.jdbc.Driver").getDeclaredConstructor().newInstance();
		} catch (Exception ex) {
			log.error("MariaDB: Exiting!\nMariaDB java client driver not found.\nPossibly, this OS/architecture is not supported or Driver has problems.", ex);
			System.exit(666);
		}

		String urlSQLite = "jdbc:sqlite:%s".formatted(fileManager.getFiles().get("database"));
		
		String urlWebsite = "jdbc:mariadb://%s:3306/union".formatted(fileManager.getNullableString("config", "website-ip"));
		String userWebsite = fileManager.getNullableString("config", "website-user");
		String passWebsite = fileManager.getNullableString("config", "website-pass");
		
		String urlCentralTemp = "jdbc:mariadb://%s:3306/".formatted(fileManager.getNullableString("config", "central-ip"));
		String userCentral = fileManager.getNullableString("config", "central-user");
		String passCentral = fileManager.getNullableString("config", "central-pass");
		
		this.connectionUtil = new ConnectionUtil(urlSQLite, log);

		updateDB();
		
		guildSettings = new GuildSettingsManager(connectionUtil);
		webhook = new WebhookManager(connectionUtil);
		access = new AccessManager(connectionUtil);
		group = new GroupManager(connectionUtil);
		verifySettings = new VerifySettingsManager(connectionUtil);
		verifyCache = new VerifyCacheManager(connectionUtil);
		ticketSettings = new TicketSettingsManager(connectionUtil);
		panels = new TicketPanelManager(connectionUtil);
		tags = new TicketTagManager(connectionUtil);
		role = new RoleManager(connectionUtil);
		ticket = new TicketManager(connectionUtil);
		guildVoice = new GuildVoiceManager(connectionUtil);
		user = new UserSettingsManager(connectionUtil);
		voice = new VoiceChannelManager(connectionUtil);
		tempRole = new TempRoleManager(connectionUtil);
		cases = new CaseManager(connectionUtil);
		strike = new StrikeManager(connectionUtil);
		autopunish = new AutopunishManager(connectionUtil);
		blacklist = new BlacklistManager(connectionUtil);
		logs = new GuildLogsManager(connectionUtil);
		logExemption = new LogExemptionManager(connectionUtil);
		modifyRole = new ModifyRoleManager(connectionUtil);
		threadControl = new ThreadControlManager(connectionUtil);
		games = new GameStrikeManager(connectionUtil);
		tempBan = new TempBanManager(connectionUtil);
		modReport = new ModReportManager(connectionUtil);
		persistent = new PersistentManager(connectionUtil);
		comments = new CommentsManager(connectionUtil);
		levels = new LevelManager(connectionUtil);
		levelRoles = new LevelRolesManager(connectionUtil);
		connectedRoles = new ConnectedRolesManager(connectionUtil);
		
		unionVerify = new UnionVerifyManager(connectionUtil, settings, urlWebsite, userWebsite, passWebsite);
		unionPlayers = new UnionPlayerManager(connectionUtil, settings, urlCentralTemp, userCentral, passCentral);

		urlSQLite = "jdbc:sqlite:%s".formatted(fileManager.getFiles().get("banlist"));
		Logger banlistLogger = (Logger) LoggerFactory.getLogger("Banlist manager");
		banlistLogger.setLevel(Level.INFO);
		ConnectionUtil banlistConnectionUtil = new ConnectionUtil(urlSQLite, banlistLogger);

		banlist = new BanlistManager(banlistConnectionUtil);
	}

	public VerifySettings getVerifySettings(Guild guild) {
		return verifySettings.getSettings(guild);
	}

	public GuildSettings getGuildSettings(Guild guild) {
		return guildSettings.getSettings(guild);
	}

	public GuildSettings getGuildSettings(long guildId) {
		return guildSettings.getSettings(guildId);
	}

	public LogSettings getLogSettings(Guild guild) {
		return logs.getSettings(guild.getIdLong());
	}

	public TicketSettings getTicketSettings(Guild guild) {
		return ticketSettings.getSettings(guild);
	}

	public VoiceSettings getVoiceSettings(Guild guild) {
		return guildVoice.getSettings(guild.getIdLong());
	}


	// 0 - no version or error
	// 1> - compare active db version with resources
	// if version lower -> apply instruction for creating new tables, adding/removing columns
	// in the end set active db version to resources
	public Integer getActiveDBVersion() {
		int version = 0;
		try (Connection conn = DriverManager.getConnection(connectionUtil.getUrlSQLite());
			PreparedStatement st = conn.prepareStatement("PRAGMA user_version")) {
			version = st.executeQuery().getInt(1);
		} catch(SQLException ex) {
			log.warn("SQLite: Failed to get active database version", ex);
		}
		return version;
	}

	public Integer getResourcesDBVersion() {
		int version = 0;
		try {
			File tempFile = File.createTempFile("local-", ".tmp");
			if (!fileManager.export(getClass().getResourceAsStream("/server.db"), tempFile.toPath())) {
				log.error("Failed to write temp file {}!", tempFile.getName());
				return version;
			} else {
				try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + tempFile.getAbsolutePath());
				PreparedStatement st = conn.prepareStatement("PRAGMA user_version")) {
					version = st.executeQuery().getInt(1);
				} catch(SQLException ex) {
					log.warn("Failed to get resources database version", ex);
				}
			}
			boolean ignored = tempFile.delete();
		} catch (IOException ioException) {
			log.error("Exception at version check\n", ioException);
		}
		return version;
	}

	private List<List<String>> loadInstructions(Integer activeVersion) {
		List<String> lines = new ArrayList<>();
		try {
			File tempFile = File.createTempFile("database_updates", ".tmp");
			if (!fileManager.export(App.class.getResourceAsStream("/database_updates"), tempFile.toPath())) {
				log.error("Failed to write instruction temp file {}!", tempFile.getName());
			} else {
				lines = Files.readAllLines(tempFile.toPath(), StandardCharsets.UTF_8);
			}
		} catch (Exception ex) {
			log.error("SQLite: Failed to open update file", ex);
		}
		lines = lines.subList(activeVersion - 1, lines.size());
		List<List<String>> result = new ArrayList<>();
		lines.forEach(line -> {
			String[] points = line.split(";");
			List<String> list = points.length == 0 ? List.of(line) : List.of(points);
			result.add(list);
		});
		return result;
	}

	private void updateDB() {
		// 0 - skip
		Integer newVersion = getResourcesDBVersion();
		if (newVersion == 0) return;
		Integer activeVersion = getActiveDBVersion();
		if (activeVersion == 0) return;

		if (newVersion > activeVersion) {
			try (
				Connection conn = DriverManager.getConnection(connectionUtil.getUrlSQLite());
				Statement st = conn.createStatement()
			) {
				conn.setAutoCommit(false);
				try {
					for (List<String> version : loadInstructions(activeVersion)) {
						for (String sql : version) {
							log.debug(sql);
							st.execute(sql);
						}
						conn.commit();
					}
				} catch (SQLException ex) {
					conn.rollback();
					throw ex; // rethrow
				}
			} catch(SQLException ex) {
				log.error("SQLite: Failed to execute update!\nRollback performed. Continue database update manually.\n{}", ex.getMessage());
				return;
			}
			
			// Update version
			try (Connection conn = DriverManager.getConnection(connectionUtil.getUrlSQLite());
			Statement st = conn.createStatement()) {
				st.execute("PRAGMA user_version = "+newVersion);
				log.info("SQLite: Database version updated to {}", newVersion);
			} catch(SQLException ex) {
				log.error("SQLite: Failed to set active database version", ex);
			}
		}
	}

}
