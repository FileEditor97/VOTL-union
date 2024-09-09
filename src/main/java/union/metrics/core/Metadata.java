package union.metrics.core;

public final class Metadata {
	private final String name;
	private final String help;

	public Metadata(String name) {
		this(name, null);
	}

	public Metadata(String name, String help) {
		this.name = name;
		this.help = help;
	}

	public String getName() {
		return name;
	}

	public String getHelp() {
		return help;
	}
}
