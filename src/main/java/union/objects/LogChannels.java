package union.objects;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import union.utils.message.LocaleUtil;

import net.dv8tion.jda.api.interactions.commands.Command.Choice;

public enum LogChannels {
	MODERATION("moderation"),
	GROUPS("groups"),
	VERIFICATION("verify"),
	TICKETS("tickets"),
	ROLES("roles"),
	SERVER("server"),
	MESSAGES("messages"),
	MEMBERS("members"),
	VOICE("voice"),
	CHANNELS("channels"),
	OTHER("other");

	private final String name;
	private final String path;

	private static final Set<String> ALL = new HashSet<>();
	private static final Map<String, LogChannels> BY_NAME = new HashMap<String, LogChannels>();

	static {
		for (LogChannels lc : LogChannels.values()) {
			ALL.add(lc.getName());
			BY_NAME.put(lc.getName(), lc);
		}
	}

	LogChannels(String name) {
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

	public static LogChannels of(String name) {
		LogChannels result = BY_NAME.get(name);
		if (result == null) {
			throw new IllegalArgumentException("Invalid DB name: " + name);
		}
		return result;
	}

}
