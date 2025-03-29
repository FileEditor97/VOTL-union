package union.services.records;

import java.time.Instant;

public class DatabaseData extends DataStore<DatabaseData.DatabaseRecord> {
	public synchronized void addRecord(int requests, int errors) {
		removeFirst();
		records.addLast(new DatabaseRecord(Instant.now(), requests, errors));
	}

	public class DatabaseRecord extends DataRecord {
		public int requests;
		public int errors;

		DatabaseRecord(Instant timestamp, int requests, int errors) {
			this.timestamp = timestamp;
			this.requests = requests;
			this.errors = errors;
		}
	}
}
