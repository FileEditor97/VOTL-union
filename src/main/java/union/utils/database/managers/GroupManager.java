package union.utils.database.managers;

import java.util.List;

import union.utils.database.LiteDBBase;
import union.utils.database.ConnectionUtil;

public class GroupManager extends LiteDBBase {

	private final String table_masters = "groupMaster";
	private final String table_members = "groupMembers";
	
	public GroupManager(ConnectionUtil cu) {
		super(cu);
	}

	// groupMaster table
	public void create(String guildId, String name) {
		execute("INSERT INTO %s(masterId, name) VALUES (%s, %s)".formatted(table_masters, guildId, quote(name)));
	}

	public Integer getIncrement() {
		return getIncrement(table_masters);
	}

	public void delete(Integer groupId) {
		execute("DELETE FROM %s WHERE (groupId=%d)".formatted(table_masters, groupId));
	}

	public void deleteAll(String guildId) {
		execute("DELETE FROM %s WHERE (masterId=%s)".formatted(table_masters, guildId));
	}

	public void rename(Integer groupId, String name) {
		execute("UPDATE %s SET name=%s WHERE (groupId=%d)".formatted(table_masters, quote(name), groupId));
	}

	public String getMaster(Integer groupId) {
		return selectOne("SELECT masterId FROM %s WHERE (groupId=%d)".formatted(table_masters, groupId), "masterId", String.class);
	}

	public List<Integer> getOwnedGroups(String guildId) {
		return select("SELECT groupId FROM %s WHERE (masterId=%s)".formatted(table_masters, guildId), "groupId", Integer.class);
	}

	public String getName(Integer groupId) {
		return selectOne("SELECT name FROM %s WHERE (groupId=%d)".formatted(table_masters, groupId), "name", String.class);
	}

	public boolean isMaster(int groupId, String guildId) {
		return selectOne("SELECT masterId FROM %s WHERE (groupId=%d AND masterId=%s)"
			.formatted(table_masters, groupId, guildId), "masterId", String.class) != null;
	}

	// groupMembers table
	public void add(Integer groupId, String guildId, Boolean canManage) {
		execute("INSERT INTO %s(groupId, guildId, canManage) VALUES (%d, %s, %d)".formatted(table_members, groupId, guildId, canManage ? 1 : 0));
	}

	public void remove(Integer groupId, String guildId) {
		execute("DELETE FROM %s WHERE (groupId=%d AND guildId=%s)".formatted(table_members, groupId, guildId));
	}

	public void removeFromGroups(String guildId) {
		execute("DELETE FROM %s WHERE (guildId=%s)".formatted(table_members, guildId));
	}
	
	public void clearGroup(Integer groupId) {
		execute("DELETE FROM %s WHERE (groupId=%d)".formatted(table_members, groupId));
	}

	public Boolean alreadyMember(Integer groupId, String guildId) {
		return selectOne("SELECT guildId FROM %s WHERE (groupId=%d AND guildId=%s)".formatted(table_members, groupId, guildId), "guildId", String.class) != null;
	}

	public List<String> getGroupGuildIds(Integer groupId) {
		return select("SELECT guildId FROM %s WHERE (groupId=%d)".formatted(table_members, groupId), "guildId", String.class);
	}

	public List<String> getGroupGuildIds(Integer groupId, Boolean whitelisted) {
		return select("SELECT guildId FROM %s WHERE (groupId=%d AND whitelisted=%d)".formatted(table_members, groupId, whitelisted ? 1 : 0),
			"guildId", String.class);
	}

	public List<Integer> getGuildGroups(String guildId) {
		return select("SELECT groupId FROM %s WHERE (guildId=%s)".formatted(table_members, guildId), "groupId", Integer.class);
	}

	public List<Integer> getManagedGroups(String guildId) {
		return select("SELECT groupId FROM %s WHERE (guildId=%s AND canManage=1)".formatted(table_members, guildId), "groupId", Integer.class);
	}

	public List<String> getGroupManagers(Integer groupId) {
		return select("SELECT guildId FROM %s WHERE (groupId=%d AND canManage=1)".formatted(table_members, groupId), "guildId", String.class);
	} 

	public boolean canManage(Integer groupId, String guildId) {
		Integer data = selectOne("SELECT canManage FROM %s WHERE (groupId=%d AND guildId=%s)".formatted(table_members, groupId, guildId), "canManage", Integer.class);
		return data==null ? false : data==1;
	}

	public void setManage(Integer groupId, String guildId, Boolean canManage) {
		execute("UPDATE %s SET canManage=%d WHERE (groupId=%d AND guildId=%s)".formatted(table_members, canManage ? 1 : 0, groupId, guildId));
	}

	public void setWhitelisted(String guildId, Boolean whitelisted) {
		execute("UPDATE %s SET whitelisted=%d WHERE (guildId=%s)".formatted(table_members, whitelisted ? 1 : 0, guildId));
	}

}
