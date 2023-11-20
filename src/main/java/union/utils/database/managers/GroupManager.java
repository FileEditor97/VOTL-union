package union.utils.database.managers;

import java.util.Collections;
import java.util.List;

import union.utils.database.LiteDBBase;
import union.utils.database.DBUtil;

public class GroupManager extends LiteDBBase {

	private final String TABLE_MASTER = "groupMaster";
	private final String TABLE_MEMBERS = "groupMembers";
	
	public GroupManager(DBUtil util) {
		super(util);
	}

	// groupMaster table
	public void create(String guildId, String name, Boolean isShared) {
		insert(TABLE_MASTER, List.of("masterId", "name", "isShared"), List.of(guildId, name, (isShared ? 1 : 0)));
	}

	public Integer getIncrement() {
		return getIncrement(TABLE_MASTER);
	}

	public void delete(Integer groupId) {
		delete(TABLE_MASTER, "groupId", groupId);
	}

	public void deleteAll(String guildId) {
		delete(TABLE_MASTER, "masterId", guildId);
	}

	public void rename(Integer groupId, String name) {
		update(TABLE_MASTER, "name", name, "groupId", groupId);
	}

	public String getMaster(Integer groupId) {
		Object data = selectOne(TABLE_MASTER, "masterId", "groupId", groupId);
		if (data == null) return null;
		return (String) data;
	}

	public List<Integer> getOwnedGroups(String guildId) {
		List<Object> data = select(TABLE_MASTER, "groupId", "masterId", guildId);
		if (data.isEmpty()) return Collections.emptyList();
		return data.stream().map(obj -> (Integer) obj).toList();
	}

	public String getName(Integer groupId) {
		Object data = selectOne(TABLE_MASTER, "name", "groupId", groupId);
		if (data == null) return null;
		return (String) data;
	}

	public Boolean isShared(Integer groupId) {
		Object data = selectOne(TABLE_MASTER, "isShared", "groupId", groupId);
		if (data == null) return false;
		return (Integer) data == 1;
	}

	// groupMembers table
	public void add(Integer groupId, String guildId, Boolean canManage) {
		insert(TABLE_MEMBERS, List.of("groupId", "guildId", "canManage"), List.of(groupId, guildId, (canManage ? 1 : 0)));
	}

	public void remove(Integer groupId, String guildId) {
		delete(TABLE_MEMBERS, List.of("groupId", "guildId"), List.of(groupId, guildId));
	}

	public void removeFromGroups(String guildId) {
		delete(TABLE_MEMBERS, "guildId", guildId);
	}
	
	public void clearGroup(Integer groupId) {
		delete(TABLE_MEMBERS, "groupId", groupId);
	}

	public Boolean alreadyMember(Integer groupId, String guildId) {
		Object data = selectOne(TABLE_MEMBERS, "groupId", List.of("groupId", "guildId"), List.of(groupId, guildId));
		if (data == null) return false;
		return true;
	}

	public List<String> getGroupGuildIds(Integer groupId) {
		List<Object> data = select(TABLE_MEMBERS, "guildId", "groupId", groupId);
		if (data.isEmpty()) return Collections.emptyList();
		return data.stream().map(obj -> String.valueOf(obj)).toList();
	}

	public List<String> getGroupGuildIds(Integer groupId, Boolean whitelisted) {
		List<Object> data = select(TABLE_MEMBERS, "guildId", List.of("groupId", "whitelisted"), List.of(groupId, (whitelisted ? 1 : 0)));
		if (data.isEmpty()) return Collections.emptyList();
		return data.stream().map(obj -> String.valueOf(obj)).toList();
	}

	public List<Integer> getGuildGroups(String guildId) {
		List<Object> data = select(TABLE_MEMBERS, "groupId", "guildId", guildId);
		if (data.isEmpty()) return Collections.emptyList();
		return data.stream().map(obj -> (Integer) obj).toList();
	}

	public List<Integer> getManagedGroups(String guildId) {
		List<Object> data = select(TABLE_MEMBERS, "groupId", List.of("guildId", "canManage"), List.of(guildId, 1));
		if (data.isEmpty()) return Collections.emptyList();
		return data.stream().map(obj -> (Integer) obj).toList();
	}

	public List<String> getGroupManagers(Integer groupId) {
		List<Object> data = select(TABLE_MEMBERS, "guildId", List.of("groupId", "canManage"), List.of(groupId, 1));
		if (data.isEmpty()) return Collections.emptyList();
		return data.stream().map(obj -> String.valueOf(obj)).toList();
	} 

	public Boolean canManage(Integer groupId, String guildId) {
		Object data = selectOne(TABLE_MEMBERS, "canManage", List.of("groupId", "guildId"), List.of(groupId, guildId));
		if (data == null) return false;
		return (Integer) data == 1 ? true : false;
	}

	public void setManage(Integer groupId, String guildId, Boolean canManage) {
		update(TABLE_MEMBERS, "canManage", (canManage ? 1 : 0), List.of("groupId", "guildId"), List.of(groupId, guildId));
	}

	public void setWhitelisted(String guildId, Boolean whitelisted) {
		update(TABLE_MEMBERS, "whitelisted", (whitelisted ? 1 : 0), "guildId", guildId);
	}

}
