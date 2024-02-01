package union.utils.database.managers;

import java.util.List;

import union.utils.database.LiteDBBase;
import union.utils.database.ConnectionUtil;

public class GroupManager extends LiteDBBase {

	private final String groups = "groups";
	private final String members = "groupMembers";
	
	public GroupManager(ConnectionUtil cu) {
		super(cu);
	}

	// groups table
	public void create(long guildId, String name, long appealGuildId) {
		execute("INSERT INTO %s(ownerId, name, appealGuildId) VALUES (%d, %s, %d)".formatted(groups, guildId, quote(name), appealGuildId));
	}

	public int getIncrement() {
		return getIncrement(groups);
	}

	public void deleteGroup(int groupId) {
		execute("DELETE FROM %s WHERE (groupId=%d)".formatted(groups, groupId));
	}

	public void deleteGuildGroups(long guildId) {
		execute("DELETE FROM %s WHERE (ownerId=%d)".formatted(groups, guildId));
	}

	public void rename(int groupId, String name) {
		execute("UPDATE %s SET name=%s WHERE (groupId=%d)".formatted(groups, quote(name), groupId));
	}

	public Long getOwner(int groupId) {
		return selectOne("SELECT ownerId FROM %s WHERE (groupId=%d)".formatted(groups, groupId), "ownerId", Long.class);
	}

	public List<Integer> getOwnedGroups(long guildId) {
		return select("SELECT groupId FROM %s WHERE (ownerId=%d)".formatted(groups, guildId), "groupId", Integer.class);
	}

	public String getName(int groupId) {
		return selectOne("SELECT name FROM %s WHERE (groupId=%d)".formatted(groups, groupId), "name", String.class);
	}

	public boolean isOwner(int groupId, long guildId) {
		return selectOne("SELECT ownerId FROM %s WHERE (groupId=%d AND ownerId=%d)"
			.formatted(groups, groupId, guildId), "ownerId", Long.class) != null;
	}

	public Long getAppealGuildId(int groupId) {
		Long data = selectOne("SELECT appealGuildId FROM %s WHERE (groupId=%d)".formatted(groups, groupId), "appealGuildId", Long.class);
		return data==null ? 0L : data;
	}

	// groupMembers table
	public void add(int groupId, long guildId, Boolean canManage) {
		execute("INSERT INTO %s(groupId, guildId, canManage) VALUES (%d, %d, %d)".formatted(members, groupId, guildId, canManage ? 1 : 0));
	}

	public void remove(int groupId, long guildId) {
		execute("DELETE FROM %s WHERE (groupId=%d AND guildId=%d)".formatted(members, groupId, guildId));
	}

	public void removeGuildFromGroups(long guildId) {
		execute("DELETE FROM %s WHERE (guildId=%d)".formatted(members, guildId));
	}
	
	public void clearGroup(int groupId) {
		execute("DELETE FROM %s WHERE (groupId=%d)".formatted(members, groupId));
	}

	public Boolean isMember(int groupId, long guildId) {
		return selectOne("SELECT guildId FROM %s WHERE (groupId=%d AND guildId=%d)".formatted(members, groupId, guildId), "guildId", Long.class) != null;
	}

	public List<Long> getGroupMembers(int groupId) {
		return select("SELECT guildId FROM %s WHERE (groupId=%d)".formatted(members, groupId), "guildId", Long.class);
	}

	public int countMembers(int groupId) {
		return count("SELECT COUNT(guildId) FROM %s WHERE (groupId=%d)".formatted(members, groupId));
	}

	public List<Integer> getGuildGroups(long guildId) {
		return select("SELECT groupId FROM %s WHERE (guildId=%d)".formatted(members, guildId), "groupId", Integer.class);
	}

	public List<Integer> getManagedGroups(long guildId) {
		return select("SELECT groupId FROM %s WHERE (guildId=%d AND canManage=1)".formatted(members, guildId), "groupId", Integer.class);
	}

	public List<Long> getGroupManagers(int groupId) {
		return select("SELECT guildId FROM %s WHERE (groupId=%d AND canManage=1)".formatted(members, groupId), "guildId", Long.class);
	} 

	public boolean canManage(int groupId, long guildId) {
		Integer data = selectOne("SELECT canManage FROM %s WHERE (groupId=%d AND guildId=%d)".formatted(members, groupId, guildId), "canManage", Integer.class);
		return data==null ? false : data==1;
	}

	public void setManage(int groupId, long guildId, Boolean canManage) {
		execute("UPDATE %s SET canManage=%d WHERE (groupId=%d AND guildId=%d)".formatted(members, canManage ? 1 : 0, groupId, guildId));
	}

}
