package union.objects;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import net.dv8tion.jda.api.entities.emoji.Emoji;

public enum Emotes {
	// Animated emotes
	LOADING     ("loading",     960102018217828352L, true),
	TYPING      ("typing",      960102038291742750L, true),
	THINKING	("thinking",    960102089919447080L, true),
	// Static/Normal emotes
	CHECK       ("check",       960101819428769812L, false),
	WARNING     ("warning",     1202299075534262342L, false),
	INFORMATION ("information", 1202299059524599859L, false),
	SETTINGS_1  ("settings_1",  1202299068630700072L, false),
	SETTINGS_2  ("settings_2",  1202299070723653652L, false),
	SETTINGS_3  ("settings_3",  960101769097150474L, false),
	PING        ("ping",        1202299063807254528L, false),
	CLOUD       ("cloud",       1202299056861225010L, false),
	DOWNLOAD    ("download",    960101994402562068L, false),
	FAVORITES   ("favorites",   1202299058211782716L, false),
	SHIELD      ("shield",      1202299072476610580L, false),
	TROPHY      ("trophy",      1202299146942562324L, false),
	MEGAPHONE   ("megaphone",   1202299062557356093L, false),
	POWER       ("power",       1202299065904136252L, false),
	ADDUSER     ("adduser",     1202299046065082368L, false),
	REMOVEUSER  ("removeuser",  1202299067280138260L, false),
	NONE		("circle",      1202299054575603882L, false),
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
