package union.utils.database.managers;

import net.dv8tion.jda.internal.utils.tuple.Pair;
import union.objects.annotation.NotNull;
import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
			.formatted(table, steam64, reason)) > 0;
	}

	public boolean add(String table, long steam64, String reason, String details) {
		return executeWithRow("INSERT OR IGNORE INTO %s(steam64, reason, details) VALUES (%d, %s, %s);"
			.formatted(table, steam64, quote(reason), quote(details))) > 0;
	}

	public void purgeTable(String table) {
		execute("DELETE * FROM %s;".formatted(table));
	}

	public BanlistData search(long steam64) {
		BanlistData data = new BanlistData();

		if (contains("alium", steam64)) data.setAlium();
		if (contains("mz", steam64)) data.setMz();

		Pair<String, String> pair = contains("octo", steam64, true);
		if (pair != null) data.setOcto(pair);

		pair = contains("custom", steam64, false);
		if (pair != null) data.setCustom(pair);

		return data;
	}

	private Pair<String, String> contains(String table, long steam64, boolean details) {
		Map<String, Object> data = selectOne("SELECT * FROM %s WHERE (steam64=%d)".formatted(table, steam64),
			details?Set.of("reason","details"):Set.of("reason")
		);
		if (data==null || data.isEmpty()) return null;
		return Pair.of((String) data.get("reason"), details?(String) data.get("details"):null);
	}

	private boolean contains(String table, long steam64) {
		return exists("SELECT * FROM %s WHERE (steam64=%d)".formatted(table, steam64));
	}

	public static class BanlistData {
		private boolean alium, mz, octo, custom = false;
		private HashMap<String, Pair<String, String>> reasons;

		protected BanlistData() {}

		protected void setAlium() {
			alium = true;
		}

		protected void setMz() {
			mz = true;
		}

		protected void setOcto(@NotNull Pair<String, String> pair) {
			if (pair.getLeft() == null) throw new NullPointerException("Reason is null");
			octo = true;
			if (reasons == null) reasons = new HashMap<>();
			reasons.put("octo", pair);
		}

		protected void setCustom(@NotNull Pair<String, String> pair) {
			if (pair.getLeft() == null) throw new NullPointerException("Reason is null");
			custom = true;
			if (reasons == null) reasons = new HashMap<>();
			reasons.put("custom", pair);
		}

		public boolean isAlium() {
			return alium;
		}

		public boolean isMz() {
			return mz;
		}

		public Pair<String, String> getOcto() {
			return octo?reasons.get("octo"):null;
		}

		public String getCustom() {
			return custom?reasons.get("custom").getLeft():null;
		}

		public boolean isEmpty() {
			return !(alium||mz||octo||custom);
		}

	}

}
