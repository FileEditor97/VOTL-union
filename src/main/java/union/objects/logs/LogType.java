package union.objects.logs;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import union.utils.file.lang.LocaleUtil;

import net.dv8tion.jda.api.interactions.commands.Command.Choice;

public enum LogType {
	MODERATION("moderation"),
	GROUP("groups"),
	VERIFICATION("verify"),
	TICKET("ticket"),
	ROLE("role"),
	GUILD("guild"),
	MESSAGE("message"),
	MEMBER("member"),
	VOICE("voice"),
	CHANNEL("channel"),
	INVITE("invite"),
	OTHER("other");

	private final String name;
	private final String path;

	private static final Set<String> ALL = new HashSet<>();
	private static final Map<String, LogType> BY_NAME = new HashMap<String, LogType>();

	static {
		for (LogType lc : LogType.values()) {
			ALL.add(lc.getName());
			BY_NAME.put(lc.getName(), lc);
		}
	}

	LogType(String name) {
		this.name = name;
		this.path = "logger."+name;
	}

	public String getName() {
		return this.name;
	}

	public String getPath() {
		return this.path;
	}

	public String getPathName() {
		return this.path+".name";
	}

	public static List<Choice> asChoices(LocaleUtil lu) {
		return Stream.of(values()).map(log -> new Choice(lu.getText(log.getPathName()), log.getName())).toList();
	}

	public static Set<String> getAllNames() {
		return ALL;
	}

	public static LogType of(String name) {
		LogType result = BY_NAME.get(name);
		if (result == null) {
			throw new IllegalArgumentException("Invalid DB name: " + name);
		}
		return result;
	}

}
