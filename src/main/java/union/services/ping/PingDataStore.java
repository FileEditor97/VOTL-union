package union.services.ping;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

public class PingDataStore {
	private static final int MAX_RECORDS = (24 * 60) / 5; // 24 hours with 5-minute intervals
	private final Deque<PingRecord> records = new ArrayDeque<>(MAX_RECORDS);

	public synchronized void addRecord(long webSocketPing, long restPing) {
		if (records.size() >= MAX_RECORDS) {
			records.pollFirst(); // Remove the oldest record
		}
		records.addLast(new PingRecord(Instant.now(), webSocketPing, restPing));
	}

	public synchronized Deque<PingRecord> getRecords() {
		return new ArrayDeque<>(records); // Return a copy to avoid external modification
	}
}
