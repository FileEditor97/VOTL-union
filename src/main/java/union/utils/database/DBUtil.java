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
import union.utils.database.managers.BanManager;
import union.utils.database.managers.GroupManager;
import union.utils.database.managers.GuildSettingsManager;
import union.utils.database.managers.GuildVoiceManager;
import union.utils.database.managers.ModuleManager;
import union.utils.database.managers.RequestsManager;
import union.utils.database.managers.RoleManager;
import union.utils.database.managers.TicketManager;
import union.utils.database.managers.TicketPanelManager;
import union.utils.database.managers.TicketTagManager;
import union.utils.database.managers.UserSettingsManager;
import union.utils.database.managers.TicketSettingsManager;
import union.utils.database.managers.VerifyCacheManager;
import union.utils.database.managers.VerifyManager;
import union.utils.database.managers.VerifyRequestManager;
import union.utils.database.managers.VoiceChannelManager;
import union.utils.database.managers.WebhookManager;
import union.utils.file.FileManager;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;

public class DBUtil {

	private final FileManager fileManager;

	public final GuildSettingsManager guild;
	public final WebhookManager webhook;
	public final ModuleManager module;
	public final AccessManager access;
	public final BanManager ban;
	public final GroupManager group;
	public final VerifyManager verify;
	public final VerifyCacheManager verifyCache;
	public final TicketSettingsManager ticketSettings;
	public final TicketPanelManager panels;
	public final TicketTagManager tags;
	public final RoleManager role;
	public final TicketManager ticket;
	public final RequestsManager requests;
	public final GuildVoiceManager guildVoice;
	public final UserSettingsManager user;
	public final VoiceChannelManager voice;
	public final VerifyRequestManager verifyRequest;

	protected final Logger logger = (Logger) LoggerFactory.getLogger(DBUtil.class);

	private String urlSQLite;
	private String urlMySql;
	private String sqldb;
	private String username;
	private String pass;

	public DBUtil(FileManager fileManager) {
		this.fileManager = fileManager;
		this.urlSQLite = "jdbc:sqlite:" + fileManager.getFiles().get("database");
		this.sqldb = fileManager.getNullableString("config", "mysql-db");
		this.urlMySql = "jdbc:mysql://" + fileManager.getNullableString("config", "mysql-ip") + ":3306/" + sqldb;
		this.username = fileManager.getNullableString("config", "mysql-user");
		this.pass = fileManager.getNullableString("config", "mysql-pass");
		
		guild = new GuildSettingsManager(this);
		webhook = new WebhookManager(this);
		module = new ModuleManager(this);
		access = new AccessManager(this);
		ban = new BanManager(this);
		group = new GroupManager(this);
		verify = new VerifyManager(this);
		verifyCache = new VerifyCacheManager(this);
		ticketSettings = new TicketSettingsManager(this);
		panels = new TicketPanelManager(this);
		tags = new TicketTagManager(this);
		role = new RoleManager(this);
		ticket = new TicketManager(this);
		requests = new RequestsManager(this);
		guildVoice = new GuildVoiceManager(this);
		user = new UserSettingsManager(this);
		voice = new VoiceChannelManager(this);
		
		try {
			Class.forName("com.mysql.cj.jdbc.Driver").getDeclaredConstructor().newInstance();
		} catch (Exception ex) {
			logger.error("MySQL: Exiting, driver not found initiated", ex);
			System.exit(0);
		}
		verifyRequest = new VerifyRequestManager(this, sqldb);

		updateDB();
	}

	protected Connection connectSQLite() {
		Connection conn = null;
		try {
			conn = DriverManager.getConnection(urlSQLite);
		} catch (SQLException ex) {
			logger.error("SQLite: Connection error to database", ex);
			return null;
		}
		return conn;
	}

	protected Connection connectMySql() {
		Connection conn = null;
		try {
			conn = DriverManager.getConnection(urlMySql, username, pass);
		} catch (SQLException ex) {
			logger.error("MySQL: Connection error to database", ex);
			return null;
		}
		return conn;
	}

	// 0 - no version or error
	// 1> - compare active db version with resources
	// if version lower -> apply instruction for creating new tables, adding/removing collumns
	// in the end set active db version to resources
	public Integer getActiveDBVersion() {
		Integer version = 0;
		try (Connection conn = DriverManager.getConnection(urlSQLite);
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

	public List<List<String>> loadInstructions(Integer activeVersion) {
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
			try (Connection conn = DriverManager.getConnection(urlSQLite);
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
			try (Connection conn = DriverManager.getConnection(urlSQLite);
			Statement st = conn.createStatement()) {
				st.execute("PRAGMA user_version = "+newVersion.toString());
				logger.info("SQLite: Database version updated to {}", newVersion);
			} catch(SQLException ex) {
				logger.warn("SQLite: Failed to set active database version", ex);
			}
		}
	}

}
