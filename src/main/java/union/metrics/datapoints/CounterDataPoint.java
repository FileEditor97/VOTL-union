package union.metrics.datapoints;

public interface CounterDataPoint extends DataPoint {
	default void inc() {
		inc(1L);
	}

	default void inc(int amount) {
		inc((long) amount);
	}

	void inc(long amount);

	long get();

	void reset();
}
