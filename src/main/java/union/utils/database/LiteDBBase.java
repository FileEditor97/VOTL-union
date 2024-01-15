package union.utils.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.Nullable;

public class LiteDBBase {

	private final ConnectionUtil util;

	public LiteDBBase(ConnectionUtil connectionUtil) {
		this.util = connectionUtil;
	}

	// Execute statement
	protected void execute(final String sql) {
		util.logger.debug(sql);
		try (Connection conn = DriverManager.getConnection(util.getUrlSQLite());
			PreparedStatement st = conn.prepareStatement(sql)) {
			st.executeUpdate();
		} catch (SQLException ex) {
			util.logger.warn("DB SQLite: Error at statement execution\nRequest: {}", sql, ex);
		}
	}

	// Select
	protected <T> T selectOne(final String sql, String selectKey, Class<T> selectClass) {
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

	protected <T> List<T> select(final String sql, String selectKey, Class<T> selectClass) {
		List<T> results = new ArrayList<T>();

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
	protected Map<String, Object> selectOne(final String sql, final List<String> selectKeys) {
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

	protected List<Map<String, Object>> select(final String sql, final List<String> selectKeys) {
		List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();

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

	protected Integer count(final String sql) {
		Integer result = 0;

		util.logger.debug(sql);
		try (Connection conn = DriverManager.getConnection(util.getUrlSQLite());
			PreparedStatement st = conn.prepareStatement(sql)) {
			ResultSet rs = st.executeQuery();

			try {
				if (rs.next()) result = rs.getInt("COUNT(*)");
			} catch (SQLException ex) {
				if (!rs.wasNull()) throw ex;
			}
		} catch (SQLException ex) {
			util.logger.warn("DB SQLite: Error at SELECT\nRequest: {}", sql, ex);
		}
		return result;
	}

	protected Integer getIncrement(final String table) {
		Integer data = selectOne("SELECT seq FROM sqlite_sequence WHERE (name=%s)".formatted(quote(table)), "seq", Integer.class);
		if (data == null) return 0;
		return data;
	}


	// UTILS
	protected String quote(Object value) {
		// Convert to string and replace '(single quote) with ''(2 single quotes) for sql
		if (value == null) return "NULL";
		String str = String.valueOf(value);
		if (str == "NULL") return str;

		return "'" + String.valueOf(value).replaceAll("'", "''") + "'"; // smt's -> 'smt''s'
	}

}
