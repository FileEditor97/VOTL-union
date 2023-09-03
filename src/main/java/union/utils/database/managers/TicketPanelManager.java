package union.utils.database.managers;

import union.utils.database.DBUtil;
import union.utils.database.LiteDBBase;

public class TicketPanelManager extends LiteDBBase {
	
	private final String TABLE = "ticketPanel";

	public TicketPanelManager(DBUtil util) {
		super(util);
	}

	public void add(String guildId) {
		insert(TABLE, "guildId", guildId);
	}

	public void remove(String guildId) {
		delete(TABLE, "guildId", guildId);
	}

	public boolean exists(String guildId) {
		if (selectOne(TABLE, "guildId", "guildId", guildId) == null) return false;
		return true;
	}

	public String getRowText(String guildId, Integer row) {
		Object data = selectOne(TABLE, "rowName"+row, "guildId", guildId);
		if (data == null) return "";
		return data.toString();
	}

	public void setRowText(String guildId, Integer row, String text) {
		update(TABLE, "rowName"+row, text, "guildId", guildId);
	}

}
