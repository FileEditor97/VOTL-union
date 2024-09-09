package union.metrics.core;

import union.metrics.datapoints.DistributionDataPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.DoubleAdder;

public class Histogram extends StatefulMetric<DistributionDataPoint, Histogram.DataPoint> implements DistributionDataPoint {

	private Histogram(Histogram.Builder builder) {
		super(builder);
	}

	public int getSize() {
		return getNoLabels().getSize();
	}

	@Override
	public double getMax() {
		return getNoLabels().getMax();
	}

	@Override
	public double getMin() {
		return getNoLabels().getMin();
	}

	@Override
	public double getAverage() {
		return getNoLabels().getAverage();
	}

	@Override
	public double getPercentile(double percentile) {
		return getNoLabels().getPercentile(percentile);
	}

	@Override
	public void observe(double amount) {
		getNoLabels().observe(amount);
	}

	public static class DataPoint implements DistributionDataPoint {
		private final List<Double> values = Collections.synchronizedList(new FixedSizeList<>(1_000));

		private final DoubleAdder sum = new DoubleAdder();

		public DataPoint() {}

		@Override
		public void observe(double value) {
			values.add(value);
			sum.add(value);
		}

		public int getSize() {
			return values.size();
		}

		@Override
		public double getMax() {
			synchronized (values) {
				if (values.isEmpty())
					return 0;
				return values.stream()
					.mapToDouble(Double::doubleValue)
					.max()
					.getAsDouble();
			}
		}

		@Override
		public double getMin() {
			synchronized (values) {
				if (values.isEmpty())
					return 0;
				return values.stream()
					.mapToDouble(Double::doubleValue)
					.min()
					.getAsDouble();
			}
		}

		@Override
		public double getAverage() {
			synchronized (values) {
				if (values.isEmpty())
					return 0.0;
				return sum.doubleValue() / values.size();
			}
		}

		@Override
		public double getPercentile(double percentile) {
			if (percentile < 0 || percentile > 100) {
				throw new IllegalArgumentException("Percentile must be between 0 and 100");
			}
			synchronized (values) {
				if (values.isEmpty())
					return 0.0;
				List<Double> sortedValues = new ArrayList<>(values);
				Collections.sort(sortedValues);
				int index = (int) Math.ceil(percentile / 100.0 * sortedValues.size()) - 1;
				return sortedValues.get(Math.max(0, index));
			}
		}

		@Override
		public void reset() {
			values.clear();
			sum.reset();
		}
	}

	@Override
	public void reset() {
		getNoLabels().reset();
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
