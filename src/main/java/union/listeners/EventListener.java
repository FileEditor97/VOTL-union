package union.listeners;

import ch.qos.logback.classic.Logger;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;
import union.metrics.Metrics;
import union.utils.database.DBUtil;

public class EventListener extends ListenerAdapter {

	private final Logger LOG = (Logger) LoggerFactory.getLogger(EventListener.class);

	private final DBUtil db;

	public EventListener(DBUtil db) {
		this.db = db;
	}

	@Override
	public void onGenericEvent(@NotNull GenericEvent event) {
		Metrics.jdaEvents.labelValue(event.getClass().getSimpleName()).inc();
	}

	@Override
	public void onReady(@NotNull ReadyEvent event) {
		// Check voice channels
		try {
			db.voice.checkCache(event.getJDA());
			LOG.info("Voice cache checked!");
		} catch (Throwable ex) {
			LOG.error("Error checking custom voice channels cache", ex);
		}
	}

}
