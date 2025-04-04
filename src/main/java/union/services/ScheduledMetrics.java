package union.services;

import ch.qos.logback.classic.Logger;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import org.slf4j.LoggerFactory;
import union.App;
import union.metrics.Metrics;
import union.services.records.DatabaseData;
import union.services.records.PingData;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ScheduledMetrics {
	private final Logger LOG = (Logger) LoggerFactory.getLogger(ScheduledMetrics.class);

	private final App bot;

	public static final PingData pingDataStore = new PingData();

	public static final DatabaseData databaseData = new DatabaseData();

	public ScheduledMetrics(App bot) {
		this.bot = bot;
	}

	public void recordMetrics() {
		try {
			final long wsPing = bot.JDA.getGatewayPing();
			bot.JDA.getRestPing().timeout(10, TimeUnit.SECONDS).queue(restPing -> {
				// Log
				LOG.debug("WebSocket Ping: {} ms; Rest Ping: {} ms", wsPing, restPing);
				// Save data
				pingDataStore.addRecord(wsPing, restPing);
			}, failure ->
				new ErrorHandler().handle(TimeoutException.class, e-> {
					LOG.warn("WebSocket Ping: {} ms; Rest Ping: >10000 ms!", wsPing);
					// Save data
					pingDataStore.addRecord(wsPing, 10_000);
				})
			);

			final int newRequests = Long.valueOf(Metrics.databaseSqlQueries.get()).intValue();
			Metrics.databaseSqlQueries.reset();

			final int errors = Long.valueOf(Metrics.databaseSqlErrors.get()).intValue();
			Metrics.databaseSqlErrors.reset();

			databaseData.addRecord(newRequests, errors);
		} catch (Throwable t) {
			LOG.error("Error recording metrics", t);
		}
	}

}
