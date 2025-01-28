package union.utils.message;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.HashMap;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;
import union.utils.exception.FormatterException;
import union.utils.file.lang.LocaleUtil;

import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.utils.TimeFormat;

public class TimeUtil {

	private static final Pattern timePatternFull = Pattern.compile("^(([0-9]+)([smhdw]))+$", Pattern.CASE_INSENSITIVE);
	private static final Pattern timePattern = Pattern.compile("([0-9]+)([smhdw])", Pattern.CASE_INSENSITIVE);

	private enum TimeFormats{
		SECONDS('s', 1),
		MINUTES('m', 60),
		HOURS  ('h', 3600),
		DAYS   ('d', 86400),
		WEEKS  ('w', 604800);

		private final Character character;
		private final Integer multip;

		private static final HashMap<Character, TimeFormats> BY_CHAR = new HashMap<>();

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
			return Optional.ofNullable(BY_CHAR.get(c)).map(TimeFormats::getMultip).orElse(null);
		}
	}

	/*
	 * Duration and Period class have parse() method,
	 * but they are quite inconvenient, as we want to
	 * use both duration(h m s) and period(w d).
	 */
	public static Duration stringToDuration(String text, boolean allowSeconds) throws FormatterException {
		if (text == null || text.isEmpty() || text.equals("0")) {
			return Duration.ZERO;
		}

		if (!timePatternFull.matcher(text).matches()) {
			throw new FormatterException("errors.formatter.no_time_provided");
		}
		
		Matcher timeMatcher = timePattern.matcher(text);
		long time = 0L;
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
				time = Math.addExact(time, Math.multiplyExact(Long.parseLong(timeMatcher.group(1)), multip));
			} catch (NumberFormatException ex) {
				throw new FormatterException("errors.formatter.parse_long");
			} catch (ArithmeticException ex) {
				throw new FormatterException("errors.formatter.long_overflow");
			}
		}
		
		return Duration.ofSeconds(time);
	}

	@SuppressWarnings("unused")
	public static String durationToString(Duration duration) {
		if (duration.isZero()) {
			return "0 seconds";
		}

		StringBuilder builder = new StringBuilder();

		final long weeks = Math.floorDiv(duration.toDaysPart(), 7);
		final long days = Math.floorMod(duration.toDaysPart(), 7);
		final int hours = duration.toHoursPart();
		final int minutes = duration.toMinutesPart();
		final int seconds = duration.toSecondsPart();

		if (weeks > 0) {
			if (weeks==1)
				builder.append("1 week");
			else
				builder.append(weeks).append(" weeks");
		}
		if (days > 0) {
			if (!builder.isEmpty()) builder.append(" ");
			if (days==1)
				builder.append("1 day");
			else
				builder.append(days).append(" days");
		}
		if (hours > 0) {
			if (!builder.isEmpty()) builder.append(" ");
			if (hours==1)
				builder.append("1 hour");
			else
				builder.append(hours).append(" hours");
		}
		if (minutes > 0) {
			if (!builder.isEmpty()) builder.append(" ");
			if (minutes==1)
				builder.append("1 minute");
			else
				builder.append(minutes).append(" minutes");
		}
		if (seconds > 0) {
			if (!builder.isEmpty()) builder.append(" ");
			if (seconds==1)
				builder.append("1 second");
			else
				builder.append(seconds).append(" seconds");
		}


		return builder.toString();
	}

	public static String durationToLocalizedString(LocaleUtil lu, DiscordLocale locale, Duration duration) {
		if (duration.isZero()) {
			return "0 %s".formatted(lu.getLocalized(locale, "misc.time.seconds"));
		}

		StringBuilder builder = new StringBuilder();

		final long weeks = Math.floorDiv(duration.toDaysPart(), 7);
		final long days = Math.floorMod(duration.toDaysPart(), 7);
		final int hours = duration.toHoursPart();
		final int minutes = duration.toMinutesPart();
		final int seconds = duration.toSecondsPart();

		if (weeks > 0) {
			if (weeks==1)
				builder.append("1 %s".formatted(lu.getLocalized(locale, "misc.time.w1")));
			else if (weeks<5)
				builder.append("%s %s".formatted(weeks, lu.getLocalized(locale, "misc.time.w2")));
			else
				builder.append("%s %s".formatted(weeks, lu.getLocalized(locale, "misc.time.w5")));
		}
		if (days > 0) {
			if (!builder.isEmpty()) {
				builder.append(", ");
				if (hours==0&&minutes==0&&seconds==0) builder.append(lu.getLocalized(locale, "misc.and")).append(" ");
			}
			if (days==1)
				builder.append("1 %s".formatted(lu.getLocalized(locale, "misc.time.d1")));
			else if (days<5)
				builder.append("%s %s".formatted(days, lu.getLocalized(locale, "misc.time.d2")));
			else
				builder.append("%s %s".formatted(days, lu.getLocalized(locale, "misc.time.d5")));
		}
		if (hours > 0) {
			if (!builder.isEmpty()) {
				builder.append(", ");
				if (minutes==0&&seconds==0) builder.append(lu.getLocalized(locale, "misc.and")).append(" ");
			}
			if (hours==1)
				builder.append("1 %s".formatted(lu.getLocalized(locale, "misc.time.h1")));
			else if (hours<5)
				builder.append("%s %s".formatted(hours, lu.getLocalized(locale, "misc.time.h2")));
			else
				builder.append("%s %s".formatted(hours, lu.getLocalized(locale, "misc.time.h5")));
		}
		if (minutes > 0) {
			if (!builder.isEmpty()) {
				builder.append(", ");
				if (seconds==0) builder.append(lu.getLocalized(locale, "misc.and")).append(" ");
			}
			if (minutes==1)
				builder.append("1 %s".formatted(lu.getLocalized(locale, "misc.time.m1")));
			else if (minutes<5)
				builder.append("%s %s".formatted(minutes, lu.getLocalized(locale, "misc.time.m2")));
			else
				builder.append("%s %s".formatted(minutes, lu.getLocalized(locale, "misc.time.m5")));
		}
		if (seconds > 0) {
			if (!builder.isEmpty()) {
				builder.append(", ").append(lu.getLocalized(locale, "misc.and")).append(" ");
			}
			if (seconds==1)
				builder.append("1 %s".formatted(lu.getLocalized(locale, "misc.time.s1")));
			else if (seconds<5)
				builder.append("%s %s".formatted(seconds, lu.getLocalized(locale, "misc.time.s2")));
			else
				builder.append("%s %s".formatted(seconds, lu.getLocalized(locale, "misc.time.s5")));
		}

		return builder.toString();
	}

	public static String formatTime(TemporalAccessor time, boolean full) {
		if (time == null) return "";
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

	private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
		.withZone(ZoneId.systemDefault());

	public static String timeToString(TemporalAccessor time) {
		if (time == null) return "indefinitely";
		return formatter.format(time);
	}

	public static String formatDuration(LocaleUtil lu, DiscordLocale locale, Instant startTime, Duration duration) {
		return duration.isZero() ?
			lu.getLocalized(locale, "misc.permanently")
			:
			lu.getLocalized(locale, "misc.temporary").formatted(formatTime(startTime.plus(duration), false));
	}

}
