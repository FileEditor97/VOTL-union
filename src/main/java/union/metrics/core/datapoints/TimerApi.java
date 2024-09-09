package union.metrics.core.datapoints;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

public interface TimerApi {
	Timer startTimer();

	default void time(Runnable func) {
		try (Timer timer = startTimer()) {
			func.run();
		}
	}

	default <T> T time(Supplier<T> func) {
		try (Timer timer = startTimer()) {
			return func.get();
		}
	}

	default <T> T timeChecked(Callable<T> func) throws Exception {
		try (Timer timer = startTimer()) {
			return func.call();
		}
	}
}
