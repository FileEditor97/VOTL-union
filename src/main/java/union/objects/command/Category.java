package union.objects.command;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * To be used in {@link union.objects.command.Command Command}s as a means of
 * organizing commands into "Categories" as well as terminate command usage when the calling
 * {@link union.objects.command.CommandEvent CommandEvent} doesn't meet
 * certain requirements.
 *
 * @author John Grosh (jagrosh)
 */
public class Category
{
	private final String name;
	private final String failResponse;
	private final Predicate<CommandEvent> predicate;

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
		this.predicate = null;
	}

	/**
	 * A Command Category containing a name and a {@link java.util.function.Predicate}.
	 *
	 * <p>The command will be terminated if
	 * {@link union.objects.command.Command.Category#test(union.objects.command.CommandEvent)}
	 * returns {@code false}.
	 *
	 * @param  name
	 *         The name of the Category
	 * @param  predicate
	 *         The Category predicate to test
	 */
	public Category(String name, Predicate<CommandEvent> predicate)
	{
		this.name = name;
		this.failResponse = null;
		this.predicate = predicate;
	}

	/**
	 * A Command Category containing a name, a {@link java.util.function.Predicate},
	 * and a failure response.
	 *
	 * <p>The command will be terminated if
	 * {@link union.objects.command.Command.Category#test(union.objects.command.CommandEvent)}
	 * returns {@code false}, and the failure response will be sent.
	 *
	 * @param  name
	 *         The name of the Category
	 * @param  failResponse
	 *         The response if the test fails
	 * @param  predicate
	 *         The Category predicate to test
	 */
	public Category(String name, String failResponse, Predicate<CommandEvent> predicate)
	{
		this.name = name;
		this.failResponse = failResponse;
		this.predicate = predicate;
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

	/**
	 * Runs a test of the provided {@link java.util.function.Predicate}.
	 * Does not support SlashCommands.
	 *
	 * @param  event
	 *         The {@link union.objects.command.CommandEvent CommandEvent}
	 *         that was called when this method is invoked
	 *
	 * @return {@code true} if the Predicate was not set, was set as null, or was
	 *         tested and returned true, otherwise returns {@code false}
	 */
	public boolean test(CommandEvent event)
	{
		return predicate==null || predicate.test(event);
	}

	@Override
	public boolean equals(Object obj)
	{
		if(!(obj instanceof Category))
			return false;
		Category other = (Category)obj;
		return Objects.equals(name, other.name) && Objects.equals(predicate, other.predicate) && Objects.equals(failResponse, other.failResponse);
	}

	@Override
	public int hashCode()
	{
		int hash = 7;
		hash = 17 * hash + Objects.hashCode(this.name);
		hash = 17 * hash + Objects.hashCode(this.failResponse);
		hash = 17 * hash + Objects.hashCode(this.predicate);
		return hash;
	}
}
