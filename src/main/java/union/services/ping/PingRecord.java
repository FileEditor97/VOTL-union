package union.services.ping;

import java.time.Instant;

public record PingRecord(Instant timestamp, long webSocketPing, long restPing) {}