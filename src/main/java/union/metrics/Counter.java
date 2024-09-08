package union.metrics;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class Counter {
	private final MetricMetadata metadata;

	private volatile DataPoint noLabel;
	private final ConcurrentHashMap<String, DataPoint> data = new ConcurrentHashMap<>();

	private Counter(Builder builder) {
		metadata = new MetricMetadata(builder.name, builder.help, builder.unit);
	}

	public static Builder builder() {
		return new Builder();
	}

	public long sum() {
		if (data.isEmpty()) {
			return get();
		}
		return data.reduceValuesToLong(1, DataPoint::get, 0, Long::sum);
	}

	public void inc() {
		noLabel.inc(1L);
	}

	public void inc(int amount) {
		noLabel.inc((long) amount);
	}

	public void inc(long amount) {
		noLabel.inc(amount);
	}

	public long get() {
		return noLabel.get();
	}

	protected DataPoint getNoLabels() {
		if (noLabel == null) {
			noLabel = new DataPoint();
		}
		return noLabel;
	}

	public void initLabel(String label) {
		addLabel(label);
	}

	public DataPoint addLabel(String label) {
		return data.computeIfAbsent(label, k -> new DataPoint());
	}

	public DataPoint getLabel(String label) {
		if (data.containsKey(label)) {
			return data.get(label);
		}
		return addLabel(label);
	}

	public Map<String, Long> collect() {
		return data.entrySet().stream().collect(Collectors.toUnmodifiableMap(Entry::getKey, e -> e.getValue().get()));
	}

	public void removeLabel(String label) {
		data.remove(label);
	}

	public void clearLabels() {
		data.clear();
	}

	public MetricMetadata getMetadata() {
		return metadata;
	}

	public static class Builder {
		protected String name;
		protected String help;
		protected Unit unit;

		public Builder name(String name) {
			this.name = name;
			return self();
		}

		public Builder help(String help) {
			this.help = help;
			return self();
		}

		public Builder unit(Unit unit) {
			this.unit = unit;
			return self();
		}

		public Counter build() {
			return new Counter(this);
		}

		protected Builder self() {
			return this;
		}
	}

}
