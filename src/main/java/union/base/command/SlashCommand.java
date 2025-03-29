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

import java.util.*;

import net.dv8tion.jda.api.interactions.InteractionContextType;
import org.jetbrains.annotations.NotNull;
import union.metrics.Metrics;
import union.metrics.datapoints.Timer;
import union.objects.CmdAccessLevel;
import union.utils.exception.CheckException;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.*;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

/**
 * <h2><b>Slash Commands In JDA-Chewtils</b></h2>
 *
 * <p>This intends to mimic the {@link Command command} with minimal breaking changes,
 * to make migration easy and smooth.</p>
 * <p>Breaking changes are documented
 * <a href="https://github.com/Chew/JDA-Chewtils/wiki/Command-to-SlashCommand-Migration">here</a>.</p>
 * {@link SlashCommand#execute(SlashCommandEvent) #execute(CommandEvent)} body:
 *
 * <pre><code> public class ExampleCmd extends SlashCommand {
 *
 *      public ExampleCmd() {
 *          this.name = "example";
 *          this.help = "gives an example of commands do";
 *      }
 *
 *      {@literal @Override}
 *      protected void execute(SlashCommandEvent event) {
 *          event.reply("Hey look! This would be the bot's reply if this was a command!").queue();
 *      }
 *
 * }</code></pre>
 *
 * Execution is with the provision of the SlashCommandEvent is performed in two steps:
 * <ul>
 *     <li>{@link SlashCommand#run(SlashCommandEvent) run} - The command runs
 *     through a series of conditionals, automatically terminating the command instance if one is not met,
 *     and possibly providing an error response.</li>
 *
 *     <li>{@link SlashCommand#execute(SlashCommandEvent) execute} - The command,
 *     now being cleared to run, executes and performs whatever lies in the abstract body method.</li>
 * </ul>
 *
 * @author Olivia (Chew)
 */
public abstract class SlashCommand extends Interaction
{
	/**
	 * The name of the command, allows the command to be called the formats: <br>
	 * Slash Command: {@code /<command name>}
	 */
	@NotNull
	protected String name = "null";

	/**
	 * A small help String that summarizes the function of the command, used in the default help builder,
	 * and shown in the client for Slash Commands.
	 */
	@NotNull
	protected String help = "no help available";

	/**
	 * The {@link union.base.command.Category Category} of the command.
	 * <br>This can perform any other checks not completed by the default conditional fields.
	 */
	protected Category category = null;

	/**
	 * {@code true} if the command may only be used in an NSFW
	 * {@link TextChannel} or DMs.
	 * {@code false} if it may be used anywhere
	 * <br>Default: {@code false}
	 */
	protected boolean nsfwOnly = false;

	/**
	 * Localization of slash command name. Allows discord to change the language of the name of slash commands in the client.<br>
	 * Example:<br>
	 *<pre><code>
	 *     public Command() {
	 *          this.name = "help"
	 *          this.nameLocalization = Map.of(DiscordLocale.GERMAN, "hilfe", DiscordLocale.RUSSIAN, "помощь");
	 *     }
	 *</code></pre>
	 */
	@NotNull
	protected Map<DiscordLocale, String> nameLocalization = new HashMap<>();

	/**
	 * Localization of slash command description. Allows discord to change the language of the description of slash commands in the client.<br>
	 * Example:<br>
	 *<pre><code>
	 *     public Command() {
	 *          this.description = "all commands"
	 *          this.descriptionLocalization = Map.of(DiscordLocale.GERMAN, "alle Befehle", DiscordLocale.RUSSIAN, "все команды");
	 *     }
	 *</code></pre>
	 */
	@NotNull
	protected Map<DiscordLocale, String> descriptionLocalization = new HashMap<>();

	/**
	 * The child commands of the command. These are used in the format {@code /<parent name>
	 * <child name>}.
	 * This is synonymous with sub commands. Additionally, sub-commands cannot have children.<br>
	 */
	protected SlashCommand[] children = new SlashCommand[0];

	/**
	 * The subcommand/child group this is associated with.
	 * Will be in format {@code /<parent name> <subcommandGroup name> <subcommand name>}.
	 * <p>
	 * <b>This only works in a child/subcommand.</b>
	 * <p>
	 * To instantiate: <code>{@literal new SubcommandGroupData(name, description)}</code><br>
	 * It's important the instantiations are the same across children if you intend to keep them in the same group.
	 * <p>
	 * Can be null, and it will not be assigned to a group.
	 */
	protected SubcommandGroupData subcommandGroup = null;

	/**
	 * An array list of OptionData.
	 * <p>
	 * <b>This is incompatible with children. You cannot have a child AND options.</b>
	 * <p>
	 * This is to specify different options for arguments and the stuff.
	 * <p>
	 * For example, to add an argument for "input", you can do this:<br>
	 * <pre><code>
	 *     OptionData data = new OptionData(OptionType.STRING, "input", "The input for the command").setRequired(true);
	 *    {@literal List<OptionData> dataList = new ArrayList<>();}
	 *     dataList.add(data);
	 *     this.options = dataList;</code></pre>
	 */
	protected List<OptionData> options = new ArrayList<>();

	/**
	 * The main body method of a {@link SlashCommand SlashCommand}.
	 * <br>This is the "response" for a successful
	 * {@link SlashCommand#run(SlashCommandEvent) #run(CommandEvent)}.
	 *
	 * @param  event
	 *         The {@link SlashCommandEvent SlashCommandEvent} that
	 *         triggered this Command
	 */
	protected abstract void execute(SlashCommandEvent event);

	/**
	 * This body is executed when an auto-complete event is received.
	 * This only ever gets executed if an auto-complete {@link #options option} is set.
	 *
	 * @param event The event to handle.
	 * @see OptionData#setAutoComplete(boolean)
	 */
	@SuppressWarnings("unused")
	public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {}

	/**
	 * Runs checks for the {@link SlashCommand SlashCommand} with the
	 * given {@link SlashCommandEvent SlashCommandEvent} that called it.
	 * <br>Will terminate, and possibly respond with a failure message, if any checks fail.
	 *
	 * @param  event
	 *         The SlashCommandEvent that triggered this Command
	 */
	public final void run(SlashCommandEvent event) {
		// start time
		final long timeStart = System.currentTimeMillis();
		// client
		final CommandClient client = event.getClient();

		// check owner command
		if (ownerCommand && !isOwner(event, client)) {
			terminate(event, bot.getEmbedUtil().getError(event, "errors.command.not_owner"), client);
			return;
		}

		// cooldown check, ignoring owner
		if (cooldown > 0 && !isOwner(event, client)) {
			String key = getCooldownKey(event);
			int remaining = client.getRemainingCooldown(key);
			if (remaining > 0) {
				terminate(event, getCooldownErrorEmbed(event, remaining), client);
				return;
			} else {
				client.applyCooldown(key, cooldown);
			}
		}

		// check db and permissions
		if (event.isFromGuild() && !ownerCommand) {
			Guild guild = event.getGuild();
			Member author = event.getMember();
			try {
				bot.getCheckUtil()
				// check module enabled
					.moduleEnabled(event, guild, getModule())
				// check access
					.hasAccess(event, author, getAccessLevel())
				// check bots perms
					.hasPermissions(event, getBotPermissions());
			} catch (CheckException ex) {
				terminate(event, ex.getCreateData(), client);
				return;
			}
		}

		// Record time
		bot.getAppLogger().debug("SlashCommand check duration: {}ms @ {} ", System.currentTimeMillis()-timeStart, event.getResponseNumber());

		// Metrics
		Metrics.commandsExecuted.labelValue(event.getFullCommandName()).inc();
		// execute
		try (Timer ignored = Metrics.executionTime.labelValue(event.getFullCommandName()).startTimer()) {
			execute(event);
		} catch (Throwable t) {
			if (client.getListener() != null) {
				client.getListener().onSlashCommandException(event, this, t);
				return;
			}
			// otherwise we rethrow
			throw t;
		}

		if (client.getListener() != null)
			client.getListener().onCompletedSlashCommand(event, this);
	}

	/**
	 * Tests whether the {@link net.dv8tion.jda.api.entities.User User} who triggered this
	 * event is an owner of the bot.
	 *
	 * @param event the event that triggered the command
	 * @param client the command client for checking stuff
	 * @return {@code true} if the User is the Owner, else {@code false}
	 */
	public boolean isOwner(SlashCommandEvent event, CommandClient client) {
        return client.getOwnerId() == event.getUser().getIdLong();
    }

	/**
	 * Checks if the given input represents this Command
	 *
	 * @param  input
	 *         The input to check
	 *
	 * @return {@code true} if the input is the name or an alias of the Command
	 */
	public boolean isCommandFor(String input) {
		return name.equalsIgnoreCase(input);
    }

	/**
	 * Gets the {@link union.base.command.SlashCommand#name SlashCommand.name} for the Command.
	 *
	 * @return The name for the Command
	 */
	@NotNull
	public String getName() {
		return name;
	}

	/**
	 * Gets the {@link union.base.command.SlashCommand#help SlashCommand.help} for the Command.
	 *
	 * @return The help for the Command
	 */
	@NotNull
	public String getHelp() {
		return help;
	}

	/**
	 * Gets the {@link union.base.command.SlashCommand#category SlashCommand.category} for the Command.
	 *
	 * @return The category for the Command
	 */
	public Category getCategory() {
		return category;
	}

	/**
	 * Gets the subcommand data associated with this subcommand.
	 *
	 * @return subcommand data
	 */
	public SubcommandGroupData getSubcommandGroup() {
		return subcommandGroup;
	}

	/**
	 * Gets the options associated with this command.
	 *
	 * @return the OptionData array for options
	 */
	public List<OptionData> getOptions() {
		return options;
	}

	/**
	 * Builds CommandData for the SlashCommand upsert.
	 * This code is executed when we need to upsert the command.
	 * <p>
	 * Useful for manual upserting.
	 *
	 * @return the built command data
	 */
	public CommandData buildCommandData() {
		// Set attributes
		this.help = lu.getText(getHelpPath());
		this.descriptionLocalization = lu.getFullLocaleMap(getHelpPath(), getHelp());

		// Make the command data
		SlashCommandData data = Commands.slash(getName(), getHelp());

		// Add options and localizations
		if (!getOptions().isEmpty()) {
			getOptions().forEach(option -> {
				option.setNameLocalizations(lu.getFullLocaleMap("%s.%s.name".formatted(getPath(), option.getName()), option.getName()));
				option.setDescriptionLocalizations(lu.getFullLocaleMap("%s.%s.help".formatted(getPath(), option.getName()), option.getDescription()));
			});
			data.addOptions(getOptions());
		}

		// Check name localizations
		if (!getNameLocalization().isEmpty()) {
			//Add localizations
			data.setNameLocalizations(getNameLocalization());
		}
		// Check description localizations
		if (!getDescriptionLocalization().isEmpty()) {
			//Add localizations
			data.setDescriptionLocalizations(getDescriptionLocalization());
		}
		// Add if NSFW command
		if (nsfwOnly) {
			data.setNSFW(true);
		}
		// Add AccessLevel if ownerCommand
		if (ownerCommand) {
			this.accessLevel = CmdAccessLevel.DEV;
		}

		// Check for children
		if (children.length != 0) {
			// Temporary map for easy group storage
			Map<String, SubcommandGroupData> groupData = new HashMap<>();
			for (SlashCommand child : children) {
				// Inherit
				if (child.userPermissions.length == 0) {
					child.userPermissions = getUserPermissions();
				}
				if (child.botPermissions.length == 0) {
					child.botPermissions = getBotPermissions();
				}
				if (child.getAccessLevel().getLevel().equals(CmdAccessLevel.ALL.getLevel())) {
					child.accessLevel = getAccessLevel();
				}
				if (child.module == null) {
					child.module = getModule();
				}
				// Set attributes
				child.help = lu.getText(child.getHelpPath());
				child.descriptionLocalization = lu.getFullLocaleMap(child.getHelpPath(), child.getHelp());
				
				// Create subcommand data
				SubcommandData subcommandData = new SubcommandData(child.getName(), child.getHelp());
				
				// Add options and check localizations
				if (!child.getOptions().isEmpty()) {
					child.getOptions().forEach(option -> {
						option.setNameLocalizations(lu.getFullLocaleMap("%s.%s.name".formatted(child.getPath(), option.getName()), option.getName()));
						option.setDescriptionLocalizations(lu.getFullLocaleMap("%s.%s.help".formatted(child.getPath(), option.getName()), option.getDescription()));
					});
					subcommandData.addOptions(child.getOptions());
				}

				// Check child name localizations
				if (!child.getNameLocalization().isEmpty()) {
					//Add localizations
					subcommandData.setNameLocalizations(child.getNameLocalization());
				}
				// Check child description localizations
				if (!child.getDescriptionLocalization().isEmpty()) {
					//Add localizations
					subcommandData.setDescriptionLocalizations(child.getDescriptionLocalization());
				}

				// If there's a subcommand group
				if (child.getSubcommandGroup() != null) {
					SubcommandGroupData group = child.getSubcommandGroup();

					SubcommandGroupData newData = groupData.getOrDefault(group.getName(), group)
						.addSubcommands(subcommandData);

					groupData.put(group.getName(), newData);
				}
				// Just add to the command
				else {
					data.addSubcommands(subcommandData);
				}
			}
			if (!groupData.isEmpty())
				data.addSubcommandGroups(groupData.values());
		}

		if (getAccessLevel().isLowerThan(CmdAccessLevel.ADMIN))
			data.setDefaultPermissions(DefaultMemberPermissions.enabledFor(this.getUserPermissions()));
		else
			data.setDefaultPermissions(DefaultMemberPermissions.DISABLED);

		data.setContexts(this.guildOnly ? Set.of(InteractionContextType.GUILD) : Set.of(InteractionContextType.GUILD, InteractionContextType.BOT_DM));

		return data;
	}

	/**
	 * Gets the {@link SlashCommand#children Command.children} for the Command.
	 *
	 * @return The children for the Command
	 */
	public SlashCommand[] getChildren() {
		return children;
	}

	private void terminate(SlashCommandEvent event, @NotNull MessageEmbed embed, CommandClient client) {
		terminate(event, MessageCreateData.fromEmbeds(embed), client);
	}

	private void terminate(SlashCommandEvent event, MessageCreateData message, CommandClient client) {
		if (message != null)
			event.reply(message).setEphemeral(true).queue(null, failure -> new ErrorHandler().ignore(ErrorResponse.UNKNOWN_INTERACTION));
		if (client.getListener() != null)
			client.getListener().onTerminatedSlashCommand(event, this);
	}

	/**
	 * Gets the proper cooldown key for this Command under the provided
	 * {@link SlashCommandEvent SlashCommandEvent}.
	 *
	 * @param  event
	 *         The CommandEvent to generate the cooldown for.
	 *
	 * @return A String key to use when applying a cooldown.
	 */
	public String getCooldownKey(SlashCommandEvent event) {
		return switch (cooldownScope) {
			case USER -> cooldownScope.genKey(name, event.getUser().getIdLong());
			case USER_GUILD ->
				Optional.of(event.getGuild()).map(g -> cooldownScope.genKey(name, event.getUser().getIdLong(), g.getIdLong()))
					.orElse(CooldownScope.USER_CHANNEL.genKey(name, event.getUser().getIdLong(), event.getChannel().getIdLong()));
			case USER_CHANNEL ->
				cooldownScope.genKey(name, event.getUser().getIdLong(), event.getChannel().getIdLong());
			case GUILD -> Optional.of(event.getGuild()).map(g -> cooldownScope.genKey(name, g.getIdLong()))
				.orElse(CooldownScope.CHANNEL.genKey(name, event.getChannel().getIdLong()));
			case CHANNEL -> cooldownScope.genKey(name, event.getChannel().getIdLong());
			case SHARD -> cooldownScope.genKey(name, event.getJDA().getShardInfo().getShardId());
			case USER_SHARD -> cooldownScope.genKey(name, event.getUser().getIdLong(), event.getJDA().getShardInfo().getShardId());
			case GLOBAL -> cooldownScope.genKey(name, 0);
		};
	}

	/**
	 * Gets an error message for this Command under the provided
	 * {@link SlashCommandEvent SlashCommandEvent}.
	 *
	 * @param  event
	 *         The CommandEvent to generate the error message for.
	 * @param  remaining
	 *         The remaining number of seconds a command is on cooldown for.
	 *
	 * @return A String error message for this command if {@code remaining > 0},
	 *         else {@code null}.
	 */
	private MessageCreateData getCooldownErrorEmbed(SlashCommandEvent event, int remaining) {
		if (remaining <= 0)
			return null;
		
		StringBuilder front = new StringBuilder(lu.getText(event,"errors.cooldown.cooldown_command")
			.replace("{time}", Integer.toString(remaining))
		);
		if (cooldownScope.equals(CooldownScope.USER_GUILD) && event.getGuild()==null)
			front.append(" ").append(lu.getText(event, CooldownScope.USER_CHANNEL.getErrorPath()));
		else if (cooldownScope.equals(CooldownScope.GUILD) && event.getGuild()==null)
			front.append(" ").append(lu.getText(event, CooldownScope.CHANNEL.getErrorPath()));
		else if (!cooldownScope.equals(CooldownScope.USER))
			front.append(" ").append(lu.getText(event, cooldownScope.getErrorPath()));

		return MessageCreateData.fromContent(Objects.requireNonNull(front.append("!").toString()));
	}

	/**
	 * Gets the specified localizations of slash command names.
	 * @return Slash command name localizations.
	 */
	@NotNull
	public Map<DiscordLocale, String> getNameLocalization() {
		return nameLocalization;
	}

	/**
	 * Gets the specified localizations of slash command descriptions.
	 * @return Slash command description localizations.
	 */
	@NotNull
	public Map<DiscordLocale, String> getDescriptionLocalization() {
		return descriptionLocalization;
	}

	/**
	 * Checks if this Command can only be used in a {@link net.dv8tion.jda.api.entities.Guild Guild}.
	 *
	 * @return {@code true} if this Command can only be used in a Guild, else {@code false} if it can
	 *         be used outside of one
	 */
	public boolean isGuildOnly() {
		return guildOnly;
	}
}
