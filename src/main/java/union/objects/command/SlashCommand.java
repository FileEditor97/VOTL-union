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
package union.objects.command;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.*;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;

import union.objects.CmdAccessLevel;
import union.utils.exception.CheckException;

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
public abstract class SlashCommand extends Command
{
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
	@Nonnull
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
	@Nonnull
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
	 *
	 * <b>This only works in a child/subcommand.</b>
	 *
	 * To instantiate: <code>{@literal new SubcommandGroupData(name, description)}</code><br>
	 * It's important the instantiations are the same across children if you intend to keep them in the same group.
	 *
	 * Can be null, and it will not be assigned to a group.
	 */
	protected SubcommandGroupData subcommandGroup = null;

	/**
	 * An array list of OptionData.
	 *
	 * <b>This is incompatible with children. You cannot have a child AND options.</b>
	 *
	 * This is to specify different options for arguments and the stuff.
	 *
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
	public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {}

	/**
	 * The main body method of a {@link union.objects.command.Command Command}.
	 * <br>This is the "response" for a successful
	 * {@link union.objects.command.Command#run(CommandEvent) #run(CommandEvent)}.
	 * <b>
	 *     Because this is a SlashCommand, this is called, but does nothing.
	 *     You can still override this if you want to have a separate response for normal [prefix][name].
	 *     Keep in mind you must add it as a Command via {@link CommandClientBuilder#addCommand(Command)} for it to work properly.
	 * </b>
	 *
	 * @param  event
	 *         The {@link union.objects.command.CommandEvent CommandEvent} that
	 *         triggered this Command
	 */
	@Override
	protected void execute(CommandEvent event) {}

	/**
	 * Runs checks for the {@link SlashCommand SlashCommand} with the
	 * given {@link SlashCommandEvent SlashCommandEvent} that called it.
	 * <br>Will terminate, and possibly respond with a failure message, if any checks fail.
	 *
	 * @param  event
	 *         The SlashCommandEvent that triggered this Command
	 */
	public final void run(SlashCommandEvent event) {
		// client 
		final CommandClient client = event.getClient();

		// check owner command
		if (ownerCommand && (!isOwner(event, client))) {
			terminate(event, bot.getEmbedUtil().getError(event, "errors.command.not_owner"), client);
			return;
		}

		// cooldown check, ignoring owner
		if (cooldown > 0 && !(isOwner(event, client))) {
			String key = getCooldownKey(event);
			int remaining = client.getRemainingCooldown(key);
			if (remaining > 0) {
				terminate(event, getCooldownErrorEmbed(event, remaining, client), client);
				return;
			} else {
				client.applyCooldown(key, cooldown);
			}
		}

		// check db and permisisons
		if (event.isFromGuild() && !ownerCommand) {
			Guild guild = event.getGuild();
			Member author = event.getMember();
			try {
				// check setup
				if (!event.getFullCommandName().equals("setup main")) bot.getCheckUtil().guildExists(event, guild);
				
				bot.getCheckUtil()
				// check module enabled
					.moduleEnabled(event, guild, getModule())
				// check access
					.hasAccess(event, author, getAccessLevel())
				// check user perms
					.hasPermissions(event, guild, author, getUserPermissions())
				// check bots perms
					.hasPermissions(event, guild, author, true, getBotPermissions());
			} catch (CheckException ex) {
				terminate(event, ex.getCreateData(), client);
				return;
			}

			// nsfw check
			if (nsfwOnly && event.getChannelType() == ChannelType.TEXT && !event.getTextChannel().isNSFW()) {
				terminate(event, bot.getEmbedUtil().getError(event, "errors.command.nsfw"), client);
				return;
			}
		} else if (guildOnly) {
			terminate(event, bot.getEmbedUtil().getError(event, "errors.command.guild_only"), client);
			return;
		}

		// execute
		try {
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
	 * Tests whether or not the {@link net.dv8tion.jda.api.entities.User User} who triggered this
	 * event is an owner of the bot.
	 *
	 * @param event the event that triggered the command
	 * @param client the command client for checking stuff
	 * @return {@code true} if the User is the Owner, else {@code false}
	 */
	public boolean isOwner(SlashCommandEvent event, CommandClient client) {
		String userId = event.getUser().getId();
		if (client.getOwnerId().equals(userId))
			return true;
		return false;
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
	 *
	 * Useful for manual upserting.
	 *
	 * @return the built command data
	 */
	public CommandData buildCommandData() {
		// Set attributes
		this.help = lu.getText(getHelpPath());
		this.descriptionLocalization = lu.getFullLocaleMap(getHelpPath());

		// Make the command data
		SlashCommandData data = Commands.slash(getName(), getHelp());
		if (!getOptions().isEmpty()) {
			getOptions().forEach(option -> {
				option.setNameLocalizations(lu.getFullLocaleMap(String.format("%s.%s.name", getPath(), option.getName())));
				option.setDescriptionLocalizations(lu.getFullLocaleMap(String.format("%s.%s.help", getPath(), option.getName())));
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
				// Inherite
				if (child.userPermissions.length == 0) {
					child.userPermissions = getUserPermissions();
				}
				if (child.botPermissions.length == 0) {
					child.botPermissions = getBotPermissions();
				}
				if (child.getAccessLevel().getLevel() == CmdAccessLevel.ALL.getLevel()) {
					child.accessLevel = getAccessLevel();
				}
				if (child.module == null) {
					child.module = getModule();
				}
				child.help = lu.getText(child.getHelpPath());
				
				// Create subcommand data
				SubcommandData subcommandData = new SubcommandData(child.getName(), child.getHelp());
				// Add options
				if (!child.getOptions().isEmpty()) {
					subcommandData.addOptions(child.getOptions());
				}

				//Check child name localizations
				if (!child.getNameLocalization().isEmpty()) {
					//Add localizations
					subcommandData.setNameLocalizations(child.getNameLocalization());
				}
				//Check child description localizations
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

		if (this.isOwnerCommand() || this.getAccessLevel().getLevel() >= 2)
			data.setDefaultPermissions(DefaultMemberPermissions.DISABLED);
		else
			data.setDefaultPermissions(DefaultMemberPermissions.enabledFor(this.getUserPermissions()));

		data.setGuildOnly(this.guildOnly);

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

	private void terminate(SlashCommandEvent event, @Nonnull MessageEmbed embed, CommandClient client) {
		terminate(event, MessageCreateData.fromEmbeds(embed), client);
	}

	private void terminate(SlashCommandEvent event, MessageCreateData message, CommandClient client) {
		if (message != null)
			event.reply(message).setEphemeral(true).queue();
		if (event.getClient().getListener() != null)
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
		switch (cooldownScope) {
			case USER:         return cooldownScope.genKey(name,event.getUser().getIdLong());
			case USER_GUILD:   return Optional.of(event.getGuild()).map(g -> cooldownScope.genKey(name,event.getUser().getIdLong(),g.getIdLong()))
				.orElse(CooldownScope.USER_CHANNEL.genKey(name,event.getUser().getIdLong(), event.getChannel().getIdLong()));
			case USER_CHANNEL: return cooldownScope.genKey(name,event.getUser().getIdLong(),event.getChannel().getIdLong());
			case GUILD:        return Optional.of(event.getGuild()).map(g -> cooldownScope.genKey(name,g.getIdLong()))
				.orElse(CooldownScope.CHANNEL.genKey(name,event.getChannel().getIdLong()));
			case CHANNEL:      return cooldownScope.genKey(name,event.getChannel().getIdLong());
			case SHARD:
				event.getJDA().getShardInfo();
				return cooldownScope.genKey(name, event.getJDA().getShardInfo().getShardId());
			case USER_SHARD:
				event.getJDA().getShardInfo();
				return cooldownScope.genKey(name,event.getUser().getIdLong(),event.getJDA().getShardInfo().getShardId());
			case GLOBAL:       return cooldownScope.genKey(name, 0);
			default:           return "";
		}
	}

	/**
	 * Gets an error message for this Command under the provided
	 * {@link SlashCommandEvent SlashCommandEvent}.
	 *
	 * @param  event
	 *         The CommandEvent to generate the error message for.
	 * @param  remaining
	 *         The remaining number of seconds a command is on cooldown for.
	 * @param client
	 *         The CommandClient for checking stuff
	 *
	 * @return A String error message for this command if {@code remaining > 0},
	 *         else {@code null}.
	 */
	private MessageCreateData getCooldownErrorEmbed(SlashCommandEvent event, int remaining, CommandClient client) {
		if (remaining <= 0)
			return null;
		
		StringBuilder front = new StringBuilder(lu.getText(event,
			"errors.cooldown.cooldown_left").replace("{time}", Integer.toString(remaining)
		));
		if (cooldownScope.equals(CooldownScope.USER))
			{}
		else if (cooldownScope.equals(CooldownScope.USER_GUILD) && event.getGuild()==null)
			front.append(" " + lu.getText(event, CooldownScope.USER_CHANNEL.errorPath));
		else if (cooldownScope.equals(CooldownScope.GUILD) && event.getGuild()==null)
			front.append(" " + lu.getText(event, CooldownScope.CHANNEL.errorPath));
		else
			front.append(" " + lu.getText(event, cooldownScope.errorPath));

		return MessageCreateData.fromContent(Objects.requireNonNull(front.append("!").toString()));
	}

	/**
	 * Gets the specified localizations of slash command names.
	 * @return Slash command name localizations.
	 */
	@Nonnull
	public Map<DiscordLocale, String> getNameLocalization() {
		return nameLocalization;
	}

	/**
	 * Gets the specified localizations of slash command descriptions.
	 * @return Slash command description localizations.
	 */
	@Nonnull
	public Map<DiscordLocale, String> getDescriptionLocalization() {
		return descriptionLocalization;
	}
}
