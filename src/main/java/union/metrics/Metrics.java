package union.metrics;

import ch.qos.logback.classic.Logger;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import io.prometheus.metrics.core.metrics.Histogram;
import io.prometheus.metrics.model.snapshots.Unit;
import org.slf4j.LoggerFactory;
import union.App;

import java.lang.reflect.Modifier;

public class Metrics {

	// GLOBAL

	public static final Counter jdaEvents = Counter.builder()
		.name("votl_jda_events_received_total")
		.help("All events that JDA provided.")
		.build();

	// COMMANDS

	public static final Counter commandsTerminated = Counter.builder()
		.name("votl_commands_terminated_total")
		.help("Total terminated commands.")
		.build();

	public static final Counter commandsReceived = Counter.builder()
		.name("votl_commands_received_total")
		.help("Total received commands.")
		.build();

	public static final Counter commandsExecuted = Counter.builder()
		.name("votl_commands_executed_total")
		.help("Total executed commands by name.")
		.build();

	public static final Histogram executionTime = Histogram.builder() // commands execution time, excluding terminated ones
		.name("votl_command_execution_duration_seconds")
		.help("Command execution time, excluding handling terminated commands.")
		.labelNames("name")
		.unit(Unit.SECONDS)
		.register();

	public static final Counter commandExceptions = Counter.builder()
		.name("votl_commands_exceptions_total")
		.help("Total uncaught exceptions thrown by command invocation.")
		.build();

	// DATABASE

	public static final Counter databaseLiteQueries = Counter.builder()
		.name("votl_database_lite_queries")
		.help("Total prepared statements created for the given type")
		.build();

	public static final Counter databaseSqlQueries = Counter.builder()
		.name("votl_database_sql_queries")
		.help("Total prepared statements created for the given type")
		.build();


	// SETUP

	public static final Logger log = (Logger) LoggerFactory.getLogger(App.class);

	private static boolean isSetup = false;

	public static void setup() {
		if (isSetup) {
			throw new IllegalStateException("The metrics has already been setup!");
		}

		Metrics.initializeEventMetrics();

		isSetup = true;
	}

	private static void initializeEventMetrics() {
		try (ScanResult scanResult = new ClassGraph()
				.enableAllInfo()
				.acceptPackages("net.dv8tion.jda.api.events")
				.scan()) {
			scanResult.getAllClasses().stream()
				.filter(info -> !info.isInterface() && !Modifier.isAbstract(info.getModifiers()))
				.map(ClassInfo::getSimpleName)
				.forEach(jdaEvents::initLabel);
		}
	}

//	public class Counter {
//		private final String value;
//		private int count;
//	}
}