package union.metrics;

import io.prometheus.metrics.model.snapshots.PrometheusNaming;

public final class MetricMetadata {
	private final String name;
	private final String help;
	private final Unit unit;

	public MetricMetadata(String name) {
		this(name, null, null);
	}

	public MetricMetadata(String name, String help) {
		this(name, help, null);
	}

	public MetricMetadata(String name, String help, Unit unit) {
		this.name = name;
		this.help = help;
		this.unit = unit;
	}

	public String getName() {
		return name;
	}

	public String getHelp() {
		return help;
	}

	public Unit getUnit() {
		return unit;
	}
}
