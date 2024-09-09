package union.listeners;

import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import union.metrics.Metrics;

public class EventListener extends ListenerAdapter {

	@Override
	public void onGenericEvent(GenericEvent event) {
		Metrics.jdaEvents.labelValue(event.getClass().getSimpleName()).inc();
	}
}
