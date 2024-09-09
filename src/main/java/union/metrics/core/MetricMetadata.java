package union.metrics.core;

public class MetricMetadata {

	private final Metadata metadata;

	protected MetricMetadata(Builder<?, ?> builder) {
		this.metadata = new Metadata(builder.name, builder.help, builder.unit);
	}

	protected Metadata getMetadata() {
		return metadata;
	}

	protected static abstract class Builder<B extends MetricMetadata.Builder<B, M>, M extends MetricMetadata> {
		protected String name;
		protected String help;
		protected Unit unit;

		public B name(String name) {
			this.name = name;
			return self();
		}

		public B help(String help) {
			this.help = help;
			return self();
		}

		public B unit(Unit unit) {
			this.unit = unit;
			return self();
		}

		public abstract M build();

		protected abstract B self();
	}
}
