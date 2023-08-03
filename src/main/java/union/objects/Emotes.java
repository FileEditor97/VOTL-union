package union.objects;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.dv8tion.jda.api.entities.emoji.Emoji;

public enum Emotes {
	// Animated emotes
	LOADING     ("loading",     960102018217828352L, true),
	TYPING      ("typing",      960102038291742750L, true),
	THINKING	("thinking",    960102089919447080L, true),
	// Static/Normal emotes
	CHECK       ("check",       960101819428769812L, false),
	WARNING     ("warning",     960101573571276820L, false),
	INFORMATION ("information", 960101921362964511L, false),
	SETTINGS_1  ("settings_1",  960101709630275584L, false),
	SETTINGS_2  ("settings_2",  960101748775714816L, false),
	SETTINGS_3  ("settings_3",  960101769097150474L, false),
	PING        ("ping",        960101551857360906L, false),
	CLOUD       ("cloud",       960101979659599872L, false),
	DOWNLOAD    ("download",    960101994402562068L, false),
	FAVORITES   ("favorites",   960101970771845161L, false),
	SHIELD      ("shield",      960101908750663760L, false),
	TROPHY      ("trophy",      960101605091454996L, false),
	MEGAPHONE   ("megaphone",   960101946243571802L, false),
	POWER       ("power",       960101627136737280L, false),
	ADDUSER     ("adduser",     960101846687551508L, false),
	REMOVEUSER  ("removeuser",  960101868577648640L, false),
	NONE		("none",        1095073050657034321L, false),
	CHECK_C		("color_check", 1043105156700577842L, false),
	CROSS_C		("color_cross", 1043105216133865522L, false),
	WARNING_C	("color_warning", 1043105732347834428L, false);

	private static final Pattern emote_pattern = Pattern.compile("\\{EMOTE_(?<name>[A-Z0-9_]+)}");
	private static final Emotes[] ALL = values();
	
	private final String emoteName;
	private final Long id;
	private final boolean animated;

	Emotes(String emoteName, Long id, boolean animated) {
		this.emoteName = emoteName;
		this.id = id;
		this.animated = animated;
	}

	public String getEmote() {
		return String.format(
			"<%s:%s:%s>",
			this.animated ? "a" : "",
			this.emoteName,
			this.id
		);
	}

	public Emoji getEmoji() {
		return Emoji.fromCustom(this.emoteName, this.id, this.animated);
	}

	public String getNameAndId() {
		return String.format(
			"<%s:%s>",
			this.emoteName,
			this.id
		);
	}

	public Long getId() {
		return this.id;
	}

	@Nonnull
	public static String getWithEmotes(@Nonnull String input) {
		Matcher matcher = emote_pattern.matcher(input);
		if (matcher.find()) {
			StringBuilder builder = new StringBuilder();

			do {
				String name = getEmote(matcher.group("name"));
				if (name == null)
					continue;
			
				matcher.appendReplacement(builder, name);
			} while (matcher.find());

			matcher.appendTail(builder);
			return Objects.requireNonNull(builder.toString());
		}

		return input;
	}

	@Nullable
	private static String getEmote(String name) {
		for (Emotes emote : ALL) {
			if (emote.name().equalsIgnoreCase(name))
				return emote.getEmote();
		}

		return null;
	}
}
