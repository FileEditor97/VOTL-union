package union.utils.database.managers;

import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

public class TicketSettingsManager extends LiteDBBase {

	public TicketSettingsManager(ConnectionUtil cu) {
		super(cu, "ticketSettings");
	}

	public void remove(String guildId) {
		execute("DELETE FROM %s WHERE (guildId=%s)".formatted(table, guildId));
	}

	public void setRowText(String guildId, int row, String text) {
		execute("INSERT INTO %1$s(guildId, rowName%2$d) VALUES (%3$s, %4$s) ON CONFLICT(guildId) DO UPDATE SET rowName%2$d=%4$s".formatted(table, row, guildId, quote(text)));
	}

	public String getRowText(String guildId, int row) {
		String data = selectOne("SELECT rowName%d FROM %s WHERE (guildId=%s)".formatted(row, table, guildId), "rowName"+row, String.class);
		return data==null ? "Select roles" : data;
	}

	public void setAutocloseTime(String guildId, int hours) {
		execute("INSERT INTO %s(guildId, autocloseTime) VALUES (%s, %d) ON CONFLICT(guildId) DO UPDATE SET autocloseTime=%d".formatted(table, guildId, hours, hours));
	}

	public int getAutocloseTime(String guildId) {
		Integer data = selectOne("SELECT autocloseTime FROM %s WHERE (guildId=%s)".formatted(table, guildId), "autocloseTime", Integer.class);
		return data==null ? 0 : data;
	}

	public void setAutocloseLeft(String guildId, boolean close) {
		int value = close==true ? 1 : 0;
		execute("INSERT INTO %s(guildId, autocloseLeft) VALUES (%s, %d) ON CONFLICT(guildId) DO UPDATE SET autocloseLeft=%d".formatted(table, guildId, value, value));
	}

	public boolean getAutocloseLeft(String guildId) {
		Integer data = selectOne("SELECT autocloseLeft FROM %s WHERE (guildId=%s)".formatted(table, guildId), "autocloseLeft", Integer.class);
		return data==null ? false : data==1;
	}

}
