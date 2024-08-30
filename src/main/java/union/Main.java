package union;

import ch.qos.logback.classic.ClassicConstants;

import java.util.stream.Stream;

public class Main {
	public static void main(String[] args) {
		if (Stream.of(args).anyMatch("debug"::matches)) {
			System.setProperty(ClassicConstants.CONFIG_FILE_PROPERTY, "logback_debug.xml");
		}

		App.instance = new App();
	}
}
