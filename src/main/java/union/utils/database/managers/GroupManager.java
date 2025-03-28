package union.utils.database.managers;

import java.sql.SQLException;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import union.utils.database.LiteDBBase;
import union.utils.database.ConnectionUtil;

public class GroupManager extends LiteDBBase {

	private final String groups = "groups";
	private final String members = "groupMembers";
	
	public GroupManager(ConnectionUtil cu) {
		super(cu, null);
	}

	// groups table
	public int create(long guildId, String name, String selfInvite) throws SQLException {
		return executeWithRow("INSERT INTO %s(ownerId, name, selfInvite) VALUES (%d, %s, %s)"
			.formatted(groups, guildId, quote(name), quote(selfInvite)));
	}

	public void deleteGroup(int groupId) throws SQLException {
		execute("DELETE FROM %s WHERE (groupId=%d)".formatted(groups, groupId));
	}

	public void deleteGuildGroups(long guildId) throws SQLException {
		execute("DELETE FROM %s WHERE (ownerId=%d)".formatted(groups, guildId));
	}

	public void rename(int groupId, String name) throws SQLException {
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

	public void setSelfInvite(int groupId, @Nullable String selfInvite) throws SQLException {
		execute("UPDATE %s SET selfInvite=%s WHERE (groupId=%d)".formatted(groups, quote(selfInvite), groupId));
	}

	public String getSelfInvite(int groupId) {
		return selectOne("SELECT selfInvite FROM %s WHERE (groupId=%d)".formatted(groups, groupId), "selfInvite", String.class);
	}

	public int getVerifyValue(int groupId) {
		Integer data = selectOne("SELECT memberVerify FROM %s WHERE (groupId=%d)".formatted(groups, groupId), "memberVerify", Integer.class);
		return data==null ? -1 : data;
	}

	public void setVerify(int groupId, int value) throws SQLException {
		execute("UPDATE %s SET memberVerify=%d WHERE (groupId=%d)".formatted(groups, value, groupId));
	}

	// groupMembers table
	public void add(int groupId, long guildId, Boolean canManage) throws SQLException {
		execute("INSERT INTO %s(groupId, guildId, canManage) VALUES (%d, %d, %d)".formatted(members, groupId, guildId, canManage ? 1 : 0));
	}

	public void remove(int groupId, long guildId) throws SQLException {
		execute("DELETE FROM %s WHERE (groupId=%d AND guildId=%d)".formatted(members, groupId, guildId));
	}

	public void removeGuildFromGroups(long guildId) throws SQLException {
		execute("DELETE FROM %s WHERE (guildId=%d)".formatted(members, guildId));
	}
	
	public void clearGroup(int groupId) throws SQLException {
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
		return data != null && data == 1;
	}

	public void setManage(int groupId, long guildId, boolean canManage) throws SQLException {
		execute("UPDATE %s SET canManage=%d WHERE (groupId=%d AND guildId=%d)".formatted(members, canManage ? 1 : 0, groupId, guildId));
	}

}
