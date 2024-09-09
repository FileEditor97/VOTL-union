package union.metrics.core;

import union.metrics.core.datapoints.DistributionDataPoint;

import java.util.concurrent.atomic.LongAdder;

public class Histogram extends StatefulMetric<DistributionDataPoint, Histogram.DataPoint> implements DistributionDataPoint {

	private Histogram(Histogram.Builder builder) {
		super(builder);
	}

	@Override
	public void observe(long amount) {
		getNoLabels().observe(amount);
	}

	public class DataPoint implements DistributionDataPoint {
		private final LongAdder count = new LongAdder();
		private final LongAdder sum = new LongAdder();

		public DataPoint() {}

		@Override
		public void observe(long value) {
			sum.add(value);
			count.increment();
		}
	}

	@Override
	protected DataPoint newDataPoint() {
		return new DataPoint();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder extends StatefulMetric.Builder<Histogram.Builder, Histogram> {
		@Override
		public Histogram build() {
			return new Histogram(this);
		}

		@Override
		protected Histogram.Builder self() {
			return this;
		}
	}
}
