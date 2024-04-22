package union.base.command;

import java.util.Objects;

/**
 * To be used in {@link SlashCommand SlashCommand}s as a means of
 * organizing commands into "Categories".
 *
 * @author John Grosh (jagrosh)
 */
public record Category(String name) {
	/**
	 * A Command Category containing a name.
	 *
	 * @param name The name of the Category
	 */
	public Category {
	}

	/**
	 * Gets the name of the Category.
	 *
	 * @return The name of the Category
	 */
	@Override
	public String name() {
		return name;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Category other))
			return false;
		return Objects.equals(name, other.name);
	}

}
