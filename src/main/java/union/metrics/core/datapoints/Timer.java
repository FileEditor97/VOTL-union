package union.metrics.core.datapoints;

import java.io.Closeable;
import java.util.function.LongConsumer;

public class Timer implements Closeable {

	private final LongConsumer observeFunction;
	private final long startTimeNanos = System.nanoTime();

	Timer(LongConsumer observeFunction) {
		this.observeFunction = observeFunction;
	}

	public long observeDuration() {
		long elapsedNanos = System.nanoTime() - startTimeNanos;
		observeFunction.accept(elapsedNanos);
		return elapsedNanos;
	}

	@Override
	public void close() {
		observeDuration();
	}
}
