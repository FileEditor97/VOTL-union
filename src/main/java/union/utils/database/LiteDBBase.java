package union.utils.database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LiteDBBase {

	private final ConnectionUtil util;

	public LiteDBBase(ConnectionUtil connectionUtil) {
		this.util = connectionUtil;
	}

	// INSERT sql
	@Deprecated
	protected void insert(String table, String insertKey, Object insertValueObj) {
		String sql = "INSERT INTO %s(%s) VALUES(%s);".formatted(table, insertKey, quote(insertValueObj));
		util.logger.debug(sql);

		try (PreparedStatement st = util.prepareStatement(sql)) {
			st.executeUpdate();
		} catch (SQLException ex) {
			util.logger.warn("DB SQLite: Error at INSERT\nrequest: {}", sql, ex);
		}
	}
	
	@Deprecated
	protected void insert(final String table, final List<String> insertKeys, final List<Object> insertValuesObj) {
		List<String> insertValues = new ArrayList<String>(insertValuesObj.size());
		for (Object obj : insertValuesObj) {
			insertValues.add(quote(obj));
		}

		String sql = "INSERT INTO %s(%s) VALUES(%s);".formatted(table, String.join(", ", insertKeys), String.join(", ", insertValues));
		util.logger.debug(sql);
		try (PreparedStatement st = util.prepareStatement(sql)) {
			st.executeUpdate();
		} catch (SQLException ex) {
			util.logger.warn("DB SQLite: Error at INSERT\nrequest: {}", sql, ex);
		}
	}

	// SELECT sql
	@Deprecated
	protected Object selectOne(final String table, final String selectKey, final String condKey, final Object condValueObj) {
		String sql = "SELECT %s FROM %s WHERE %s=%s;".formatted(selectKey, table, condKey, quote(condValueObj));

		Object result = null;
		util.logger.debug(sql);
		try (PreparedStatement st = util.prepareStatement(sql)) {
			ResultSet rs = st.executeQuery();
			result = rs.getObject(selectKey);
		} catch (SQLException ex) {
			util.logger.warn("DB SQLite: Error at SELECT\nrequest: {}", sql, ex);
		}
		return result;
	}

	@Deprecated
	protected Object selectOne(final String table, final String selectKey, final List<String> condKeys, final List<Object> condValuesObj) {
		List<String> condValues = new ArrayList<String>(condValuesObj.size());
		for (Object obj : condValuesObj) {
			condValues.add(quote(obj));
		}

		String sql = "SELECT "+selectKey+" FROM "+table+" WHERE ";
		for (int i = 0; i < condKeys.size(); i++) {
			if (i > 0) {
				sql += " AND ";
			}
			sql += condKeys.get(i)+"="+condValues.get(i);
		}

		Object result = null;
		util.logger.debug(sql);
		try (PreparedStatement st = util.prepareStatement(sql)) {
			ResultSet rs = st.executeQuery();
			result = rs.getObject(selectKey);
		} catch (SQLException ex) {
			util.logger.warn("DB SQLite: Error at SELECT\nrequest: {}", sql, ex);
		}
		return result;
	}

	@Deprecated
	protected Map<String, Object> selectOne(String table, List<String> selectKeys, String condKey, Object condValueObj) {
		return selectOne(table, selectKeys, List.of(condKey), List.of(condValueObj));
	}

	@Deprecated
	protected Map<String, Object> selectOne(final String table, final List<String> selectKeys, final List<String> condKeys, final List<Object> condValuesObj) {
		List<String> condValues = new ArrayList<String>(condValuesObj.size());
		for (Object obj : condValuesObj) {
			condValues.add(quote(obj));
		}

		String sql = "SELECT * FROM "+table+" WHERE ";
		for (int i = 0; i < condKeys.size(); i++) {
			if (i > 0) {
				sql += " AND ";
			}
			sql += condKeys.get(i)+"="+condValues.get(i);
		}

		Map<String, Object> result = new HashMap<>();
		util.logger.debug(sql);
		try (PreparedStatement st = util.prepareStatement(sql)) {
			ResultSet rs = st.executeQuery();
			
			List<String> keys = new ArrayList<>();
			if (selectKeys.size() == 0) {
				for (int i = 1; i<=rs.getMetaData().getColumnCount(); i++) {
					keys.add(rs.getMetaData().getColumnName(i));
				}
			} else {
				keys = selectKeys;
			}

			for (String key : keys) {
				result.put(key, rs.getObject(key));
			}
		} catch (SQLException ex) {
			util.logger.warn("DB SQLite: Error at SELECT\nrequest: {}", sql, ex);
		}
		return result;
	}

	@Deprecated
	protected List<Object> select(String table, String selectKey, String condKey, Object condValueObj) {
		return select(table, selectKey, List.of(condKey), List.of(condValueObj));
	}

	@Deprecated
	protected List<Object> select(final String table, final String selectKey, final List<String> condKeys, final List<Object> condValuesObj) {
		List<String> condValues = new ArrayList<String>(condValuesObj.size());
		for (Object obj : condValuesObj) {
			condValues.add(quote(obj));
		}

		String sql = "SELECT "+selectKey+" FROM "+table+" WHERE ";
		for (int i = 0; i < condKeys.size(); i++) {
			if (i > 0) {
				sql += " AND ";
			}
			sql += condKeys.get(i)+"="+condValues.get(i);
		}

		List<Object> results = new ArrayList<Object>();
		util.logger.debug(sql);
		try (PreparedStatement st = util.prepareStatement(sql)) {
			ResultSet rs = st.executeQuery();
			while (rs.next()) {
				results.add(rs.getObject(selectKey));
			}
		} catch (SQLException ex) {
			util.logger.warn("DB SQLite: Error at SELECT\nrequest: {}", sql, ex);
		}
		return results;
	}

	@Deprecated
	protected List<Map<String, Object>> select(String table, List<String> selectKeys, String condKey, Object condValueObj) {
		return select(table, selectKeys, List.of(condKey), List.of(condValueObj));
	}

	@Deprecated
	protected List<Map<String, Object>> select(final String table, final List<String> selectKeys, final List<String> condKeys, final List<Object> condValuesObj) {
		List<String> condValues = new ArrayList<String>(condValuesObj.size());
		for (Object obj : condValuesObj) {
			condValues.add(quote(obj));
		}

		String sql = "SELECT * FROM "+table+" WHERE ";
		for (int i = 0; i < condKeys.size(); i++) {
			if (i > 0) {
				sql += " AND ";
			}
			sql += condKeys.get(i)+"="+condValues.get(i);
		}

		List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();

		util.logger.debug(sql);
		try (PreparedStatement st = util.prepareStatement(sql)) {
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
				Map<String, Object> data = new HashMap<>();
				for (String key : keys) {
					data.put(key, rs.getObject(key));
				}
				results.add(data);
			}
		} catch (SQLException ex) {
			util.logger.warn("DB SQLite: Error at SELECT\nrequest: {}", sql, ex);
		}
		return results;
	}

	@Deprecated
	protected Object selectLast(final String table, final String selectKey) {
		String sql = "SELECT "+selectKey+" FROM "+table+" ORDER BY "+selectKey+" DESC LIMIT 1";

		Object result = null;
		util.logger.debug(sql);
		try (PreparedStatement st = util.prepareStatement(sql)) {
			ResultSet rs = st.executeQuery();
			while (rs.next()) {
				result = rs.getObject(selectKey);
			}
		} catch (SQLException ex) {
			util.logger.warn("DB SQLite: Error at SELECT\nrequest: {}", sql, ex);
		}
		return result;
	}

	@Deprecated
	protected Object selectLast(final String table, final String selectKey, final String condKey, final Object condValueObj) {
		String sql = "SELECT "+selectKey+" FROM "+table+" WHERE "+condKey+"="+quote(condValueObj)+" ORDER BY "+selectKey+" DESC LIMIT 1";

		Object result = null;
		util.logger.debug(sql);
		try (PreparedStatement st = util.prepareStatement(sql)) {
			ResultSet rs = st.executeQuery();
			while (rs.next()) {
				result = rs.getObject(selectKey);
			}
		} catch (SQLException ex) {
			util.logger.warn("DB SQLite: Error at SELECT\nrequest: {}", sql, ex);
		}
		return result;
	}

	@Deprecated
	protected List<Map<String, Object>> selectAll(final String table, final List<String> selectKeys) {
		String sql = "SELECT * FROM "+table;
		
		List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();

		util.logger.debug(sql);
		try (PreparedStatement st = util.prepareStatement(sql)) {
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
				Map<String, Object> data = new HashMap<>();
				for (String key : keys) {
					data.put(key, rs.getObject(key));
				}
				results.add(data);
			}
		} catch (SQLException ex) {
			util.logger.warn("DB SQLite: Error at SELECT\nrequest: {}", sql, ex);
		}
		return results;
	}

	@Deprecated
	protected Integer countAll(final String table) {
		String sql = "SELECT COUNT(*) FROM "+table;

		util.logger.debug(sql);
		Integer result = null;
		try (PreparedStatement st = util.prepareStatement(sql)) {
			ResultSet rs = st.executeQuery();
			result = rs.getInt("COUNT(*)");
		} catch (SQLException ex) {
			util.logger.warn("DB SQLite: Error at COUNT\nrequest: {}", sql, ex);
		}
		return result;
	}

	@Deprecated
	protected Integer countSelect(final String table, final List<String> condKeys, final List<Object> condValuesObj) {
		List<String> condValues = new ArrayList<String>(condValuesObj.size());
		for (Object obj : condValuesObj) {
			condValues.add(quote(obj));
		}

		String sql = "SELECT COUNT(*) FROM "+table+" WHERE ";
		for (int i = 0; i < condKeys.size(); i++) {
			if (i > 0) {
				sql += " AND ";
			}
			sql += condKeys.get(i)+"="+condValues.get(i);
		}

		util.logger.debug(sql);
		Integer result = null;
		try (PreparedStatement st = util.prepareStatement(sql)) {
			ResultSet rs = st.executeQuery();
			result = rs.getInt("COUNT(*)");
		} catch (SQLException ex) {
			util.logger.warn("DB SQLite: Error at COUNT\nrequest: {}", sql, ex);
		}
		return result;
	}

	@Deprecated
	protected Integer getIncrement(final String table) {
		Object data = selectOne("sqlite_sequence", "seq", "name", table);
		if (data == null) return 0;
		return (Integer) data;
	}

	//  specific SELECT, return tickets to be autoclosed
	@Deprecated
	protected List<String> getExpiredTickets(String table, Long time) {
		String sql = "SELECT channelId FROM %s WHERE (closed=0 AND closeRequested>0 AND closeRequested<=%d)".formatted(table, time);

		List<String> results = new ArrayList<String>();
		util.logger.debug(sql);
		try (PreparedStatement st = util.prepareStatement(sql)) {
			ResultSet rs = st.executeQuery();
			while (rs.next()) {
				results.add(rs.getString("channelId"));
			}
		} catch (SQLException ex) {
			util.logger.warn("DB SQLite: Error at SELECT\nrequest: {}", sql, ex);
		}
		return results;
	}

	//  specific SELECT, count tickets by dates
	@Deprecated
	protected Integer countTicketsClaimed(String table, String guildId, String modId, Long afterTime, Long beforeTime, boolean roleTag) {
		String tagType = roleTag ? "tagId=0" : "tagId>=1";
		String sql = "SELECT COUNT(*) FROM %s WHERE (guildId='%s' AND modId='%s' AND timeClosed>=%d AND timeClosed<=%d AND %s)".formatted(table, guildId, modId, afterTime, beforeTime, tagType);
		
		Integer result = 0;
		util.logger.debug(sql);
		try (PreparedStatement st = util.prepareStatement(sql)) {
			ResultSet rs = st.executeQuery();
			result = rs.getInt("COUNT(*)");
		} catch (SQLException ex) {
			util.logger.warn("DB SQLite: Error at SELECT\nrequest: {}", sql, ex);
		}
		return result;
	}

	//  specific SELECT, get last id of ticket
	@Deprecated
	protected Integer selectLastTicketId(final String table, final String guildId, final Integer tagId) {
		String sql = "SELECT ticketId FROM %s WHERE (guildId='%s' AND tagId='%s') ORDER BY ticketId DESC LIMIT 1".formatted(table, guildId, tagId);

		Integer result = null;
		util.logger.debug(sql);
		try (PreparedStatement st = util.prepareStatement(sql)) {
			ResultSet rs = st.executeQuery();
			result = rs.getInt("ticketId");
		} catch (SQLException ex) {
			util.logger.warn("DB SQLite: Error at SELECT\nrequest: {}", sql, ex);
		}
		return result;
	}

	//  specific SELECT, get expired temp roles
	@Deprecated
	protected List<Map<String, String>> selectExpiredTempRoles(final String table, final Long time) {
		String sql = "SELECT roleId, userId FROM %s WHERE (expireAfter<=%d)".formatted(table, time);

		List<Map<String, String>> results = new ArrayList<Map<String, String>>();
		util.logger.debug(sql);
		try (PreparedStatement st = util.prepareStatement(sql)) {
			ResultSet rs = st.executeQuery();
			while (rs.next()) {
				results.add(Map.of("roleId", rs.getString("roleId"), "userId", rs.getString("userId")));
			}
		} catch (SQLException ex) {
			util.logger.warn("DB SQLite: Error at SELECT\nrequest: {}", sql, ex);
		}
		return results;
	}

	//  specific SELECT, get roles in guild with invites
	@Deprecated
	protected Map<String, String> selectRoleInvites(final String table, final String guildId) {
		String sql = "SELECT roleId, discordInvite FROM %s WHERE (guildId='%s' AND discordInvite IS NOT NULL)".formatted(table, guildId);

		Map<String, String> results = new HashMap<String, String>();
		util.logger.debug(sql);
		try (PreparedStatement st = util.prepareStatement(sql)) {
			ResultSet rs = st.executeQuery();
			while (rs.next()) {
				results.put(rs.getString("roleId"), rs.getString("discordInvite"));
			}
		} catch (SQLException ex) {
			util.logger.warn("DB SQLite: Error at SELECT\nrequest: {}", sql, ex);
		}
		return results;
	}

	// UPDATE sql
	@Deprecated
	protected void update(String table, String updateKey, Object updateValueObj, String condKey, Object condValueObj) {
		update(table, List.of(updateKey), List.of(updateValueObj), List.of(condKey), List.of(condValueObj));
	}

	@Deprecated
	protected void update(String table, String updateKey, Object updateValueObj, List<String> condKeys, List<Object> condValuesObj) {
		update(table, List.of(updateKey), List.of(updateValueObj), condKeys, condValuesObj);
	}

	@Deprecated
	protected void update(String table, List<String> updateKeys, List<Object> updateValuesObj, String condKey, Object condValueObj) {
		update(table, updateKeys, updateValuesObj, List.of(condKey), List.of(condValueObj));
	}

	@Deprecated
	protected void update(final String table, final List<String> updateKeys, final List<Object> updateValuesObj, final List<String> condKeys, final List<Object> condValuesObj) {
		List<String> updateValues = new ArrayList<String>(updateValuesObj.size());
		for (Object obj : updateValuesObj) {
			updateValues.add(quote(obj));
		}
		List<String> condValues = new ArrayList<String>(condValuesObj.size());
		for (Object obj : condValuesObj) {
			condValues.add(quote(obj));
		}

		String sql = "UPDATE "+table+" SET ";
		for (int i = 0; i<updateKeys.size(); i++) {
			if (i > 0) {
				sql += ", ";
			}
			sql += updateKeys.get(i)+"="+updateValues.get(i);
		}
		sql += " WHERE ";
		for (int i = 0; i<condKeys.size(); i++) {
			if (i > 0) {
				sql += " AND ";
			}
			sql += condKeys.get(i)+"="+condValues.get(i);
		}

		util.logger.debug(sql);
		try (PreparedStatement st = util.prepareStatement(sql)) {
			st.executeUpdate();
		} catch (SQLException ex) {
			util.logger.warn("DB SQLite: Error at UPDATE\nrequest: {}", sql, ex);
		}
	}

	// DELETE sql
	@Deprecated
	protected void delete(String table, String condKey, Object condValueObj) {
		delete(table, List.of(condKey), List.of(condValueObj));
	}

	@Deprecated
	protected void delete(final String table, final List<String> condKeys, final List<Object> condValuesObj) {
		List<String> condValues = new ArrayList<String>(condValuesObj.size());
		for (Object obj : condValuesObj) {
			condValues.add(quote(obj));
		}

		String sql = "DELETE FROM "+table+" WHERE ";
		for (int i = 0; i<condKeys.size(); i++) {
			if (i > 0) {
				sql += " AND ";
			}
			sql += condKeys.get(i)+"="+condValues.get(i);
		}

		util.logger.debug(sql);
		try (PreparedStatement st = util.prepareStatement(sql)) {
			st.executeUpdate();
		} catch (SQLException ex) {
			util.logger.warn("DB SQLite: Error at DELETE\nrequest: {}", sql, ex);
		}
	}

	@Deprecated
	protected void deleteAll(final String table, final String condKey, final Object condValue) {
		String sql = "DELETE FROM "+table+" WHERE "+condKey+"="+quote(condValue);

		util.logger.debug(sql);
		try (PreparedStatement st = util.prepareStatement(sql)) {
			st.executeUpdate();
		} catch (SQLException ex) {
			util.logger.warn("DB SQLite: Error at DELETE\nrequest: {}", sql, ex);
		}
	}

	//  specific delete request
	@Deprecated
	protected void deleteExpired(final String table, final Long timeNow) {
		String sql = "DELETE FROM %s WHERE (expiresAt<=%d)".formatted(table, timeNow);

		util.logger.debug(sql);
		try (PreparedStatement st = util.prepareStatement(sql)) {
			st.executeUpdate();
		} catch (SQLException ex) {
			util.logger.warn("DB SQLite: Error at DELETE\nrequest: {}", sql, ex);
		}
	}

	// Rewrite

	// Execute statement
	protected void execute(final String sql) {
		util.logger.debug(sql);
		try (PreparedStatement st = util.prepareStatement(sql)) {
			st.executeUpdate();
		} catch (SQLException ex) {
			util.logger.warn("DB SQLite: Error at statement execution\nRequest: {}", sql, ex);
		}
	}

	// Select
	protected <T> T selectOne(final String sql, String selectKey, Class<T> selectClass) {
		T result = null;

		util.logger.debug(sql);
		try (PreparedStatement st = util.prepareStatement(sql)) {
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
		try (PreparedStatement st = util.prepareStatement(sql)) {
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

	protected Map<String, Object> selectOne(final String sql, final List<String> selectKeys) {
		Map<String, Object> result = new HashMap<>();

		util.logger.debug(sql);
		try (PreparedStatement st = util.prepareStatement(sql)) {
			ResultSet rs = st.executeQuery();

			for (String key : selectKeys) {
				result.put(key, rs.getObject(key));
			}
		} catch (SQLException ex) {
			util.logger.warn("DB SQLite: Error at SELECT\nRequest: {}", sql, ex);
		}
		return result;
	}

	protected List<Map<String, Object>> select(final String sql, final List<String> selectKeys) {
		List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();

		util.logger.debug(sql);
		try (PreparedStatement st = util.prepareStatement(sql)) {
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
		try (PreparedStatement st = util.prepareStatement(sql)) {
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


	// UTILS
	protected String quote(Object value) {
		// Convert to string and replace '(single quote) with ''(2 single quotes) for sql
		if (value == null) return "NULL";
		String str = String.valueOf(value);
		if (str == "NULL") return str;

		return "'" + String.valueOf(value).replaceAll("'", "''") + "'"; // smt's -> 'smt''s'
	}

}
