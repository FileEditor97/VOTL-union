package union.services;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.annotation.Nonnull;

public class CountingThreadFactory implements ThreadFactory {
	
	private final String identifier;
	private final AtomicLong count = new AtomicLong(1);
	private final boolean daemon;

	public CountingThreadFactory(@Nonnull String identifier, @Nonnull String specifier) {
		this(identifier, specifier, true);
	}

	public CountingThreadFactory(@Nonnull String identifier, @Nonnull String specifier, boolean daemon) {
		this.identifier = identifier + " " + specifier;
		this.daemon = daemon;
	}

	@Nonnull
	@Override
	public Thread newThread(@Nonnull Runnable r) {
		final Thread thread = new Thread(r, identifier + " " + count.getAndIncrement());
		thread.setDaemon(daemon);
		return thread;
	}

}
