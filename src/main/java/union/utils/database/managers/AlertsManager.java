package union.utils.database.managers;

import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

public class AlertsManager extends LiteDBBase {
	
	public AlertsManager(ConnectionUtil cu) {
		super(cu, "alertActions");
	}

	public void addPoint(long guildId, long userId) {
		execute("INSERT INTO %s(guildId, userId, points) VALUES (%d, %d, 1) ON CONFLICT(guildId, userId) DO UPDATE SET points=points+1".formatted(table, guildId, userId));
	}

	public void removePoint() {
		execute("UPDATE %s SET points=points-1; DELETE FROM %<s WHERE points<1;".formatted(table));
	}

	public void removeAll() {
		execute("DELETE FROM %s".formatted(table));
	}

	public int getPoints(long guildId, long userId) {
		Integer data = selectOne("SELECT points FROM %s WHERE (guildId=%d AND userId=%d)".formatted(table, guildId, userId), "points", Integer.class);
		return data==null ? 0 : data;
	}

}
