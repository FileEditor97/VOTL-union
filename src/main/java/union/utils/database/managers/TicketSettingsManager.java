package union.utils.database.managers;

import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

public class TicketSettingsManager extends LiteDBBase {
	
	private final String table = "ticketSettings";

	public TicketSettingsManager(ConnectionUtil cu) {
		super(cu);
	}

	public void remove(String guildId) {
		execute("DELETE FROM %s WHERE (guildId=%s)".formatted(table, guildId));
	}

	public void setRowText(String guildId, Integer row, String text) {
		execute("INSERT INTO %s(guildId, rowName) VALUES (%s, %s) ON CONFLICT(guildId) DO UPDATE SET rowName=%s".formatted(table, guildId, quote(text), quote(text)));
	}

	public String getRowText(String guildId, Integer row) {
		String data = selectOne("SELECT rowName FROM %s WHERE (guildId=%s)".formatted(table, guildId), "rowName", String.class);
		return data==null ? "Select roles" : data;
	}

	public void setAutocloseTime(String guildId, Integer hours) {
		execute("INSERT INTO %s(guildId, autocloseTime) VALUES (%s, %d) ON CONFLICT(guildId) DO UPDATE SET autocloseTime=%d".formatted(table, guildId, hours, hours));
	}

	public Integer getAutocloseTime(String guildId) {
		Integer data = selectOne("SELECT autocloseTime FROM %s WHERE (guildId=%s)".formatted(table, guildId), "autocloseTime", Integer.class);
		return data==null ? 0 : data;
	}

	public void setAutocloseLeft(String guildId, Boolean close) {
		Integer value = close==true ? 1 : 0;
		execute("INSERT INTO %s(guildId, autocloseLeft) VALUES (%s, %d) ON CONFLICT(guildId) DO UPDATE SET autocloseLeft=%d".formatted(table, guildId, value, value));
	}

	public Boolean getAutocloseLeft(String guildId) {
		Integer data = selectOne("SELECT autocloseLeft FROM %s WHERE (guildId=%s)".formatted(table, guildId), "autocloseLeft", Integer.class);
		return data==null ? false : data==1;
	}

}
