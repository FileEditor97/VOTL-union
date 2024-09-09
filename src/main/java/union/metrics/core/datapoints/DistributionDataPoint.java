package union.metrics.core.datapoints;

public interface DistributionDataPoint extends DataPoint, TimerApi {
	void observe(long value);

	default Timer startTimer() {
		return new Timer(this::observe);
	}
}
