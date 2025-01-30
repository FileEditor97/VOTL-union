package union.utils.database.managers;

import org.jetbrains.annotations.NotNull;
import union.utils.database.ConnectionUtil;
import union.utils.database.LiteDBBase;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CommentsManager extends LiteDBBase {
	public CommentsManager(ConnectionUtil cu) {
		super(cu, "playerComments");
	}

	public boolean add(long steam64, long authorId, Instant timestamp, @NotNull String comment) {
		return execute("INSERT INTO %s(steam64, authorId, timestamp, comment) VALUES (%d, %d, %d, %s) ON CONFLICT(steam64, authorId) DO UPDATE SET comment=%<s"
			.formatted(table, steam64, authorId, timestamp.getEpochSecond(), quote(comment)));
	}

	public boolean removeComment(long steam64, long authorId) {
		return execute("DELETE FROM %s WHERE (steam64=%d AND authorId=%d)".formatted(table, steam64, authorId));
	}

	public boolean removePlayer(long steam64) {
		return execute("DELETE FROM %s WHERE (steam64=%d)".formatted(table, steam64));
	}

	@SuppressWarnings("unused")
	public boolean purge() {
		return execute("DELETE FROM %s".formatted(table));
	}

	public int countComments(long steam64) {
		return count("SELECT COUNT(*) FROM %s WHERE (steam64=%d)".formatted(table, steam64));
	}

	public List<Map<String, Object>> getComments(long steam64) {
		return select("SELECT * FROM %s WHERE (steam64=%d)".formatted(table, steam64), Set.of("authorId", "comment", "timestamp"));
	}
}
