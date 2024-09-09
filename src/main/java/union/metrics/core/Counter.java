package union.metrics.core;

import union.metrics.datapoints.CounterDataPoint;

import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

public class Counter extends StatefulMetric<CounterDataPoint, Counter.DataPoint> implements CounterDataPoint {

	private Counter(Builder builder) {
		super(builder);
	}

	@Override
	public void inc(int amount) {
		inc((long) amount);
	}

	@Override
	public void inc(long amount) {
		getNoLabels().inc(amount);
	}

	@Override
	public long get() {
		return getNoLabels().get();
	}

	@Override
	public void reset() {
		getNoLabels().reset();
	}

	@Override
	protected DataPoint newDataPoint() {
		return new DataPoint();
	}

	public long sum() {
		if (getLabels().isEmpty()) {
			return get();
		}
		return getLabels().reduceValuesToLong(1, CounterDataPoint::get, 0, Long::sum);
	}

	public Map<String, Long> collect() {
		return getLabels().entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> e.getValue().get()));
	}

	public static Builder builder() {
		return new Builder();
	}

	static class DataPoint implements CounterDataPoint {
		private final LongAdder longValue = new LongAdder();

		private DataPoint() {}

		@Override
		public void inc(long amount) {
			if (amount < 0) {
				throw new IllegalArgumentException("Negative increment " + amount + " is illegal for Counter metrics.");
			}
			longValue.add(amount);
		}

		public long get() {
			return longValue.longValue();
		}

		public void reset() {
			longValue.reset();
		}
	}

	public static class Builder extends StatefulMetric.Builder<Builder, Counter> {
		@Override
		public Counter build() {
			return new Counter(this);
		}

		@Override
		protected Builder self() {
			return this;
		}
	}

}
