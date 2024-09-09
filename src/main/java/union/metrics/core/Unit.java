package union.metrics.core;

import java.util.Objects;

public class Unit {
	private final String name;
	public static final Unit RATIO = new Unit("ratio");
	public static final Unit SECONDS = new Unit("seconds");
	public static final Unit BYTES = new Unit("bytes");
	public static final Unit CELSIUS = new Unit("celsius");
	public static final Unit JOULES = new Unit("joules");
	public static final Unit GRAMS = new Unit("grams");
	public static final Unit METERS = new Unit("meters");
	public static final Unit VOLTS = new Unit("volts");
	public static final Unit AMPERES = new Unit("amperes");

	public Unit(String name) {
		if (name == null) {
			throw new NullPointerException("Unit name cannot be null.");
		} else {
			this.name = name.trim();
		}
	}

	public String toString() {
		return this.name;
	}

	public static double nanosToSeconds(long nanos) {
		return (double)nanos / 1.0E9;
	}

	public static double millisToSeconds(long millis) {
		return (double)millis / 1000.0;
	}

	public static double secondsToMillis(double seconds) {
		return seconds * 1000.0;
	}

	public static double kiloBytesToBytes(double kilobytes) {
		return kilobytes * 1024.0;
	}

	public boolean equals(Object o) {
		if (this == o) {
			return true;
		} else if (o != null && this.getClass() == o.getClass()) {
			Unit unit = (Unit)o;
			return Objects.equals(this.name, unit.name);
		} else {
			return false;
		}
	}

	public int hashCode() {
		return Objects.hash(this.name);
	}
}
