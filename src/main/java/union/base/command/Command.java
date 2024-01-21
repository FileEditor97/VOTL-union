/*
 * Copyright 2016-2018 John Grosh (jagrosh) & Kaidan Gustave (TheMonitorLizard)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package union.base.command;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.BiConsumer;

import jakarta.annotation.Nonnull;


/**
 * <h2><b>Commands In JDA-Utilities</b></h2>
 *
 * <p>The internal inheritance for Commands used in JDA-Utilities is that of the Command object.
 *
 * <p>Classes created inheriting this class gain the unique traits of commands operated using the Commands Extension.
 * <br>Using several fields, a command can define properties that make it unique and complex while maintaining
 * a low level of development.
 * <br>All Commands extending this class can define any number of these fields in a object constructor and then
 * create the command action/response in the abstract
 * {@link union.base.command.Command#execute(CommandEvent) #execute(CommandEvent)} body:
 *
 * <pre><code> public class ExampleCmd extends Command {
 *
 *      public ExampleCmd() {
 *          this.name = "example";
 *          this.aliases = new String[]{"test","demo"};
 *          this.help = "gives an example of commands do";
 *      }
 *
 *      {@literal @Override}
 *      protected void execute(CommandEvent) {
 *          event.reply("Hey look! This would be the bot's reply if this was a command!");
 *      }
 *
 * }</code></pre>
 *
 * Execution is with the provision of a MessageReceivedEvent-CommandClient wrapper called a
 * {@link union.base.command.CommandEvent CommandEvent} and is performed in two steps:
 * <ul>
 *     <li>{@link union.base.command.Command#run(CommandEvent) run} - The command runs
 *     through a series of conditionals, automatically terminating the command instance if one is not met,
 *     and possibly providing an error response.</li>
 *
 *     <li>{@link union.base.command.Command#execute(CommandEvent) execute} - The command,
 *     now being cleared to run, executes and performs whatever lies in the abstract body method.</li>
 * </ul>
 *
 * @author John Grosh (jagrosh)
 */
public abstract class Command extends Interaction
{
	/**
	 * The name of the command, allows the command to be called the formats: <br>
	 * Normal Command: {@code [prefix]<command name>}. <br>
	 * Slash Command: {@code /<command name>}
	 */
	@Nonnull
	protected String name = "null";

	/**
	 * A small help String that summarizes the function of the command, used in the default help builder,
	 * and shown in the client for Slash Commands.
	 */
	@Nonnull
	protected String help = "no help available";

	/**
	 * The {@link union.base.command.Command.Category Category} of the command.
	 * <br>This can perform any other checks not completed by the default conditional fields.
	 */
	protected Category category = null;

	/**
	 * An arguments format String for the command, used in the default help builder.
	 * Not supported for SlashCommands.
	 * @see SlashCommand#options
	 */
	protected String arguments = null;

	/**
	 * {@code true} if the command may only be used in an NSFW
	 * {@link TextChannel} or DMs.
	 * {@code false} if it may be used anywhere
	 * <br>Default: {@code false}
	 */
	protected boolean nsfwOnly = false;

	/**
	 * A String name of a role required to use this command.
	 */
	protected String requiredRole = null;

	/**
	 * The aliases of the command, when calling a command these function identically to calling the
	 * {@link union.base.command.Command#name Command.name}.
	 * This options only works for normal commands, not slash commands.
	 */
	protected String[] aliases = new String[0];

	/**
	 * The child commands of the command. These are used in the format {@code [prefix]<parent name>
	 * <child name>}.
	 */
	protected Command[] children = new Command[0];

	/**
	 * The {@link java.util.function.BiConsumer BiConsumer} for creating a help response to the format
	 * {@code [prefix]<command name> help}.
	 */
	protected BiConsumer<CommandEvent, Command> helpBiConsumer = null;

	/**
	 * {@code true} if this command checks a channel topic for topic-tags.
	 * <br>This means that putting {@code {-commandname}}, {@code {-command category}}, {@code {-all}} in a channel topic
	 * will cause this command to terminate.
	 * <br>Default {@code true}.
	 */
	protected boolean usesTopicTags = true;

	/**
	 * {@code true} if this command should be hidden from the help.
	 * <br>Default {@code false}<br>
	 * <b>This has no effect for SlashCommands.</b>
	 */
	protected boolean hidden = false;

	/**
	 * The main body method of a {@link union.base.command.Command Command}.
	 * <br>This is the "response" for a successful
	 * {@link union.base.command.Command#run(CommandEvent) #run(CommandEvent)}.
	 *
	 * @param  event
	 *         The {@link union.base.command.CommandEvent CommandEvent} that
	 *         triggered this Command
	 */
	protected abstract void execute(CommandEvent event);

	/**
	 * Runs checks for the {@link union.base.command.Command Command} with the
	 * given {@link union.base.command.CommandEvent CommandEvent} that called it.
	 * <br>Will terminate, and possibly respond with a failure message, if any checks fail.
	 *
	 * @param  event
	 *         The CommandEvent that triggered this Command
	 */
	public final void run(CommandEvent event) {
		// client 
		final CommandClient client = event.getClient();

		// child check
		if (!event.getArgs().isEmpty()) {
			String[] parts = Arrays.copyOf(event.getArgs().split("\\s+",2), 2);
			if(helpBiConsumer!=null && parts[0].equalsIgnoreCase(client.getHelpWord())) {
				helpBiConsumer.accept(event, this);
				return;
			}
			for (Command cmd : getChildren()) {
				if (cmd.isCommandFor(parts[0])) {
					event.setArgs(parts[1]==null ? "" : parts[1]);
					cmd.run(event);
					return;
				}
			}
		}

		// check owner command
		if (ownerCommand && !(event.isOwner())) {
			terminate(event, bot.getEmbedUtil().getError(null, "errors.command.not_owner"));
			return;
		}

		// category check
		if (category!=null && !category.test(event)) {
			//terminate(event, bot.getEmbedUtil().getError(event, "errors.unknown", category.getFailureResponse()));
			terminate(event, bot.getEmbedUtil().getError(null, "errors.unknown", category.getFailureResponse()));
			return;
		}

		if (event.getChannelType() != ChannelType.PRIVATE) {
/* 			Guild guild = event.getGuild();
			Member author = event.getMember();
			if (mustSetup) {
				try {
					bot.getCheckUtil()
					// check setup
						.guildExists(event, guild)
					// check module enabled
						.moduleEnabled(event, guild, getModule())
					// check access
						.hasAccess(event, author, getAccessLevel());
				} catch (CheckException ex) {
					terminate(event, ex.getCreateData());
					return;
				}
			}
			try {
				bot.getCheckUtil()
				// check user perms
					.hasPermissions(event, guild, author, getUserPermissions())
				// check bots perms
					.hasPermissions(event, guild, author, true, getBotPermissions());
			} catch (CheckException ex) {
				terminate(event, ex.getCreateData());
				return;
			} */

			// nsfw check
			if (nsfwOnly && event.getChannelType() == ChannelType.TEXT && !event.getTextChannel().isNSFW()) {
				terminate(event, bot.getEmbedUtil().getError(null, "errors.command.nsfw"));
				return;
			}
		} else if (guildOnly) {
			terminate(event, bot.getEmbedUtil().getError(null, "errors.command.guild_only"));
			return;
		}

		// cooldown check, ignoring owner
		if (cooldown>0 && !(event.isOwner())) {
			String key = getCooldownKey(event);
			int remaining = client.getRemainingCooldown(key);
			if (remaining>0) {
				terminate(event, getCooldownError(event, remaining));
				return;
			}
			else client.applyCooldown(key, cooldown);
		}

		// run
		try {
			execute(event);
		} catch(Throwable t) {
			if (client.getListener() != null) {
				client.getListener().onCommandException(event, this, t);
				return;
			}
			// otherwise we rethrow
			throw t;
		}

		if(client.getListener() != null)
			client.getListener().onCompletedCommand(event, this);
	}

	/**
	 * Checks if the given input represents this Command
	 *
	 * @param  input
	 *         The input to check
	 *
	 * @return {@code true} if the input is the name or an alias of the Command
	 */
	public boolean isCommandFor(String input)
	{
		if(name.equalsIgnoreCase(input))
			return true;
		for(String alias: aliases)
			if(alias.equalsIgnoreCase(input))
				return true;
		return false;
	}

	/**
	 * Gets the {@link union.base.command.Command#name Command.name} for the Command.
	 *
	 * @return The name for the Command
	 */
	@Nonnull
	public String getName()
	{
		return name;
	}

	/**
	 * Gets the {@link union.base.command.Command#help Command.help} for the Command.
	 *
	 * @return The help for the Command
	 */
	@Nonnull
	public String getHelp()
	{
		return help;
	}

	/**
	 * Gets the {@link union.base.command.Command#category Command.category} for the Command.
	 *
	 * @return The category for the Command
	 */
	public Category getCategory()
	{
		return category;
	}

	/**
	 * Gets the {@link union.base.command.Command#arguments Command.arguments} for the Command.
	 *
	 * @return The arguments for the Command
	 */
	public String getArguments()
	{
		return arguments;
	}

	/**
	 * Checks if this Command can only be used in a {@link net.dv8tion.jda.api.entities.Guild Guild}.
	 *
	 * @return {@code true} if this Command can only be used in a Guild, else {@code false} if it can
	 *         be used outside of one
	 */
	public boolean isGuildOnly()
	{
		return guildOnly;
	}

	/**
	 * Gets the {@link union.base.command.Command#requiredRole Command.requiredRole} for the Command.
	 *
	 * @return The requiredRole for the Command
	 */
	public String getRequiredRole()
	{
		return requiredRole;
	}

	/**
	 * Gets the {@link union.base.command.Command#aliases Command.aliases} for the Command.
	 *
	 * @return The aliases for the Command
	 */
	public String[] getAliases()
	{
		return aliases;
	}

	/**
	 * Gets the {@link union.base.command.Command#children Command.children} for the Command.
	 *
	 * @return The children for the Command
	 */
	public Command[] getChildren()
	{
		return children;
	}

	/**
	 * Checks whether or not this command should be hidden from the help.
	 *
	 * @return {@code true} if the command should be hidden, otherwise {@code false}
	 */
	public boolean isHidden()
	{
		return hidden;
	}

	private void terminate(CommandEvent event, @Nonnull MessageEmbed embed) {
		terminate(event, MessageCreateData.fromEmbeds(embed));
	}

	private void terminate(CommandEvent event, MessageCreateData message)
	{
		if(message!=null)
			event.reply(message);
		if(event.getClient().getListener()!=null)
			event.getClient().getListener().onTerminatedCommand(event, this);
	}

	/**
	 * Gets the proper cooldown key for this Command under the provided
	 * {@link union.base.command.CommandEvent CommandEvent}.
	 *
	 * @param  event
	 *         The CommandEvent to generate the cooldown for.
	 *
	 * @return A String key to use when applying a cooldown.
	 */
	public String getCooldownKey(CommandEvent event)
	{
		switch (cooldownScope)
		{
			case USER:         return cooldownScope.genKey(name,event.getAuthor().getIdLong());
			case USER_GUILD:   return event.getGuild()!=null ? cooldownScope.genKey(name,event.getAuthor().getIdLong(),event.getGuild().getIdLong()) :
				CooldownScope.USER_CHANNEL.genKey(name,event.getAuthor().getIdLong(), event.getChannel().getIdLong());
			case USER_CHANNEL: return cooldownScope.genKey(name,event.getAuthor().getIdLong(),event.getChannel().getIdLong());
			case GUILD:        return event.getGuild()!=null ? cooldownScope.genKey(name,event.getGuild().getIdLong()) :
				CooldownScope.CHANNEL.genKey(name,event.getChannel().getIdLong());
			case CHANNEL:      return cooldownScope.genKey(name,event.getChannel().getIdLong());
			case SHARD:        return event.getJDA().getShardInfo()!= JDA.ShardInfo.SINGLE ? cooldownScope.genKey(name, event.getJDA().getShardInfo().getShardId()) :
				CooldownScope.GLOBAL.genKey(name, 0);
			case USER_SHARD:   return event.getJDA().getShardInfo()!= JDA.ShardInfo.SINGLE ? cooldownScope.genKey(name,event.getAuthor().getIdLong(),event.getJDA().getShardInfo().getShardId()) :
				CooldownScope.USER.genKey(name, event.getAuthor().getIdLong());
			case GLOBAL:       return cooldownScope.genKey(name, 0);
			default:           return "";
		}
	}

	/**
	 * Gets an error message for this Command under the provided
	 * {@link union.base.command.CommandEvent CommanEvent}.
	 *
	 * @param  event
	 *         The CommandEvent to generate the error message for.
	 * @param  remaining
	 *         The remaining number of seconds a command is on cooldown for.
	 *
	 * @return A String error message for this command if {@code remaining > 0},
	 *         else {@code null}.
	 */
	public MessageCreateData getCooldownError(CommandEvent event, int remaining) {
		if (remaining <= 0)
			return null;
		
		StringBuilder front = new StringBuilder(lu.getText(null, "errors.cooldown.cooldown_command")
			.replace("{time}", Integer.toString(remaining))
		);
		if(cooldownScope.equals(CooldownScope.USER))
			{}
		else if(cooldownScope.equals(CooldownScope.USER_GUILD) && event.getGuild()==null)
			front.append(" " + lu.getText(null, CooldownScope.USER_CHANNEL.errorPath));
		else if(cooldownScope.equals(CooldownScope.GUILD) && event.getGuild()==null)
			front.append(" " + lu.getText(null, CooldownScope.CHANNEL.errorPath));
		else
			front.append(" " + lu.getText(null, cooldownScope.errorPath));
		
		return MessageCreateData.fromContent(Objects.requireNonNull(front.append("!").toString()));
	}

}