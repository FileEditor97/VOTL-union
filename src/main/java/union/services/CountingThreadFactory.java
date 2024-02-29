package union.services;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

import union.objects.annotation.NotNull;

public class CountingThreadFactory implements ThreadFactory {
	
	private final String identifier;
	private final AtomicLong count = new AtomicLong(1);
	private final boolean daemon;

	public CountingThreadFactory(@NotNull String identifier, @NotNull String specifier) {
		this(identifier, specifier, true);
	}

	public CountingThreadFactory(@NotNull String identifier, @NotNull String specifier, boolean daemon) {
		this.identifier = identifier + " " + specifier;
		this.daemon = daemon;
	}

	@NotNull
	@Override
	public Thread newThread(@NotNull Runnable r) {
		final Thread thread = new Thread(r, identifier + " " + count.getAndIncrement());
		thread.setDaemon(daemon);
		return thread;
	}

}
