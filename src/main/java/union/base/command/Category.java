package union.base.command;

import java.util.Objects;

/**
 * To be used in {@link union.base.command.SlashCommand SlashCommand}s as a means of
 * organizing commands into "Categories" as well as terminate command usage when the calling
 * {@link union.base.command.CommandEvent CommandEvent} doesn't meet
 * certain requirements.
 *
 * @author John Grosh (jagrosh)
 */
public class Category
{
	private final String name;
	private final String failResponse;

	/**
	 * A Command Category containing a name.
	 *
	 * @param  name
	 *         The name of the Category
	 */
	public Category(String name)
	{
		this.name = name;
		this.failResponse = null;
	}

	/**
	 * A Command Category containing a name, a {@link java.util.function.Predicate},
	 * and a failure response.
	 *
	 * <p>The command will be terminated if
	 * test
	 * returns {@code false}, and the failure response will be sent.
	 *
	 * @param  name
	 *         The name of the Category
	 * @param  failResponse
	 *         The response if the test fails
	 * @param  predicate
	 *         The Category predicate to test
	 */
	public Category(String name, String failResponse)
	{
		this.name = name;
		this.failResponse = failResponse;
	}

	/**
	 * Gets the name of the Category.
	 *
	 * @return The name of the Category
	 */
	public String getName()
	{
		return name;
	}

	/**
	 * Gets the failure response of the Category.
	 *
	 * @return The failure response of the Category
	 */
	public String getFailureResponse()
	{
		return failResponse;
	}

	@Override
	public boolean equals(Object obj)
	{
		if(!(obj instanceof Category))
			return false;
		Category other = (Category)obj;
		return Objects.equals(name, other.name) && Objects.equals(failResponse, other.failResponse);
	}

	@Override
	public int hashCode()
	{
		int hash = 7;
		hash = 17 * hash + Objects.hashCode(this.name);
		hash = 17 * hash + Objects.hashCode(this.failResponse);
		return hash;
	}
}
