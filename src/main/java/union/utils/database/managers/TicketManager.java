package union.utils.database.managers;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import union.utils.database.DBUtil;
import union.utils.database.LiteDBBase;

public class TicketManager extends LiteDBBase {
	
	private final String TABLE = "ticket";

	public TicketManager(DBUtil util) {
		super(util);
	}

	// add new ticket
	public void addRoleTicket(Integer ticketId, String userId, String guildId, String channelId, String alias, String roleIds) {
		insert(TABLE, List.of("ticketId", "userId", "guildId", "channelId", "alias", "roleIds"),
			List.of(ticketId, userId, guildId, channelId, alias, roleIds));
	}

	// get last ticket's ID
	public Integer lastId(String guildId) {
		Object data = selectLast(TABLE, "ticketId", "guildId", guildId);
		if (data == null) return 0;
		return Integer.parseInt(data.toString());
	}

	// update mod
	public void updateMod(String channelId, String modId) {
		update(TABLE, "modId", modId, "channelId", channelId);
	}

	// update status
	public void closeTicket(Instant timeClosed, String channelId) {
		update(TABLE, List.of("closed", "timeClosed"), List.of(1, timeClosed.getEpochSecond()), "channelId", channelId);
	}

	public String getOpenedTicket(String userId, String guildId) {
		Object data = selectOne(TABLE, "channelId", List.of("userId", "guildId", "closed"), List.of(userId, guildId, 0));
		if (data == null) return null;
		return String.valueOf(data);
	}

	public boolean isOpened(String channelId) {
		if (selectOne(TABLE, "ticketId", List.of("channelId", "closed"), List.of(channelId, 0)) == null) return false;
		return true;
	}

	public List<String> getRoleIds(String channelId) {
		Object data = selectOne(TABLE, "roleIds", "channelId", channelId);
		if (data == null || data.equals("")) return Collections.emptyList();
		return Arrays.asList(String.valueOf(data).split(";"));
	}

	public String getUserId(String channelId) {
		Object data = selectOne(TABLE, "userId", "channelId", channelId);
		if (data == null) return null;
		return String.valueOf(data);
	}

	public String getTicketId(String channelId) {
		Object data = selectOne(TABLE, "ticketId", "channelId", channelId);
		if (data == null) return null;
		return String.valueOf(data);
	}

	public void setAccepted(String modId, String channelId) {
		update(TABLE, "modId", modId, "channelId", channelId);
	}

	public Integer countTicketsByMod(String guildId, String modId, Instant afterTime, Instant beforeTime) {
		List<Integer> data = selectAfterTime(TABLE, guildId, modId, afterTime.getEpochSecond(), beforeTime.getEpochSecond());
		return data.size();
	}


}
