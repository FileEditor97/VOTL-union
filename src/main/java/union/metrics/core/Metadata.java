package union.metrics.core;

public final class Metadata {
	private final String name;
	private final String help;
	private final Unit unit;

	public Metadata(String name) {
		this(name, null, null);
	}

	public Metadata(String name, String help) {
		this(name, help, null);
	}

	public Metadata(String name, String help, Unit unit) {
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
