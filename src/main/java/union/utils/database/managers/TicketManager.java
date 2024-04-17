package union.utils.database.managers;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

public class TicketManager extends LiteDBBase {

	public TicketManager(ConnectionUtil cu) {
		super(cu, "ticket");
	}

	/* tags:
	 *  0 - role request ticket
	 *  1+ - custom tags
	 */

	// add new ticket
	public void addRoleTicket(int ticketId, String userId, String guildId, String channelId, String roleIds) {
		execute("INSERT INTO %s(ticketId, userId, guildId, channelId, tagId, roleIds) VALUES (%d, %s, %s, %s, 0, %s)"
			.formatted(table, ticketId, userId, guildId, channelId, quote(roleIds)));
	}

	public void addTicket(int ticketId, String userId, String guildId, String channelId, int tagId) {
		execute("INSERT INTO %s(ticketId, userId, guildId, channelId, tagId) VALUES (%d, %s, %s, %s, %d)".formatted(table, ticketId, userId, guildId, channelId, tagId));
	}

	// get last ticket's ID
	public int lastIdByTag(String guildId, int tagId) {
		Integer data = selectOne("SELECT ticketId FROM %s WHERE (guildId=%s AND tagId=%d) ORDER BY ticketId DESC LIMIT 1"
			.formatted(table, guildId, tagId), "ticketId", Integer.class);
		return data == null ? 0 : data;
	}

	// update mod
	public void setClaimed(String channelId, String modId) {
		execute("UPDATE %s SET modId=%s WHERE (channelId=%s)".formatted(table, modId, channelId));
	}

	public void setUnclaimed(String channelId) {
		execute("UPDATE %s SET modId=NULL WHERE (channelId=%s)".formatted(table, channelId));
	}

	public String getClaimer(String channelId) {
		return selectOne("SELECT modId FROM %s WHERE (channelId=%s)".formatted(table, channelId), "modId", String.class);
	}

	// set status
	public void closeTicket(Instant timeClosed, String channelId, String reason) {
		execute("UPDATE %s SET closed=1, timeClosed=%d, reasonClosed=%s WHERE (channelId=%s)".formatted(table, timeClosed.getEpochSecond(), quote(reason), channelId));
	}

	public void forceCloseTicket(String channelId) {
		execute("UPDATE %s SET closed=1 WHERE (channelId=%s)".formatted(table, channelId));
	}

	// get status
	public boolean isOpened(String channelId) {
		Integer data = selectOne("SELECT closed FROM %s WHERE (channelId=%s)".formatted(table, channelId), "closed", Integer.class);
		return data==null ? false : data==0;
	}

	public String getOpenedChannel(String userId, String guildId, int tagId) {
		return selectOne("SELECT channelId FROM %s WHERE (userId=%s AND guildId=%s AND tagId=%s AND closed=0)".formatted(table, userId, guildId, tagId),
			"channelId", String.class);
	}

	public List<String> getOpenedChannel(String userId, String guildId) {
		return select("SELECT channelId FROM %s WHERE (userId=%s AND guildId=%s AND closed=0)".formatted(table, userId, guildId),
			"channelId", String.class);
	}

	public int countOpenedByUser(String userId, String guildId, int tagId) {
		return count("SELECT COUNT(*) FROM %s WHERE (userId=%s AND guildId=%s AND tagId=%s AND closed=0)".formatted(table, userId, guildId, tagId));
	}

	public int countAllOpenedByUser(String userId, String guildId) {
		return count("SELECT COUNT(*) FROM %s WHERE (userId=%s AND guildId=%s AND closed=0)".formatted(table, userId, guildId));
	}

	public List<String> getOpenedChannels() {
		return select("SELECT channelId FROM %s WHERE (closed=0 AND closeRequested=0)".formatted(table), "channelId", String.class);
	}

	public List<String> getExpiredTickets() {
		return select("SELECT channelId FROM %s WHERE (closed=0 AND closeRequested>0 AND closeRequested<=%d)".formatted(table, Instant.now().getEpochSecond()),
			"channelId", String.class);
	}

	public List<String> getRoleIds(String channelId) {
		String data = selectOne("SELECT roleIds FROM %s WHERE (channelId=%s)".formatted(table, channelId), "roleIds", String.class);
		if (data == null) return Collections.emptyList();
		return Arrays.asList(data.split(";"));
	}

	public String getUserId(String channelId) {
		return selectOne("SELECT userId FROM %s WHERE (channelId=%s)".formatted(table, channelId), "userId", String.class);
	}

	public String getTicketId(String channelId) {
		return selectOne("SELECT ticketId FROM %s WHERE (channelId=%s)".formatted(table, channelId), "ticketId", String.class);
	}

	public Boolean isRoleTicket(String channelId) {
		Integer data = selectOne("SELECT tagId FROM %s WHERE (channelId=%s)".formatted(table, channelId), "tagId", Integer.class);
		return data==null ? false : data==0;
	}

	public int countTicketsByMod(String guildId, String modId, Instant afterTime, Instant beforeTime, boolean roleTag) {
		String tagType = roleTag ? "tagId=0" : "tagId>=1";
		return count("SELECT COUNT(*) FROM %s WHERE (guildId=%s AND modId=%s AND timeClosed>=%d AND timeClosed<=%d AND %s)"
			.formatted(table, guildId, modId, afterTime.getEpochSecond(), beforeTime.getEpochSecond(), tagType));
	}

	public int countTicketsByMod(String guildId, String modId, Instant afterTime, boolean roleTag) {
		String tagType = roleTag ? "tagId=0" : "tagId>=1";
		return count("SELECT COUNT(*) FROM %s WHERE (guildId=%s AND modId=%s AND timeClosed>=%d AND %s)"
			.formatted(table, guildId, modId, afterTime.getEpochSecond(), tagType));
	}

	public int countTicketsByMod(String guildId, String modId, boolean roleTag) {
		String tagType = roleTag ? "tagId=0" : "tagId>=1";
		return count("SELECT COUNT(*) FROM %s WHERE (guildId=%s AND modId=%s AND %s)"
			.formatted(table, guildId, modId, tagType));
	}

	/**
	 * Close requested:<p>
	 *  0 - not requested;
	 *  >1 - requested, await, close when time expires;  
	 *  <-1 - closure canceled, do not request.
	 * @param channelId Ticket's channel ID
	 * @param closeRequested Time in epoch seconds
	 */
	public void setRequestStatus(String channelId, long closeRequested) {
		execute("UPDATE %s SET closeRequested=%d WHERE (channelId=%s)".formatted(table, closeRequested, channelId));
	}

	public long getTimeClosing(String channelId) {
		Long data = selectOne("SELECT closeRequested FROM %s WHERE (channelId=%s);".formatted(table, channelId), "closeRequested", Long.class);
		return data == null ? 0L : data;

	}

}
