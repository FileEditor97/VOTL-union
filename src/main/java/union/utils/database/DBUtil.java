package union.utils.database;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import union.utils.database.managers.AccessManager;
import union.utils.database.managers.BanManager;
import union.utils.database.managers.GroupManager;
import union.utils.database.managers.GuildSettingsManager;
import union.utils.database.managers.ModuleManager;
import union.utils.database.managers.RoleManager;
import union.utils.database.managers.TicketManager;
import union.utils.database.managers.TicketPanelManager;
import union.utils.database.managers.VerifyCacheManager;
import union.utils.database.managers.VerifyManager;
import union.utils.database.managers.VerifyRequestManager;
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
	public final TicketPanelManager ticketPanel;
	public final RoleManager role;
	public final TicketManager ticket;
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
		ticketPanel = new TicketPanelManager(this);
		role = new RoleManager(this);
		ticket = new TicketManager(this);
		
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

	// CREATE TABLE table_name (column1 datatype, column2 datatype);
	// DROP TABLE table_name;
	// ALTER TABLE table_name RENAME TO new_name;
	// ALTER TABLE table_name ADD column_name datatype;
	// ALTER TABLE table_name DROP column_name;
	// ALTER TABLE table_name RENAME old_name to new_name;
	private final List<List<String>> instruct = List.of(
		List.of("ALTER TABLE 'guild' DROP 'language'", "DROP TABLE 'modAccess'", "CREATE TABLE 'modAccess' ('roleId' TEXT NOT NULL, 'guildId' TEXT NOT NULL, FOREIGN KEY('guildId') REFERENCES 'guild'('guildId'))"), // 1 -> 2
		List.of("ALTER TABLE 'verify' DROP 'verificationLink'", "ALTER TABLE 'verify' DROP 'instructionField'", "ALTER TABLE 'verify' RENAME 'panelText' TO 'mainText'"), // 2 -> 3
		List.of("ALTER TABLE 'guild' ADD 'ticketLogId' TEXT", "CREATE TABLE 'ticketPanel' ('guildId' TEXT NOT NULL, 'panelColor' TEXT DEFAULT '0x112E51', FOREIGN KEY('guildId') REFERENCES 'guild'('guildId'))",
			"CREATE TABLE 'requestRole' ('guildId' TEXT NOT NULL, 'roleId' TEXT NOT NULL UNIQUE, 'description' TEXT, 'type' INTEGER NOT NULL DEFAULT 0, FOREIGN KEY('guildId') REFERENCES 'guild'('guildId'))",
			"CREATE TABLE 'ticket' ('ticketId' INTEGER NOT NULL, 'guildId' TEXT, 'userId' TEXT NOT NULL, 'modId' TEXT, 'channelId' TEXT, 'alias' TEXT NOT NULL, 'closed' INTEGER NOT NULL DEFAULT 0, 'roleIds' TEXT)",
			"CREATE TABLE 'ticketType' ('guildId' TEXT NOT NULL, 'alias' TEXT NOT NULL, 'short' TEXT NOT NULL, 'long' TEXT, FOREIGN KEY('guildId') REFERENCES 'guild'('guildId'))"), // 3 -> 4
		List.of("ALTER TABLE 'ticket' ADD 'timeClosed' INTEGER"), // 4 -> 5
		List.of("ALTER TABLE 'ticketPanel' DROP 'panelColor'", "ALTER TABLE 'verify' DROP 'panelColor'", "ALTER TABLE 'guild' ADD 'color' TEXT DEFAULT '0x112E51'",
			"ALTER TABLE 'modAccess' RENAME TO 'roleAccess'", "ALTER TABLE 'roleAccess' ADD 'level' INTEGER NOT NULL",
			"CREATE TABLE 'userAccess' ('guildId' TEXT NOT NULL, 'userId' TEXT NOT NULL, 'level' INTEGER NOT NULL, FOREIGN KEY('guildId') REFERENCES 'guild'('guildId'))",
			"DROP TABLE 'groupMaster'", "DROP TABLE 'groupSync'", "CREATE TABLE 'groupMaster' ('groupId' INTEGER, 'masterId' TEXT NOT NULL, 'name' TEXT, 'isShared' INTEGER NOT NULL DEFAULT 0, PRIMARY KEY('groupId' AUTOINCREMENT))",
			"CREATE TABLE 'groupMembers' ('groupId' INTEGER NOT NULL, 'guildId' TEXT NOT NULL, 'canManage' INTEGER NOT NULL DEFAULT 0, FOREIGN KEY('groupId') REFERENCES 'groupMaster'('groupId'))"), // 5 -> 6
		List.of("ALTER TABLE 'groupMembers' ADD 'whitelisted' INTEGER NOT NULL DEFAULT 0"), // 6 -> 7
		List.of("ALTER TABLE 'guild' ADD 'lastWebhook' TEXT") // 7 -> 8
	);

	private void updateDB() {
		// 0 - skip
		Integer newVersion = getResourcesDBVersion();
		if (newVersion == 0) return;
		Integer activeVersion = getActiveDBVersion();
		if (activeVersion == 0) return;

		if (newVersion > activeVersion) {
			try (Connection conn = DriverManager.getConnection(urlSQLite);
				Statement st = conn.createStatement()) {
				while (activeVersion < newVersion) {
					Integer next = activeVersion + 1;
					List<String> instructions = instruct.get(next - 2);
					for (String sql : instructions) {
						logger.debug(sql);
						st.execute(sql);
					}
					activeVersion = next;
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
