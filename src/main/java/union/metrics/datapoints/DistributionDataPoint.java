package union.metrics.datapoints;

public interface DistributionDataPoint extends DataPoint, TimerApi {
	void observe(double value);

	double getMax();

	double getMin();

	double getAverage();

	double getPercentile(double percentile);

	void reset();

	default Timer startTimer() {
		return new Timer(this::observe);
	}
}
