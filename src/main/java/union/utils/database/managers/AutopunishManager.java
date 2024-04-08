package union.utils.database.managers;

import java.util.List;
import java.util.Map;
import java.util.Set;

import union.objects.PunishActions;
import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

import net.dv8tion.jda.internal.utils.tuple.Pair;

public class AutopunishManager extends LiteDBBase {

	public AutopunishManager(ConnectionUtil cu) {
		super(cu, "autopunish");
	}

	public void addAction(long guildId, int atStrikeCount, List<PunishActions> actions, String data) {
		execute("INSERT INTO %s(guildId, strike, actions, data) VALUES (%d, %d, %d, %s)".formatted(table, guildId, atStrikeCount, PunishActions.encodeActions(actions), quote(data)));
	}

	public void removeAction(long guildId, int atStrikeCount) {
		execute("DELETE FROM %s WHERE (guildId=%d AND strike=%d)".formatted(table, guildId, atStrikeCount));
	}

	public void removeGuild(long guildId) {
		execute("DELETE FROM %s WHERE (guildId=%d)".formatted(table, guildId));
	}

	public Pair<Integer, String> getAction(long guildId, int atStrikeCount) {
		Map<String, Object> data = selectOne("SELECT actions, data FROM %s WHERE (guildId=%d AND strike=%d)".formatted(table, guildId, atStrikeCount), Set.of("actions", "data"));
		if (data == null) return null;
		return Pair.of((Integer) data.get("actions"), (String) data.getOrDefault("data", ""));
	}

	public List<Map<String, Object>> getAllActions(long guildId) {
		return select("SELECT * FROM %s WHERE (guildId=%d)".formatted(table, guildId), Set.of("strike", "actions", "data"));
	}
	
}
