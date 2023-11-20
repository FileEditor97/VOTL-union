package union.utils.database.managers;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import union.utils.database.DBUtil;
import union.utils.database.LiteDBBase;

public class TicketManager extends LiteDBBase {
	
	private final String TABLE = "ticket";

	public TicketManager(DBUtil util) {
		super(util);
	}

	/* tags:
	 *  0 - role request ticket
	 *  1+ - custom tags
	 */

	// add new ticket
	public void addRoleTicket(Integer ticketId, String userId, String guildId, String channelId, String roleIds) {
		insert(TABLE, List.of("ticketId", "userId", "guildId", "channelId", "tagId", "roleIds"),
			List.of(ticketId, userId, guildId, channelId, 0, roleIds));
	}

	public void addTicket(Integer ticketId, String userId, String guildId, String channelId, Integer tagId) {
		insert(TABLE, List.of("ticketId", "userId", "guildId", "channelId", "tagId"),
			List.of(ticketId, userId, guildId, channelId, tagId));
	}

	// get last ticket's ID
	public Integer lastIdByTag(String guildId, Integer tagId) {
		Integer data = selectLastTicketId(TABLE, guildId, tagId);
		if (data == null) return 0;
		return data;
	}

	// update mod
	public void setClaimed(String channelId, String modId) {
		update(TABLE, "modId", modId, "channelId", channelId);
	}

	public void setUnclaimed(String channelId) {
		update(TABLE, "modId", "NULL", "channelId", channelId);
	}

	public String getClaimer(String channelId) {
		Object data = selectOne(TABLE, "modId", "channelId", channelId);
		if (data == null) return null;
		return (String) data;
	}

	// set status
	public void closeTicket(Instant timeClosed, String channelId, String reason) {
		update(TABLE, List.of("closed", "timeClosed", "reasonClosed"), List.of(1, timeClosed.getEpochSecond(), Optional.ofNullable(reason).orElse("NULL")), "channelId", channelId);
	}

	// get status
	public boolean isOpened(String channelId) {
		if (selectOne(TABLE, "ticketId", List.of("channelId", "closed"), List.of(channelId, 0)) == null) return false;
		return true;
	}

	public String getOpenedChannel(String userId, String guildId, Integer tagId) {
		Object data = selectOne(TABLE, "channelId", List.of("userId", "guildId", "tagId", "closed"), List.of(userId, guildId, tagId, 0));
		if (data == null) return null;
		return String.valueOf(data);
	}

	public Integer countOpenedByUser(String userId, String guildId, Integer tagId) {
		Object data = countSelect(TABLE, List.of("userId", "guildId", "tagId", "closed"), List.of(userId, guildId, tagId, 0));
		if (data == null) return null;
		return Integer.valueOf(data.toString());
	}

	public Integer countAllOpenedByUser(String userId, String guildId) {
		Object data = countSelect(TABLE, List.of("userId", "guildId", "closed"), List.of(userId, guildId, 0));
		if (data == null) return null;
		return Integer.valueOf(data.toString());
	}

	public List<String> getOpenedChannels() {
		List<Object> data = select(TABLE, "channelId", List.of("closed", "closeRequested"), List.of(0, 0));
		if (data.isEmpty()) return Collections.emptyList();
		return data.stream().map(e -> (String) e).toList();
	}

	public List<String> getExpiredTickets() {
		return getExpiredTickets(TABLE, Instant.now().getEpochSecond());
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
		return String.valueOf(data); // Cast-ing will not work, as it is integer value
	}

	public Boolean isRoleTicket(String channelId) {
		Object data = selectOne(TABLE, "tagId", "channelId", channelId);
		if (data == null) return false;
		return ((Integer) data) == 0;
	}

	public Integer countTicketsByMod(String guildId, String modId, Instant afterTime, Instant beforeTime, boolean roleTag) {
		return countTicketsClaimed(TABLE, guildId, modId, afterTime.getEpochSecond(), beforeTime.getEpochSecond(), roleTag);
	}

	/**
	 * Close requested:<p>
	 *  0 - not requested;
	 *  >1 - requested, await, close when time expires;  
	 *  <-1 - closure canceled, do not request.
	 * @param channelId
	 * @param closeRequested
	 */
	public void setRequestStatus(String channelId, Long closeRequested) {
		update(TABLE, "closeRequested", closeRequested, "channelId", channelId);
	}

	public Long getCloseTime(String channelId) {
		Object data = selectOne(TABLE, "closeRequested", "channelId", channelId);
		if (data == null) return 0L;
		return ((Number) data).longValue();
	}

}
