package union.metrics.core;

import union.metrics.datapoints.DataPoint;

import java.util.concurrent.ConcurrentHashMap;

abstract class StatefulMetric<D extends DataPoint, T extends D> extends MetricMetadata {

	private final ConcurrentHashMap<String, T> data = new ConcurrentHashMap<>();

	private volatile T noLabel;

	protected StatefulMetric(Builder<?, ?> builder) {
		super(builder);
	}

	public void initLabel(String label) {
		labelValue(label);
	}

	public D labelValue(String label) {
		return data.computeIfAbsent(label, k -> newDataPoint());
	}

	public ConcurrentHashMap<String, T> getLabels() {
		return data;
	}

	public void removeLabel(String label) {
		data.remove(label);
	}

	public void clearValues() {
		data.clear();
		noLabel = null;
	}

	protected T getNoLabels() {
		if (noLabel == null) {
			noLabel = newDataPoint();
		}
		return noLabel;
	}

	protected abstract T newDataPoint();

	static abstract class Builder<B extends Builder<B, M>, M extends StatefulMetric<?, ?>> extends MetricMetadata.Builder<B, M> {}
}
