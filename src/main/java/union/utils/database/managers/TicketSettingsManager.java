package union.utils.database.managers;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import union.objects.constants.Constants;
import union.utils.FixedCache;
import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

import net.dv8tion.jda.api.entities.Guild;

public class TicketSettingsManager extends LiteDBBase {

	private final Set<String> columns = Set.of("autocloseTime", "autocloseLeft", "rowName1", "rowName2", "rowName3");

	// Cache
	private final FixedCache<Long, TicketSettings> cache = new FixedCache<>(Constants.DEFAULT_CACHE_SIZE);
	private final TicketSettings defaultSettings = new TicketSettings();

	public TicketSettingsManager(ConnectionUtil cu) {
		super(cu, "ticketSettings");
	}

	public TicketSettings getSettings(Guild guild) {
		return getSettings(guild.getIdLong());
	}

	public TicketSettings getSettings(long guildId) {
		if (cache.contains(guildId))
			return cache.get(guildId);
		TicketSettings settings = applyNonNull(getData(guildId), data -> new TicketSettings(data));
		if (settings == null)
			return defaultSettings;
		cache.put(guildId, settings);
		return settings;
	}

	private Map<String, Object> getData(long guildId) {
		return selectOne("SELECT * FROM %s WHERE (guildId=%d)".formatted(table, guildId), columns);
	}

	public void remove(long guildId) {
		invalidateCache(guildId);
		execute("DELETE FROM %s WHERE (guildId=%d)".formatted(table, guildId));
	}

	public void setRowText(long guildId, int row, String text) {
		if (row < 1 || row > 3)
			throw new IndexOutOfBoundsException(row);
		invalidateCache(guildId);
		execute("INSERT INTO %1$s(guildId, rowName%2$d) VALUES (%3$d, %4$s) ON CONFLICT(guildId) DO UPDATE SET rowName%2$d=%4$s".formatted(table, row, guildId, quote(text)));
	}

	public void setAutocloseTime(long guildId, int hours) {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, autocloseTime) VALUES (%d, %d) ON CONFLICT(guildId) DO UPDATE SET autocloseTime=%<d".formatted(table, guildId, hours));
	}

	public void setAutocloseLeft(long guildId, boolean close) {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, autocloseLeft) VALUES (%d, %d) ON CONFLICT(guildId) DO UPDATE SET autocloseLeft=%<d".formatted(table, guildId, close==true ? 1 : 0));
	}

	private void invalidateCache(long guildId) {
		cache.pull(guildId);
	}

	public class TicketSettings {
		private final int autocloseTime;
		private final boolean autocloseLeft;
		private final List<String> rowText;

		public TicketSettings() {
			this.autocloseTime = 0;
			this.autocloseLeft = false;
			this.rowText = Collections.nCopies(3, "Select roles");
		}

		public TicketSettings(Map<String, Object> data) {
			this.autocloseTime = (Integer) data.getOrDefault("autocloseTime", 0);
			this.autocloseLeft = ((Integer) data.getOrDefault("autocloseTime", 0)) == 1;
			this.rowText = List.of(
				(String) data.getOrDefault("rowName1", "Select roles"),
				(String) data.getOrDefault("rowName2", "Select roles"),
				(String) data.getOrDefault("rowName3", "Select roles")
			);
		}

		public int getAutocloseTime() {
			return autocloseTime;
		}

		public boolean isAutocloseLeft() {
			return autocloseLeft;
		}

		public List<String> getRowText() {
			return rowText;
		}

		public String getRowText(int n) {
			if (n < 1 || n > 3)
				throw new IndexOutOfBoundsException(n);
			return rowText.get(n);
		}
	}

}
