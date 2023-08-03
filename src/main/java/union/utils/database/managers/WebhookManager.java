package union.utils.database.managers;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import union.utils.database.LiteDBBase;
import union.utils.database.DBUtil;

public class WebhookManager extends LiteDBBase {

	private final String TABLE = "webhook";
	
	public WebhookManager(DBUtil util) {
		super(util);
	}

	public void add(String webhookId, String guildId, String token) {
		insert(TABLE, List.of("webhookId", "guildId", "token"), List.of(webhookId, guildId, token));
	}

	public void remove(String webhookId) {
		delete(TABLE, "webhookId", webhookId);
	}

	public void removeAll(String guildId) {
		delete(TABLE, "guildId", guildId);
	}

	public boolean exists(String webhookId) {
		if (selectOne(TABLE, "webhookId", "webhookId", webhookId) == null) return false;
		return true;
	}

	public String getToken(String webhookId) {
		Object data = selectOne(TABLE, "token", "webhookId", webhookId);
		if (data == null) return null;
		return String.valueOf(data);
	}

	public List<String> getIds(String guildId) {
		List<Object> objs = select(TABLE, "webhookId", "guildId", guildId);
		if (objs.isEmpty()) return Collections.emptyList();
		return objs.stream().map(obj -> String.valueOf(obj)).collect(Collectors.toList());
	}

}
