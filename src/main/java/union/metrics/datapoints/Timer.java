package union.metrics.datapoints;

import java.io.Closeable;
import java.util.function.DoubleConsumer;

public class Timer implements Closeable {

	private final DoubleConsumer observeFunction;
	private final long startTimeNanos = System.nanoTime();

	Timer(DoubleConsumer observeFunction) {
		this.observeFunction = observeFunction;
	}

	public double observeDuration() {
		double elapsed = ((double) (System.nanoTime() - startTimeNanos)) / 100_000_000.0;
		observeFunction.accept(elapsed);
		return elapsed;
	}

	@Override
	public void close() {
		observeDuration();
	}
}
