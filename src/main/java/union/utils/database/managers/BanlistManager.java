package union.utils.database.managers;

import union.objects.annotation.NotNull;
import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

import java.util.*;

public class BanlistManager extends LiteDBBase {

	public BanlistManager(ConnectionUtil cu) {
		super(cu, null);
	}

	public boolean add(String table, long steam64) {
		return executeWithRow("INSERT OR IGNORE INTO %s(steam64) VALUES (%d);"
			.formatted(table, steam64)) > 0;
	}

	public boolean add(String table, long steam64, String reason) {
		return executeWithRow("INSERT OR IGNORE INTO %s(steam64, reason) VALUES (%d, %s);"
			.formatted(table, steam64, quote(reason))) > 0;
	}

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

		Map<String, String> map = contains("octo", steam64, true);
		if (map != null) data.setOcto(map);

		map = contains("custom", steam64, false);
		if (map != null) data.setCustom(map.get("reason"));

		return data;
	}

	private Map<String, String> contains(String table, long steam64, boolean octo) {
		Map<String, Object> data = selectOne("SELECT * FROM %s WHERE (steam64=%d)".formatted(table, steam64),
			octo?Set.of("reason","details","command"):Set.of("reason")
		);
		if (data==null || data.isEmpty()) return null;
		if (octo) return Map.of(
			"reason", String.valueOf(data.get("reason")),
			"details", String.valueOf(data.get("details")),
			"command", String.valueOf(data.get("command"))
		);
		return Map.of("reason", String.valueOf(data.get("reason")));
	}

	private boolean contains(String table, long steam64) {
		return exists("SELECT * FROM %s WHERE (steam64=%d)".formatted(table, steam64));
	}

	public static class BanlistData {
		private boolean alium, mz, octo, custom = false;
		private Map<String, Map<String, String>> info;

		protected BanlistData() {}

		protected void setAlium() {
			alium = true;
		}

		protected void setMz() {
			mz = true;
		}

		protected void setOcto(@NotNull Map<String, String> map) {
			octo = true;
			if (info == null) info = new HashMap<>();
			info.put("octo", map);
		}

		protected void setCustom(@NotNull String reason) {
			custom = true;
			if (info == null) info = new HashMap<>();
			info.put("custom", Collections.singletonMap("reason", reason));
		}

		public boolean isAlium() {
			return alium;
		}

		public boolean isMz() {
			return mz;
		}

		public Map<String, String> getOcto() {
			return octo?info.get("octo"):null;
		}

		public String getCustom() {
			return custom?info.get("custom").get("reason"):null;
		}

		public boolean isEmpty() {
			return !(alium||mz||octo||custom);
		}

	}

}
