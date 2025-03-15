package union.utils.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import union.metrics.Metrics;

@SuppressWarnings("SqlSourceToSinkFlow")
public class LiteDBBase {

	private final ConnectionUtil util;
	protected final String table;

	public LiteDBBase(ConnectionUtil connectionUtil, String table) {
		this.util = connectionUtil;
		this.table = table;
	}

	/**
	 * @param sql SQL statement to execute
	 * @throws SQLException Rethrows error
	 */
	protected void execute(final String sql) throws SQLException {
		// Metrics
		Metrics.databaseLiteQueries.labelValue(sql.split(" ")[0].toUpperCase()).inc();

		util.logger.debug(sql);
		try (Connection conn = DriverManager.getConnection(util.getUrlSQLite());
			 PreparedStatement st = conn.prepareStatement(sql)) {
			st.executeUpdate();
		} catch (SQLException ex) {
			util.logger.warn("DB SQLite: Error at statement execution\nRequest: {}", sql, ex);
			throw ex;
		}
	}

	/**
	 * @param sql SQL statement to execute
	 * @return inserted row ID.
	 * @throws SQLException Rethrows error
	 */
	protected int executeWithRow(final String sql) throws SQLException {
		// Metrics
		Metrics.databaseLiteQueries.labelValue(sql.split(" ")[0].toUpperCase()).inc();

		util.logger.debug(sql);
		try (Connection conn = DriverManager.getConnection(util.getUrlSQLite());
			 PreparedStatement st = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
			st.executeUpdate();
			return st.getGeneratedKeys().getInt(1);
		} catch (SQLException ex) {
			util.logger.warn("DB SQLite: Error at statement execution\nRequest: {}", sql, ex);
			throw ex;
		}
	}

	// Select
	@Nullable
	protected <T> T selectOne(final String sql, String selectKey, Class<T> selectClass) {
		// Metrics
		Metrics.databaseLiteQueries.labelValue("SELECT").inc();

		T result = null;

		util.logger.debug(sql);
		try (Connection conn = DriverManager.getConnection(util.getUrlSQLite());
			PreparedStatement st = conn.prepareStatement(sql)) {
			ResultSet rs = st.executeQuery();

			try {
				if (rs.next()) result = rs.getObject(selectKey, selectClass);
			} catch (SQLException ex) {
				if (!rs.wasNull()) throw ex;
			}
		} catch (SQLException ex) {
			util.logger.warn("DB SQLite: Error at SELECT\nRequest: {}", sql, ex);
		}
		return result;
	}

	@NotNull
	protected <T> List<T> select(final String sql, String selectKey, Class<T> selectClass) {
		// Metrics
		Metrics.databaseLiteQueries.labelValue("SELECT").inc();

		List<T> results = new ArrayList<>();

		util.logger.debug(sql);
		try (Connection conn = DriverManager.getConnection(util.getUrlSQLite());
			PreparedStatement st = conn.prepareStatement(sql)) {
			ResultSet rs = st.executeQuery();

			while (rs.next()) {
				try {
					results.add(rs.getObject(selectKey, selectClass));
				} catch (SQLException ex) {
					if (!rs.wasNull()) throw ex;
				}
			}
		} catch (SQLException ex) {
			util.logger.warn("DB SQLite: Error at SELECT\nRequest: {}", sql, ex);
		}
		return results;
	}

	@Nullable
	protected Map<String, Object> selectOne(final String sql, final Set<String> selectKeys) {
		// Metrics
		Metrics.databaseLiteQueries.labelValue("SELECT").inc();

		Map<String, Object> result = new HashMap<>();

		util.logger.debug(sql);
		try (Connection conn = DriverManager.getConnection(util.getUrlSQLite());
			PreparedStatement st = conn.prepareStatement(sql)) {
			ResultSet rs = st.executeQuery();

			if (rs.next())
				for (String key : selectKeys) {
					result.put(key, rs.getObject(key));
				}
		} catch (SQLException ex) {
			util.logger.warn("DB SQLite: Error at SELECT\nRequest: {}", sql, ex);
		}
		return result.isEmpty() ? null : result;
	}

	@NotNull
	protected List<Map<String, Object>> select(final String sql, final Set<String> selectKeys) {
		// Metrics
		Metrics.databaseLiteQueries.labelValue("SELECT").inc();

		List<Map<String, Object>> results = new ArrayList<>();

		util.logger.debug(sql);
		try (Connection conn = DriverManager.getConnection(util.getUrlSQLite());
			PreparedStatement st = conn.prepareStatement(sql)) {
			ResultSet rs = st.executeQuery();

			while (rs.next()) {
				Map<String, Object> data = new HashMap<>();
				for (String key : selectKeys) {
					data.put(key, rs.getObject(key));
				}
				results.add(data);
			}
		} catch (SQLException ex) {
			util.logger.warn("DB SQLite: Error at SELECT\nRequest: {}", sql, ex);
		}
		return results;
	}

	// Exists
	protected boolean exists(final String sql) {
		// Metrics
		Metrics.databaseLiteQueries.labelValue("SELECT").inc();

		boolean result = false;

		util.logger.debug(sql);
		try (Connection conn = DriverManager.getConnection(util.getUrlSQLite());
			 PreparedStatement st = conn.prepareStatement(sql)) {
			ResultSet rs = st.executeQuery();

			try {
				if (rs.next()) result = rs.getBoolean(1);
			} catch (SQLException ex) {
				if (!rs.wasNull()) throw ex;
			}
		} catch (SQLException ex) {
			util.logger.warn("DB SQLite: Error at SELECT\nRequest: {}", sql, ex);
		}
		return result;
	}

	protected int count(final String sql) {
		// Metrics
		Metrics.databaseLiteQueries.labelValue("SELECT").inc();

		int result = 0;

		util.logger.debug(sql);
		try (Connection conn = DriverManager.getConnection(util.getUrlSQLite());
			PreparedStatement st = conn.prepareStatement(sql)) {
			ResultSet rs = st.executeQuery();

			try {
				if (rs.next()) result = rs.getInt(1);
			} catch (SQLException ex) {
				if (!rs.wasNull()) throw ex;
			}
		} catch (SQLException ex) {
			util.logger.warn("DB SQLite: Error at SELECT\nRequest: {}", sql, ex);
		}
		return result;
	}

	protected String getUrl() {
		return util.getUrlSQLite();
	}

	// UTILS
	@NotNull
	protected String quote(Object value) {
		// Convert to string and replace '(single quote) with ''(2 single quotes) for sql
		if (value == null) return "NULL";
		String str = String.valueOf(value);
		if (str.isBlank() || str.equalsIgnoreCase("NULL")) return "NULL";

		return String.format("'%s'", String.valueOf(value).replaceAll("'", "''")); // smt's -> 'smt''s'
	}

	protected <T, V> T applyNonNull(V obj, @NotNull Function<V, T> function) {
		return (obj != null) ? function.apply(obj) : null;
	}

}
