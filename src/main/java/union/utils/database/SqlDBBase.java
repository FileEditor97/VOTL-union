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

import union.utils.database.managers.UnionPlayerManager.PlayerInfo;

public class SqlDBBase {

	private final ConnectionUtil cu;
	private final String url;

	public SqlDBBase(ConnectionUtil connectionUtil, String url) {
		this.cu = connectionUtil;
		this.url = url;
	}

	// SELECT sql
	protected List<String> select(String table, String selectKey, String condKey, String condValue) {
		return select(table, selectKey, List.of(condKey), List.of(condValue));
	}

	protected List<String> select(final String table, final String selectKey, final List<String> condKeys, final List<String> condValuesInp) {
		List<String> condValues = condValuesInp.stream().map(value -> quote(value)).toList();

		String sql = "SELECT "+selectKey+" FROM "+table+" WHERE ";
		for (int i = 0; i < condKeys.size(); i++) {
			if (i > 0) {
				sql += " AND ";
			}
			sql += condKeys.get(i)+"="+condValues.get(i);
		}

		List<String> results = new ArrayList<String>();
		cu.logger.debug(sql);
		try (Connection conn = DriverManager.getConnection(url);
		PreparedStatement st = conn.prepareStatement(sql)) {
			ResultSet rs = st.executeQuery();
			while (rs.next()) {
				results.add(rs.getString(selectKey));
			}
		} catch (SQLException ex) {
			cu.logger.warn("DB MariaDB: Error at SELECT\nrequest: {}", sql, ex);
		}
		return results;
	}

	protected List<Map<String, String>> select(String table, List<String> selectKeys, String condKey, String condValue) {
		return select(table, selectKeys, List.of(condKey), List.of(condValue));
	}

	protected List<Map<String, String>> select(final String table, final List<String> selectKeys, final List<String> condKeys, final List<String> condValuesInp) {
		List<String> condValues = condValuesInp.stream().map(value -> quote(value)).toList();

		StringBuffer sql = new StringBuffer("SELECT "); //* FROM "+table+" WHERE ";

		for (int i = 0; i < selectKeys.size(); i++) {
			if (i > 0) {
				sql.append(", ");
			}
			sql.append(selectKeys.get(i));
		}
		sql.append(" FROM ").append(table).append(" WHERE ");
		for (int i = 0; i < condKeys.size(); i++) {
			if (i > 0) {
				sql.append(" AND ");
			}
			sql.append(condKeys.get(i)).append("=").append(condValues.get(i));
		}

		List<Map<String, String>> results = new ArrayList<Map<String, String>>();

		cu.logger.debug(sql.toString());
		try (Connection conn = DriverManager.getConnection(url);
		PreparedStatement st = conn.prepareStatement(sql.toString())) {
			ResultSet rs = st.executeQuery();
			List<String> keys = new ArrayList<>();
			
			if (selectKeys.size() == 0) {
				for (int i = 1; i<=rs.getMetaData().getColumnCount(); i++) {
					keys.add(rs.getMetaData().getColumnName(i));
				}
			} else {
				keys = selectKeys;
			}

			while (rs.next()) {
				Map<String, String> data = new HashMap<>();
				for (String key : keys) {
					data.put(key, rs.getString(key));
				}
				results.add(data);
			}
		} catch (SQLException ex) {
			cu.logger.warn("DB MariaDB: Error at SELECT\nrequest: {}", sql.toString(), ex);
		}
		return results;
	}

	protected String selectOne(final String table, final String selectKey, final String condKey, final Object condValue) {
		String sql = "SELECT %s FROM %s WHERE %s=%s".formatted(selectKey, table, condKey, quote(condValue));

		String result = null;
		cu.logger.debug(sql);
		try (Connection conn = DriverManager.getConnection(url);
		PreparedStatement st = conn.prepareStatement(sql)) {
			ResultSet rs = st.executeQuery();
			if (rs.next()) result = rs.getString(selectKey);
		} catch (SQLException ex) {
			cu.logger.warn("DB MariaDB: Error at SELECT\nrequest: {}", sql, ex);
		}
		return result;
	}

	// SELECT with database selection
	protected String selectOne(final String database, final String table, final String selectKey, final String condKey, final Object condValue) {
		String sql = "SELECT %s FROM %s.%s WHERE %s=%s".formatted(selectKey, database, table, condKey, quote(condValue));

		String result = null;
		cu.logger.debug(sql);
		try (Connection conn = DriverManager.getConnection(url);
		PreparedStatement st = conn.prepareStatement(sql)) {
			ResultSet rs = st.executeQuery();
			if (rs.next()) result = rs.getString(selectKey);
		} catch (SQLException ex) {
			cu.logger.warn("DB MariaDB: Error at SELECT\nrequest: {}", sql, ex);
		}
		return result;
	}

	// UPDATE sql
	protected void update(final String table, final String updateKey, final Object updateValueObj, final String condKey, final Object condValueObj) {
		String sql = "UPDATE "+table+" SET "+updateKey+"="+quote(updateValueObj)+" WHERE "+condKey+"="+quote(condValueObj);

		cu.logger.debug(sql);
		try (Connection conn = DriverManager.getConnection(url);
		PreparedStatement st = conn.prepareStatement(sql)) {
			st.executeUpdate();
		} catch (SQLException ex) {
			cu.logger.warn("DB MariaDB: Error at UPDATE\nrequest: {}", sql, ex);
		}
	}

	private String quote(Object value) {
		// Convert to string and replace '(single quote) with ''(2 single quotes) for sql
		String str = String.valueOf(value);
		if (str == "NULL") {
			return str;
		}
		return "'" + String.valueOf(value).replaceAll("'", "''") + "'"; // smt's -> 'smt''s'
	}


	// Specific SELECT
	protected PlayerInfo selectPlayerInfo(final String database, final String table, final String steamId) {
		String sql = "SELECT rank, play_time FROM %s.%s WHERE steamid=%s;".formatted(database, table, quote(steamId));

		PlayerInfo result = null;
		cu.logger.debug(sql);
		try (Connection conn = DriverManager.getConnection(url);
		PreparedStatement st = conn.prepareStatement(sql)) {
			ResultSet rs = st.executeQuery();
			if (rs.next()) result = new PlayerInfo(steamId, rs.getString("rank"), rs.getLong("play_time"));
		} catch (SQLException ex) {
			cu.logger.warn("DB MariaDB: Error at SELECT\nrequest: {}", sql, ex);
		}
		return result==null ? new PlayerInfo(steamId) : result;
	}
}
