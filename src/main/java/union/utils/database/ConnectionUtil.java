package union.utils.database;

import ch.qos.logback.classic.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public class ConnectionUtil {

	private final String urlSQLite;

	protected final Logger logger;

	protected ConnectionUtil(String urlSQLite, Logger logger) {
		this.urlSQLite = urlSQLite;
		this.logger = logger;
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

	protected PreparedStatement prepareStatement(final String sql) throws SQLException {
		return connectSQLite().prepareStatement(sql);
	}

	protected Statement createStatement() throws SQLException {
		return connectSQLite().createStatement();
	}

	protected Connection connectMySQL(final String url) {
		Connection conn = null;
		try {
			conn = DriverManager.getConnection(url);
		} catch (SQLException ex) {
			logger.error("MySQL: Connection error to database", ex);
			return null;
		}
		return conn;
	}

	protected PreparedStatement prepareStatement(final String url, final String sql) throws SQLException {
		return connectMySQL(url).prepareStatement(sql);
	}

	protected Statement createStatement(final String url) throws SQLException {
		return connectMySQL(url).createStatement();
	}
	
}
