package union.utils.database.managers;

import java.sql.SQLException;
import java.util.List;

import union.utils.database.LiteDBBase;
import union.utils.database.ConnectionUtil;

public class WebhookManager extends LiteDBBase {
	public WebhookManager(ConnectionUtil cu) {
		super(cu, "webhook");
	}

	public void add(long webhookId, long guildId, String token) throws SQLException {
		execute("INSERT INTO %s(webhookId, guildId, token) VALUES (%s, %s, %s) ON CONFLICT(webhookId) DO NOTHING".formatted(table, webhookId, guildId, quote(token)));
	}

	public void remove(long webhookId) throws SQLException {
		execute("DELETE FROM %s WHERE (webhookId=%s)".formatted(table, webhookId));
	}

	public void removeAll(long guildId) throws SQLException {
		execute("DELETE FROM %s WHERE (guildId=%s)".formatted(table, guildId));
	}

	public boolean exists(long webhookId) {
		return selectOne("SELECT webhookId FROM %s WHERE (webhookId=%s)".formatted(table, webhookId), "webhookId", Long.class) != null;
	}

	public String getToken(long webhookId) {
		return selectOne("SELECT token FROM %s WHERE (webhookId=%s)".formatted(table, webhookId), "token", String.class);
	}

	public List<Long> getWebhookIds(long guildId) {
		return select("SELECT webhookId FROM %s WHERE (guildId=%s)".formatted(table, guildId), "webhookId", Long.class);
	}
}
