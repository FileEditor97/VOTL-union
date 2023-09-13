package union.utils.database.managers;

import union.utils.database.DBUtil;
import union.utils.database.LiteDBBase;

public class TicketGlobalManager extends LiteDBBase {
	
	private final String TABLE = "ticketSettings";

	public TicketGlobalManager(DBUtil util) {
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

	public void setRowText(String guildId, Integer row, String text) {
		update(TABLE, "rowName"+row, text, "guildId", guildId);
	}

	public String getRowText(String guildId, Integer row) {
		Object data = selectOne(TABLE, "rowName"+row, "guildId", guildId);
		if (data == null) return "";
		return data.toString();
	}

	public void setAutocloseTime(String guildId, Integer hours) {
		update(TABLE, "autocloseTime", hours, "guildId", guildId);
	}

	public Integer getAutocloseTime(String guildId) {
		Object data = selectOne(TABLE, "autocloseTime", "guildId", guildId);
		if (data == null) return 12;
		return (Integer) data;
	}

	public void setAutocloseLeft(String guildId, Boolean close) {
		update(TABLE, "autocloseLeft", close, "guildId", guildId);
	}

	public Boolean getAutocloseLeft(String guildId) {
		Object data = selectOne(TABLE, "autocloseLeft", "guildId", guildId);
		if (data == null) return false;
		return data.equals(1);
	}

}
