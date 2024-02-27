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
import java.util.Arrays;
import java.util.List;

import union.App;
import union.utils.database.managers.AccessManager;
import union.utils.database.managers.AlertsManager;
import union.utils.database.managers.AutopunishManager;
import union.utils.database.managers.BlacklistManager;
import union.utils.database.managers.CaseManager;
import union.utils.database.managers.GroupManager;
import union.utils.database.managers.GuildSettingsManager;
import union.utils.database.managers.GuildVoiceManager;
import union.utils.database.managers.ModuleManager;
import union.utils.database.managers.RoleManager;
import union.utils.database.managers.StrikeManager;
import union.utils.database.managers.TempRoleManager;
import union.utils.database.managers.TicketManager;
import union.utils.database.managers.TicketPanelManager;
import union.utils.database.managers.TicketTagManager;
import union.utils.database.managers.UnionPlayerManager;
import union.utils.database.managers.UnionVerifyManager;
import union.utils.database.managers.UserSettingsManager;
import union.utils.database.managers.TicketSettingsManager;
import union.utils.database.managers.VerifyCacheManager;
import union.utils.database.managers.VerifyManager;
import union.utils.database.managers.VoiceChannelManager;
import union.utils.database.managers.WebhookManager;
import union.utils.file.FileManager;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;

public class DBUtil {

	private final FileManager fileManager;
	private final ConnectionUtil connectionUtil;
	
	protected final Logger logger = (Logger) LoggerFactory.getLogger(DBUtil.class);

	public final GuildSettingsManager guild;
	public final WebhookManager webhook;
	public final ModuleManager module;
	public final AccessManager access;
	public final GroupManager group;
	public final VerifyManager verify;
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
	public final AlertsManager alerts;
	
	public final UnionVerifyManager unionVerify;
	public final UnionPlayerManager unionPlayers;

	public DBUtil(FileManager fileManager) {
		this.fileManager = fileManager;

		// Check if MySQL driver is initiated
		try {
			Class.forName("com.mysql.cj.jdbc.Driver").getDeclaredConstructor().newInstance();
		} catch (Exception ex) {
			logger.error("MySQL: Exiting!\nMySQL-J driver not found initiated.\nPossibly, this OS/architecture is not supported or Driver has problems.", ex);
			System.exit(666);
		}

		String urlSQLite = "jdbc:sqlite:%s".formatted(fileManager.getFiles().get("database"));
		
		String urlWebsite = "jdbc:mysql://%s:3306/union".formatted(fileManager.getNullableString("config", "website-ip"));
		String userWebsite = fileManager.getNullableString("config", "website-user");
		String passWebsite = fileManager.getNullableString("config", "website-pass");
		
		String urlCentralTemp = "jdbc:mysql://%s:3306/".formatted(fileManager.getNullableString("config", "central-ip"));
		String userCentral = fileManager.getNullableString("config", "central-user");
		String passCentral = fileManager.getNullableString("config", "central-pass");
		
		this.connectionUtil = new ConnectionUtil(urlSQLite, logger);
		
		guild = new GuildSettingsManager(connectionUtil);
		webhook = new WebhookManager(connectionUtil);
		module = new ModuleManager(connectionUtil);
		access = new AccessManager(connectionUtil);
		group = new GroupManager(connectionUtil);
		verify = new VerifyManager(connectionUtil);
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
		alerts = new AlertsManager(connectionUtil);
		
		unionVerify = new UnionVerifyManager(connectionUtil, urlWebsite, userWebsite, passWebsite);
		unionPlayers = new UnionPlayerManager(connectionUtil, fileManager.getMap("config", "central-dbs"), urlCentralTemp, userCentral, passCentral);

		updateDB();
	}


	// 0 - no version or error
	// 1> - compare active db version with resources
	// if version lower -> apply instruction for creating new tables, adding/removing collumns
	// in the end set active db version to resources
	public Integer getActiveDBVersion() {
		Integer version = 0;
		try (Connection conn = DriverManager.getConnection(connectionUtil.getUrlSQLite());
			PreparedStatement st = conn.prepareStatement("PRAGMA user_version")) {
			version = st.executeQuery().getInt(1);
		} catch(SQLException ex) {
			logger.warn("SQLite: Failed to get active database version", ex);
		}
		return version;
	}

	public Integer getResourcesDBVersion() {
		Integer version = 0;
		try {
			File tempFile = File.createTempFile("locale-", ".json");
			if (!fileManager.export(getClass().getResourceAsStream("/server.db"), tempFile.toPath())) {
				logger.error("Failed to write temp file {}!", tempFile.getName());
				return version;
			} else {
				try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + tempFile.getAbsolutePath());
				PreparedStatement st = conn.prepareStatement("PRAGMA user_version")) {
					version = st.executeQuery().getInt(1);
				} catch(SQLException ex) {
					logger.warn("Failed to get resources database version", ex);
				}
			}
			tempFile.delete();
		} catch (IOException ioException) {
			logger.error("Exception at version check\n", ioException);
		}
		return version;
	}

	private List<List<String>> loadInstructions(Integer activeVersion) {
		List<String> lines = new ArrayList<>();
		try {
			File tempFile = File.createTempFile("database_updates", ".tmp");
			if (!fileManager.export(App.class.getResourceAsStream("/database_updates"), tempFile.toPath())) {
				logger.error("Failed to write temp file {}!", tempFile.getName());
			} else {
				lines = Files.readAllLines(tempFile.toPath(), StandardCharsets.UTF_8);
			}
		} catch (Exception ex) {
			logger.error("SQLite: Failed to open update file", ex);
		}
		lines = lines.subList(activeVersion - 1, lines.size());
		List<List<String>> result = new ArrayList<>();
		lines.forEach(line -> {
			String[] points = line.split(";");
			List<String> list = points.length == 0 ? Arrays.asList(line) : Arrays.asList(points);
			if (!list.isEmpty()) result.add(list);
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
			try (Connection conn = DriverManager.getConnection(connectionUtil.getUrlSQLite());
			Statement st = conn.createStatement()) {
				if (activeVersion < newVersion) {
					for (List<String> version : loadInstructions(activeVersion)) {
						for (String sql : version) {
							logger.debug(sql);
							st.execute(sql);
						}
					}
				}
			} catch(SQLException ex) {
				logger.warn("SQLite: Failed to execute update!\nPerform database update manually or delete it.\n{}", ex.getMessage());
				return;
			}
			
			// Update version
			try (Connection conn = DriverManager.getConnection(connectionUtil.getUrlSQLite());
			Statement st = conn.createStatement()) {
				st.execute("PRAGMA user_version = "+newVersion.toString());
				logger.info("SQLite: Database version updated to {}", newVersion);
			} catch(SQLException ex) {
				logger.warn("SQLite: Failed to set active database version", ex);
			}
		}
	}

}
