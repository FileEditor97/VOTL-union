package union.metrics;

import java.util.concurrent.atomic.LongAdder;

public class DataPoint {
	private final LongAdder longValue = new LongAdder();

	public void inc() {
		inc(1L);
	}

	public void inc(int amount) {
		inc((long) amount);
	}

	public void inc(long amount) {
		if (amount < 0) {
			throw new IllegalArgumentException("Negative increment " + amount + " is illegal for Counter metrics.");
		}
		longValue.add(amount);
	}

	public void reset() {
		longValue.reset();
	}

	public long get() {
		return longValue.sum();
	}
}
