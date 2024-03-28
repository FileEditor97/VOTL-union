package union.utils.message;

import java.time.Duration;
import java.time.temporal.TemporalAccessor;
import java.util.HashMap;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import union.objects.annotation.Nullable;
import union.utils.exception.FormatterException;

import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.utils.TimeFormat;

public class TimeUtil {

	private static final Pattern timePatternFull = Pattern.compile("^(([0-9]+)([smhdw]{1}))+$", Pattern.CASE_INSENSITIVE);
	private static final Pattern timePattern = Pattern.compile("([0-9]+)([smhdw]{1})", Pattern.CASE_INSENSITIVE);

	private enum TimeFormats{
		SECONDS('s', 1),
		MINUTES('m', 60),
		HOURS  ('h', 3600),
		DAYS   ('d', 86400),
		WEEKS  ('w', 604800);

		private final Character character;
		private final Integer multip;

		private static final HashMap<Character, TimeFormats> BY_CHAR = new HashMap<Character, TimeFormats>();

		static {
			for (TimeFormats format : TimeFormats.values()) {
				BY_CHAR.put(format.getChar(), format);
			}
		}

		TimeFormats(Character character, Integer multip) {
			this.character = character;
			this.multip = multip;
		}

		public Character getChar() {
			return character;
		}

		public Integer getMultip() {
			return multip;
		}

		@Nullable
		public static Integer getMultipByChar(Character c) {
			return Optional.ofNullable(BY_CHAR.get(c)).map(tf -> tf.getMultip()).orElse(null);
		}
	}

	/*
	 * Duration and Period class have parse() method,
	 * but they are quite inconvinient, as we want to
	 * use both duration(h m s) and period(w d).
	 */
	public static Duration stringToDuration(String text, boolean allowSeconds) throws FormatterException {
		if (text == null || text.isEmpty() || text == "0") {
			return Duration.ZERO;
		}

		if (!timePatternFull.matcher(text).matches()) {
			throw new FormatterException("errors.formatter.no_time_provided");
		}
		
		Matcher timeMatcher = timePattern.matcher(text);
		Long time = 0L;
		while (timeMatcher.find()) {
			Character c = timeMatcher.group(2).charAt(0);
			if (c.equals('s') && !allowSeconds) {
				throw new FormatterException("errors.formatter.except_seconds");
			}
			Integer multip = TimeFormats.getMultipByChar(c);
			if (multip == null) {
				throw new FormatterException("errors.formatter.no_multip");
			}

			try {
				time = Math.addExact(time, Math.multiplyExact(Long.valueOf(timeMatcher.group(1)), multip));
			} catch (NumberFormatException ex) {
				throw new FormatterException("errors.formatter.parse_long");
			} catch (ArithmeticException ex) {
				throw new FormatterException("errors.formatter.long_overflow");
			}
		}
		
		return Duration.ofSeconds(time);
	}

	public static String durationToString(Duration duration) {
		if (duration.isZero()) {
			return "0 seconds";
		}

		StringBuffer buffer = new StringBuffer();
		Long days = duration.toDaysPart();
		if (days >= 7) {
			Integer weeks = Math.floorMod(days, 7);
			buffer.append(weeks+" weeks ");
			days -= weeks*7;
		}
		if (days > 0) buffer.append(duration.toDays()+" days ");
		
		Integer value = duration.toHoursPart();
		if (value > 0) buffer.append(value+" hours ");
		value = duration.toMinutesPart();
		if (value > 0) buffer.append(value+" minutes ");
		value = duration.toSecondsPart();
		if (value > 0) buffer.append(value+" seconds");

		return buffer.toString();
	}

	public static String durationToLocalizedString(LocaleUtil lu, DiscordLocale locale, Duration duration) {
		if (duration.isZero()) {
			return "0 %s".formatted(lu.getLocalized(locale, "misc.time.seconds"));
		}

		StringBuffer buffer = new StringBuffer();
		Long days = duration.toDaysPart();
		if (days >= 7L) {
			Long weeks = Math.floorDiv(days, 7L);
			buffer.append("%s %s ".formatted(weeks, lu.getLocalized(locale, "misc.time.weeks")));
			days = Math.floorMod(days, 7L);
		}
		if (days > 0) buffer.append("%s %s ".formatted(days, lu.getLocalized(locale, "misc.time.days")));
		
		Integer value = duration.toHoursPart();
		if (value > 0) buffer.append("%s %s ".formatted(value, lu.getLocalized(locale, "misc.time.hours")));
		value = duration.toMinutesPart();
		if (value > 0) buffer.append("%s %s ".formatted(value, lu.getLocalized(locale, "misc.time.minutes")));
		value = duration.toSecondsPart();
		if (value > 0) buffer.append("%s %s".formatted(value, lu.getLocalized(locale, "misc.time.seconds")));

		return buffer.toString();
	}

	public static String formatTime(TemporalAccessor time, Boolean full) {
		if (time != null) {
			if (full) {
				return String.format(
					"%s (%s)",
					TimeFormat.DATE_TIME_SHORT.format(time),
					TimeFormat.RELATIVE.format(time)
				);
			}
			return String.format(
				"%s %s",
				TimeFormat.DATE_SHORT.format(time),
				TimeFormat.TIME_SHORT.format(time)
			);
		}
		return "";
	}

}
