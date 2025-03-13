package union.utils.database.managers;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import union.objects.PunishActions;
import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

import net.dv8tion.jda.internal.utils.tuple.Pair;

public class AutopunishManager extends LiteDBBase {

	public AutopunishManager(ConnectionUtil cu) {
		super(cu, "autopunish");
	}

	public void addAction(long guildId, int atStrikeCount, List<PunishActions> actions, @Nullable String data) throws SQLException {
		execute("INSERT INTO %s(guildId, strike, actions, data) VALUES (%d, %d, %d, %s)".formatted(table, guildId, atStrikeCount, PunishActions.encodeActions(actions), quote(data)));
	}

	public void removeAction(long guildId, int atStrikeCount) throws SQLException {
		execute("DELETE FROM %s WHERE (guildId=%d AND strike=%d)".formatted(table, guildId, atStrikeCount));
	}

	public void removeGuild(long guildId) throws SQLException {
		execute("DELETE FROM %s WHERE (guildId=%d)".formatted(table, guildId));
	}

	public Pair<Integer, String> getAction(long guildId, int atStrikeCount) {
		Map<String, Object> data = selectOne("SELECT actions, data FROM %s WHERE (guildId=%d AND strike=%d) ORDER BY strike DESC".formatted(table, guildId, atStrikeCount), Set.of("actions", "data"));
		if (data == null) return null;
		return Pair.of((Integer) data.get("actions"), (String) data.getOrDefault("data", ""));
	}

	public Pair<Integer, String> getTopAction(long guildId, int minStrikeCount) {
		Map<String, Object> data = selectOne("SELECT actions, data FROM %s WHERE (guildId=%d AND strike<=%d) ORDER BY strike DESC".formatted(table, guildId, minStrikeCount), Set.of("actions", "data"));
		if (data == null) return null;
		return Pair.of((Integer) data.get("actions"), (String) data.getOrDefault("data", ""));
	}

	public List<Autopunish> getAllActions(long guildId) {
		return select("SELECT * FROM %s WHERE (guildId=%d)".formatted(table, guildId), Set.of("strike", "actions", "data"))
			.stream()
			.map(Autopunish::new)
			.toList();
	}

	public static class Autopunish {
		private final int strike;
		private final List<PunishActions> actions;
		private final String data;

		public Autopunish(Map<String, Object> data) {
			this.strike = (Integer) data.get("strike");
			this.actions = PunishActions.decodeActions((Integer) data.get("actions"));
			this.data = (String) data.get("data");
		}

		public int getCount() {
			return strike;
		}

		public List<PunishActions> getActions() {
			return actions;
		}

		public String getData() {
			return data;
		}
	}
	
}
