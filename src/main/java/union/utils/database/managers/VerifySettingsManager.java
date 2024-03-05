package union.utils.database.managers;

import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

public class VerifySettingsManager extends LiteDBBase {

	public VerifySettingsManager(ConnectionUtil cu) {
		super(cu, "verify");
	}

	public void remove(String guildId) {
		execute("DELETE FROM %s WHERE (guildId=%s)".formatted(table, guildId));
	}

	public void setVerifyRole(String guildId, String roleId) {
		execute("INSERT INTO %s(guildId, roleId) VALUES (%s, %s) ON CONFLICT(guildId) DO UPDATE SET roleId=%s".formatted(table, guildId, roleId, roleId));
	}

	public String getVerifyRole(String guildId) {
		return selectOne("SELECT roleId FROM %s WHERE (guildId=%s)".formatted(table, guildId), "roleId", String.class);
	}

	public void setMainText(String guildId, String text) {
		final String textParsed = quote(text.replace("\\n", "<br>"));
		execute("INSERT INTO %s(guildId, mainText) VALUES (%s, %s) ON CONFLICT(guildId) DO UPDATE SET mainText=%s".formatted(table, guildId, textParsed, textParsed));
	}

	public String getMainText(String guildId) {
		String data = selectOne("SELECT mainText FROM %s WHERE (guildId=%s)".formatted(table, guildId), "mainText", String.class);
		if (data == null) return "No text";
		return data.replaceAll("<br>", "\n");
	}

	// manage check for verification role
	public void enableCheck(String guildId) {
		execute("INSERT INTO %s(guildId, enabledCheck) VALUES (%s, 1) ON CONFLICT(guildId) DO UPDATE SET enabledCheck=1".formatted(table, guildId));
	}

	public void disableCheck(String guildId) {
		execute("INSERT INTO %s(guildId, enabledCheck) VALUES (%s, 0) ON CONFLICT(guildId) DO UPDATE SET enabledCheck=0".formatted(table, guildId));
	}

	public Boolean isCheckEnabled(String guildId) {
		Integer data = selectOne("SELECT enabledCheck FROM %s WHERE (guildId=%s)".formatted(table, guildId), "enabledCheck", Integer.class);
		return data==null ? false : data==1;
	}

}
