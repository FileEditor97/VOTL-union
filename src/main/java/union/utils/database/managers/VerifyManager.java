package union.utils.database.managers;

import union.utils.database.DBUtil;
import union.utils.database.LiteDBBase;

public class VerifyManager extends LiteDBBase {

	private final String TABLE = "verify";

	public VerifyManager(DBUtil util) {
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

	public void setVerifyRole(String guildId, String roleId) {
		update(TABLE, "roleId", roleId, "guildId", guildId);
	}

	public String getVerifyRole(String guildId) {
		Object data = selectOne(TABLE, "roleId", "guildId", guildId);
		if (data == null) return null;
		return String.valueOf(data);
	}

	public void setMainText(String guildId, String text) {
		update(TABLE, "mainText", removeNewline(text), "guildId", guildId);
	}

	public String getMainText(String guildId) {
		Object data = selectOne(TABLE, "mainText", "guildId", guildId);
		if (data == null) return "No text";
		return setNewline((String) data);
	}

	private String removeNewline(String text) {
		return text.replace("\\n", "<br>");
	}

	private String setNewline(String text) {
		return text.replaceAll("<br>", "\n");
	}

	// manage check for verification role
	public void enableCheck(String guildId) {
		update(TABLE, "enabledCheck", 1, "guildId", guildId);
	}

	public void disableCheck(String guildId) {
		update(TABLE, "enabledCheck", 0, "guildId", guildId);
	}

	public boolean isCheckEnabled(String guildId) {
		Object data = selectOne(TABLE, "enabledCheck", "guildId", guildId);
		if (data == null) return false;
		return (Integer) data == 1;
	}

}
