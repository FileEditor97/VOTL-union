package union.utils.database.managers;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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
	public void addRoleTicket(int ticketId, long userId, long guildId, long channelId, String roleIds, int replyTime) {
		try {
			execute("INSERT INTO %s(ticketId, userId, guildId, channelId, tagId, roleIds, replyWait) VALUES (%d, %s, %s, %s, 0, %s, %d)"
				.formatted(table, ticketId, userId, guildId, channelId, quote(roleIds), replyTime>0 ? Instant.now().plus(replyTime, ChronoUnit.HOURS).getEpochSecond() : 0));
		} catch (SQLException ignored) {}
	}

	public void addTicket(int ticketId, long userId, long guildId, long channelId, int tagId, int replyTime) {
		try {
			execute("INSERT INTO %s(ticketId, userId, guildId, channelId, tagId, replyWait) VALUES (%d, %s, %s, %s, %d, %d)"
				.formatted(table, ticketId, userId, guildId, channelId, tagId, replyTime>0 ? Instant.now().plus(replyTime, ChronoUnit.HOURS).getEpochSecond() : 0));
		} catch (SQLException ignored) {}
	}

	// get last ticket's ID
	public int lastIdByTag(long guildId, int tagId) {
		Integer data = selectOne("SELECT ticketId FROM %s WHERE (guildId=%s AND tagId=%d) ORDER BY ticketId DESC LIMIT 1"
			.formatted(table, guildId, tagId), "ticketId", Integer.class);
		return data == null ? 0 : data;
	}

	// update mod
	public void setClaimed(long channelId, long modId) {
		try {
			execute("UPDATE %s SET modId=%s WHERE (channelId=%s)".formatted(table, modId, channelId));
		} catch (SQLException ignored) {}
	}

	public void setUnclaimed(long channelId) {
		try {
			execute("UPDATE %s SET modId=NULL WHERE (channelId=%s)".formatted(table, channelId));
		} catch (SQLException ignored) {}
	}

	public Long getClaimer(long channelId) {
		return selectOne("SELECT modId FROM %s WHERE (channelId=%s)".formatted(table, channelId), "modId", Long.class);
	}

	// set status
	public void closeTicket(Instant timeClosed, long channelId, String reason) throws SQLException {
		execute("UPDATE %s SET closed=1, timeClosed=%d, reasonClosed=%s WHERE (channelId=%s)".formatted(table, timeClosed.getEpochSecond(), quote(reason), channelId));
	}

	public void forceCloseTicket(long channelId) {
		try {
			execute("UPDATE %s SET closed=1 WHERE (channelId=%s)".formatted(table, channelId));
		} catch (SQLException ignored) {}
	}

	// get status
	public boolean isClosed(long channelId) {
		Integer data = selectOne("SELECT closed FROM %s WHERE (channelId=%s)".formatted(table, channelId), "closed", Integer.class);
		return data == null || data != 0;
	}

	public Long getOpenedChannel(long userId, long guildId, int tagId) {
		return selectOne("SELECT channelId FROM %s WHERE (userId=%s AND guildId=%s AND tagId=%s AND closed=0)".formatted(table, userId, guildId, tagId),
			"channelId", Long.class);
	}

	public List<Long> getOpenedChannel(long userId, long guildId) {
		return select("SELECT channelId FROM %s WHERE (userId=%s AND guildId=%s AND closed=0)".formatted(table, userId, guildId),
			"channelId", Long.class);
	}

	public List<Long> getOpenedChannels() {
		return select("SELECT channelId FROM %s WHERE (closed=0 AND closeRequested=0)".formatted(table), "channelId", Long.class);
	}

	public List<Long> getCloseMarkedTickets() {
		return select("SELECT channelId FROM %s WHERE (closed=0 AND closeRequested>0 AND closeRequested<=%d)".formatted(table, Instant.now().getEpochSecond()),
			"channelId", Long.class);
	}

	public List<Long> getReplyExpiredTickets() {
		return select("SELECT channelId FROM %s WHERE (closed=0 AND replyWait>0 AND replyWait<=%d)".formatted(table, Instant.now().getEpochSecond()),
			"channelId", Long.class);
	}

	public List<String> getRoleIds(long channelId) {
		String data = selectOne("SELECT roleIds FROM %s WHERE (channelId=%s)".formatted(table, channelId), "roleIds", String.class);
		if (data == null) return Collections.emptyList();
		return Arrays.asList(data.split(";"));
	}

	public Long getUserId(long channelId) {
		return selectOne("SELECT userId FROM %s WHERE (channelId=%s)".formatted(table, channelId), "userId", Long.class);
	}

	public String getTicketId(long channelId) {
		return selectOne("SELECT ticketId FROM %s WHERE (channelId=%s)".formatted(table, channelId), "ticketId", String.class);
	}

	public Boolean isRoleTicket(long channelId) {
		Integer data = selectOne("SELECT tagId FROM %s WHERE (channelId=%s)".formatted(table, channelId), "tagId", Integer.class);
		return data != null && data == 0;
	}

	public Integer getTag(long channelId) {
		return selectOne("SELECT tagId FROM %s WHERE (channelId=%s)".formatted(table, channelId), "tagId", Integer.class);
	}

	public int countTicketsByMod(long guildId, long modId, LocalDateTime afterTime, LocalDateTime beforeTime, boolean roleTag) {
		String tagType = roleTag ? "tagId=0" : "tagId>=1";
		return count("SELECT COUNT(*) FROM %s WHERE (guildId=%s AND modId=%s AND timeClosed>=%d AND timeClosed<=%d AND %s)"
			.formatted(table, guildId, modId, afterTime.toEpochSecond(ZoneOffset.UTC), beforeTime.toEpochSecond(ZoneOffset.UTC), tagType));
	}

	public int countTicketsByMod(long guildId, long modId, Instant afterTime, boolean roleTag) {
		String tagType = roleTag ? "tagId=0" : "tagId>=1";
		return count("SELECT COUNT(*) FROM %s WHERE (guildId=%s AND modId=%s AND timeClosed>=%d AND %s)"
			.formatted(table, guildId, modId, afterTime.getEpochSecond(), tagType));
	}

	public int countTicketsByMod(long guildId, long modId, boolean roleTag) {
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
	public void setRequestStatus(long channelId, long closeRequested) {
		try {
			execute("UPDATE %s SET closeRequested=%d WHERE (channelId=%s)".formatted(table, closeRequested, channelId));
		} catch (SQLException ignored) {}
	}

	public void setRequestStatus(long channelId, long closeRequested, String reason) {
		try {
			execute("UPDATE %s SET closeRequested=%d, reasonClosed=%s WHERE (channelId=%s)".formatted(table, closeRequested, quote(reason), channelId));
		} catch (SQLException ignored) {}
	}

	public long getTimeClosing(long channelId) {
		Long data = selectOne("SELECT closeRequested FROM %s WHERE (channelId=%s);".formatted(table, channelId), "closeRequested", Long.class);
		return data == null ? 0L : data;
	}

	public void setWaitTime(long channelId, long time) {
		try {
			execute("UPDATE %s SET replyWait=%d WHERE (channelId=%s)".formatted(table, time, channelId));
		} catch (SQLException ignored) {}
	}
}
