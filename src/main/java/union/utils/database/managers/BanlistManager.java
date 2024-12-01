package union.utils.database.managers;

import org.jetbrains.annotations.Nullable;
import union.objects.annotation.NotNull;
import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

import java.util.*;

public class BanlistManager extends LiteDBBase {

	public BanlistManager(ConnectionUtil cu) {
		super(cu, null);
	}

	// Plain SteamID, unique
	public boolean add(String table, long steam64) {
		return executeWithRow("INSERT OR IGNORE INTO %s(steam64) VALUES (%d);"
			.formatted(table, steam64)) > 0;
	}

	// With reason, not unique
	public boolean add(String table, long steam64, String reason) {
		return executeWithRow("INSERT OR IGNORE INTO %s(steam64, reason) VALUES (%d, %s);"
			.formatted(table, steam64, quote(reason))) > 0;
	}

	// Octo, not unique
	public boolean add(String table, long steam64, String reason, String details, String command) {
		return executeWithRow("INSERT OR IGNORE INTO %s(steam64, reason, details, command) VALUES (%d, %s, %s, %s);"
			.formatted(table, steam64, quote(reason), quote(details), quote(command))) > 0;
	}

	public void purgeTable(String table) {
		execute("DELETE FROM %s;".formatted(table));
	}

	public BanlistData search(long steam64) {
		BanlistData data = new BanlistData();

		if (contains("alium", steam64)) data.setAlium();
		if (contains("mz", steam64)) data.setMz();

		List<Map<String, String>> map = contains("octo", steam64, true);
		if (map != null) data.setOcto(map);

		map = contains("custom", steam64, false);
		if (map != null) data.setCustom(map);

		return data;
	}

	private List<Map<String, String>> contains(String table, long steam64, boolean octo) {
		List<Map<String, Object>> data = select("SELECT * FROM %s WHERE (steam64=%d)".formatted(table, steam64),
			octo?Set.of("reason","details","command"):Set.of("reason")
		);
		if (data==null || data.isEmpty()) return null;
		if (octo) return data.stream()
			.map(m -> {
				return Map.of(
					"reason", String.valueOf(m.get("reason")),
					"details", String.valueOf(m.get("details")),
					"command", String.valueOf(m.get("command"))
				);
			})
			.toList();
		return data.stream()
			.map(m -> Map.of("reason", String.valueOf(m.get("reason"))))
			.toList();
	}

	private boolean contains(String table, long steam64) {
		return exists("SELECT * FROM %s WHERE (steam64=%d)".formatted(table, steam64));
	}

	public static class BanlistData {
		private boolean alium, mz = false;
		private int octo, custom = 0;
		private Map<String, List<Map<String, String>>> info;

		protected BanlistData() {}

		protected void setAlium() {
			alium = true;
		}

		protected void setMz() {
			mz = true;
		}

		protected void setOcto(@NotNull List<Map<String, String>> map) {
			octo = map.size();
			if (info == null) info = new HashMap<>();
			info.put("octo", map);
		}

		protected void setCustom(@NotNull List<Map<String, String>> map) {
			custom = map.size();
			if (info == null) info = new HashMap<>();
			info.put("custom", map);
		}

		public boolean isAlium() {
			return alium;
		}

		public boolean isMz() {
			return mz;
		}

		public boolean hasOcto() {
			return octo > 0;
		}

		public boolean hasCustom() {
			return custom > 0;
		}

		@Nullable
		public List<Map<String, String>> getOcto() {
			return hasOcto()?info.get("octo"):null;
		}

		@Nullable
		public List<String> getCustom() {
			return hasCustom() ?
				info.get("custom").stream().map(m->m.get("reason")).toList() :
				null;
		}

		public boolean isEmpty() {
			return !(alium||mz||hasOcto()||hasCustom());
		}

	}

}
