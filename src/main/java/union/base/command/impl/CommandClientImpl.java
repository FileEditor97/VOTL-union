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
package union.base.command.impl;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.annotation.Nonnull;
import union.base.command.Category;
import union.base.command.Command;
import union.base.command.CommandClient;
import union.base.command.CommandEvent;
import union.base.command.CommandListener;
import union.base.command.ContextMenu;
import union.base.command.MessageContextMenu;
import union.base.command.MessageContextMenuEvent;
import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.base.command.UserContextMenu;
import union.base.command.UserContextMenuEvent;
import union.base.utils.FixedSizeCache;
import union.base.utils.SafeIdUtil;
import union.objects.constants.Constants;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.internal.utils.Checks;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of {@link union.base.command.CommandClient CommandClient} to be used by a bot.
 *
 * <p>This is a listener usable with {@link net.dv8tion.jda.api.JDA JDA}, as it implements
 * {@link net.dv8tion.jda.api.hooks.EventListener EventListener} in order to catch and use different kinds of
 * {@link net.dv8tion.jda.api.events.Event Event}s. The primary usage of this is where the CommandClient implementation
 * takes {@link net.dv8tion.jda.api.events.message.MessageReceivedEvent MessageReceivedEvent}s, and automatically
 * processes arguments, and provide them to a {@link union.base.command.Command Command} for
 * running and execution.
 *
 * @author John Grosh (jagrosh)
 */
public class CommandClientImpl implements CommandClient, EventListener
{
	private static final Logger LOG = LoggerFactory.getLogger(CommandClient.class);
	private static final String DEFAULT_PREFIX = "@mention";

	private final OffsetDateTime start;
	private final Activity activity;
	private final OnlineStatus status;
	private final String ownerId;
	private final String prefix;
	private final Function<MessageReceivedEvent, String> prefixFunction;
	private final Function<MessageReceivedEvent, Boolean> commandPreProcessFunction;
	private final BiFunction<MessageReceivedEvent, Command, Boolean> commandPreProcessBiFunction;
	private final String serverInvite;
	private final HashMap<String, Integer> commandIndex;
	private final HashMap<String, Integer> slashCommandIndex;
	private final ArrayList<Command> commands;
	private final ArrayList<SlashCommand> slashCommands;
	private final ArrayList<ContextMenu> contextMenus;
	private final HashMap<String, Integer> contextMenuIndex;
	private final String forcedGuildId;
	private final String[] devGuildIds;
	private final boolean manualUpsert;
	private final HashMap<String,OffsetDateTime> cooldowns;
	private final HashMap<String,Integer> uses;
	private final FixedSizeCache<Long, Set<Message>> linkMap;
	private final boolean useHelp;
	private final boolean shutdownAutomatically;
	private final Consumer<CommandEvent> helpConsumer;
	private final String helpWord;
	private final ScheduledExecutorService executor;

	private String textPrefix;
	private CommandListener listener = null;
	private int totalGuilds;

	public CommandClientImpl(String ownerId, String prefix, String altprefix, String[] prefixes, Function<MessageReceivedEvent, String> prefixFunction, Function<MessageReceivedEvent, Boolean> commandPreProcessFunction, BiFunction<MessageReceivedEvent, Command, Boolean> commandPreProcessBiFunction, Activity activity, OnlineStatus status, String serverInvite,
							 ArrayList<Command> commands, ArrayList<SlashCommand> slashCommands, ArrayList<ContextMenu> contextMenus, String forcedGuildId, String[] devGuildIds, boolean manualUpsert,
							 boolean useHelp, boolean shutdownAutomatically, Consumer<CommandEvent> helpConsumer, String helpWord, ScheduledExecutorService executor,
							 int linkedCacheSize)
	{
		Checks.check(ownerId != null, "Owner ID was set null or not set! Please provide an User ID to register as the owner!");

		if(!SafeIdUtil.checkId(ownerId))
			LOG.warn(String.format("The provided Owner ID (%s) was found unsafe! Make sure ID is a non-negative long!", ownerId));

		this.start = OffsetDateTime.now();

		this.ownerId = ownerId;
		this.prefix = prefix==null || prefix.isEmpty() ? DEFAULT_PREFIX : prefix;

		this.prefixFunction = prefixFunction;
		this.commandPreProcessFunction = commandPreProcessFunction;
		this.commandPreProcessBiFunction = commandPreProcessBiFunction;
		this.textPrefix = prefix;
		this.activity = activity;
		this.status = status;
		this.serverInvite = serverInvite;
		this.commandIndex = new HashMap<>();
		this.slashCommandIndex = new HashMap<>();
		this.commands = new ArrayList<>();
		this.slashCommands = new ArrayList<>();
		this.contextMenus = new ArrayList<>();
		this.contextMenuIndex = new HashMap<>();
		this.forcedGuildId = forcedGuildId;
		this.devGuildIds = devGuildIds==null || devGuildIds.length==0 ? null : devGuildIds;
		this.manualUpsert = manualUpsert;
		this.cooldowns = new HashMap<>();
		this.uses = new HashMap<>();
		this.linkMap = linkedCacheSize>0 ? new FixedSizeCache<>(linkedCacheSize) : null;
		this.useHelp = useHelp;
		this.shutdownAutomatically = shutdownAutomatically;
		this.helpWord = helpWord==null ? "help" : helpWord;
		this.executor = executor==null ? Executors.newSingleThreadScheduledExecutor() : executor;
		this.helpConsumer = helpConsumer==null ? (event) -> {
				StringBuilder builder = new StringBuilder("**"+event.getSelfUser().getName()+"** commands:\n");
				Category category = null;
				for(Command command : commands)
				{
					if(!command.isHidden() && (!command.isOwnerCommand() || event.isOwner()))
					{
						if(!Objects.equals(category, command.getCategory()))
						{
							category = command.getCategory();
							builder.append("\n\n  __").append(category==null ? "No Category" : category.getName()).append("__:\n");
						}
						builder.append("\n`").append(textPrefix).append(prefix==null?" ":"").append(command.getName())
							   .append(command.getArguments()==null ? "`" : " "+command.getArguments()+"`")
							   .append(" - ").append(command.getHelp());
					}
				}
				if (ownerId != null) {
					Optional.ofNullable(event.getJDA().getUserById(ownerId)).map(owner -> 
						builder.append("\n\nFor additional help, contact **").append(owner.getName())
							.append(serverInvite != null ? " or join "+serverInvite : "")
					);
				}
				event.replyInDm(builder.toString(), unused ->
				{
					if(event.isFromType(ChannelType.TEXT))
						event.reply(Constants.SUCCESS);
				}, t -> event.reply(Constants.FAILURE+"Help cannot be sent because you are blocking Direct Messages."));
		} : helpConsumer;

		// Load commands
		for(Command command : commands)
		{
			addCommand(command);
		}

		// Load slash commands
		for(SlashCommand command : slashCommands)
		{
			addSlashCommand(command);
		}

		// Load context menus
		for(ContextMenu menu : contextMenus)
		{
			addContextMenu(menu);
		}
	}

	@Override
	public void setListener(CommandListener listener)
	{
		this.listener = listener;
	}

	@Override
	public CommandListener getListener()
	{
		return listener;
	}

	@Override
	public List<Command> getCommands()
	{
		return commands;
	}

	@Override
	public List<SlashCommand> getSlashCommands()
	{
		return slashCommands;
	}

	@Override
	public List<ContextMenu> getContextMenus()
	{
		return contextMenus;
	}

	@Override
	public boolean isManualUpsert()
	{
		return manualUpsert;
	}

	@Override
	public String forcedGuildId()
	{
		return forcedGuildId;
	}

	@Override
	public String[] devGuildIds()
	{
		return devGuildIds;
	}

	@Override
	public OffsetDateTime getStartTime()
	{
		return start;
	}

	@Override
	public OffsetDateTime getCooldown(String name)
	{
		return cooldowns.get(name);
	}

	@Override
	public int getRemainingCooldown(String name)
	{
		if(cooldowns.containsKey(name))
		{
			int time = (int) Math.ceil(OffsetDateTime.now().until(cooldowns.get(name), ChronoUnit.MILLIS) / 1000D);
			if(time<=0)
			{
				cooldowns.remove(name);
				return 0;
			}
			return time;
		}
		return 0;
	}

	@Override
	public void applyCooldown(String name, int seconds)
	{
		cooldowns.put(name, OffsetDateTime.now().plusSeconds(seconds));
	}

	@Override
	public void cleanCooldowns()
	{
		OffsetDateTime now = OffsetDateTime.now();
		cooldowns.keySet().stream().filter((str) -> (cooldowns.get(str).isBefore(now)))
				.collect(Collectors.toList()).forEach(cooldowns::remove);
	}

	@Override
	public int getCommandUses(Command command)
	{
		return getCommandUses(command.getName());
	}

	@Override
	public int getCommandUses(String name)
	{
		return uses.getOrDefault(name, 0);
	}

	@Override
	public void addCommand(Command command)
	{
		addCommand(command, commands.size());
	}

	@Override
	public void addCommand(Command command, int index)
	{
		if(index>commands.size() || index<0)
			throw new ArrayIndexOutOfBoundsException("Index specified is invalid: ["+index+"/"+commands.size()+"]");
		synchronized(commandIndex)
		{
			String name = command.getName().toLowerCase(Locale.ROOT);
			//check for collision
			if(commandIndex.containsKey(name))
				throw new IllegalArgumentException("Command added has a name or alias that has already been indexed: \""+name+"\"!");
			for(String alias : command.getAliases())
			{
				if(commandIndex.containsKey(alias.toLowerCase(Locale.ROOT)))
					throw new IllegalArgumentException("Command added has a name or alias that has already been indexed: \""+alias+"\"!");
			}
			//shift if not append
			if(index<commands.size())
			{
				commandIndex.entrySet().stream().filter(entry -> entry.getValue()>=index).collect(Collectors.toList())
					.forEach(entry -> commandIndex.put(entry.getKey(), entry.getValue()+1));
			}
			//add
			commandIndex.put(name, index);
			for(String alias : command.getAliases())
				commandIndex.put(alias.toLowerCase(Locale.ROOT), index);
		}
		commands.add(index,command);
	}

	@Override
	public void addSlashCommand(SlashCommand command)
	{
		addSlashCommand(command, slashCommands.size());
	}

	@Override
	public void addSlashCommand(SlashCommand command, int index)
	{
		if(index>slashCommands.size() || index<0)
			throw new ArrayIndexOutOfBoundsException("Index specified is invalid: ["+index+"/"+slashCommands.size()+"]");
		synchronized(slashCommandIndex)
		{
			String name = command.getName().toLowerCase(Locale.ROOT);
			//check for collision
			if(slashCommandIndex.containsKey(name))
				throw new IllegalArgumentException("Command added has a name that has already been indexed: \""+name+"\"!");
			//shift if not append
			if(index<slashCommands.size())
			{
				slashCommandIndex.entrySet().stream().filter(entry -> entry.getValue()>=index).collect(Collectors.toList())
					.forEach(entry -> slashCommandIndex.put(entry.getKey(), entry.getValue()+1));
			}
			//add
			slashCommandIndex.put(name, index);
		}
		slashCommands.add(index,command);
	}

	@Override
	public void addContextMenu(ContextMenu menu)
	{
		addContextMenu(menu, contextMenus.size());
	}

	@Override
	public void addContextMenu(ContextMenu menu, int index)
	{
		if(index>contextMenus.size() || index<0)
			throw new ArrayIndexOutOfBoundsException("Index specified is invalid: ["+index+"/"+contextMenus.size()+"]");
		synchronized(contextMenuIndex)
		{
			String name = menu.getName();
			//check for collision
			if(contextMenuIndex.containsKey(name)) {
				// Compare the existing menu's class to the new menu's class
				if (contextMenuIndex.get(name).getClass().getName().equals(menu.getClass().getName())) {
					throw new IllegalArgumentException("Context Menu added has a name and class that has already been indexed: \"" + name + "\"!");
				}
			}
			//shift if not append
			if(index<contextMenuIndex.size())
			{
				contextMenuIndex.entrySet().stream().filter(entry -> entry.getValue()>=index).collect(Collectors.toList())
					.forEach(entry -> contextMenuIndex.put(entry.getKey(), entry.getValue()+1));
			}
			//add
			contextMenuIndex.put(name, index);
		}
		contextMenus.add(index,menu);
	}

	@Override
	public void removeCommand(String name)
	{
		synchronized(commandIndex)
		{
			if(!commandIndex.containsKey(name.toLowerCase(Locale.ROOT)))
				throw new IllegalArgumentException("Name provided is not indexed: \"" + name + "\"!");
			int targetIndex = commandIndex.remove(name.toLowerCase(Locale.ROOT));
			Command removedCommand = commands.remove(targetIndex);
			for(String alias : removedCommand.getAliases())
			{
				commandIndex.remove(alias.toLowerCase(Locale.ROOT));
			}
			commandIndex.entrySet().stream().filter(entry -> entry.getValue()>targetIndex).collect(Collectors.toList())
				.forEach(entry -> commandIndex.put(entry.getKey(), entry.getValue()-1));
		}
	}

	@Override
	public String getOwnerId()
	{
		return ownerId;
	}

	@Override
	public long getOwnerIdLong()
	{
		return Long.parseLong(ownerId);
	}

	@Override
	public ScheduledExecutorService getScheduleExecutor()
	{
		return executor;
	}

	@Override
	public String getServerInvite()
	{
		return serverInvite;
	}

	@Override
	public String getPrefix()
	{
		return prefix;
	}

	@Override
	public Function<MessageReceivedEvent, String> getPrefixFunction()
	{
		return prefixFunction;
	}

	@Override
	public String getTextualPrefix()
	{
		return textPrefix;
	}

	@Override
	public int getTotalGuilds()
	{
		return totalGuilds;
	}

	@Override
	public String getHelpWord()
	{
		return helpWord;
	}

	@Override
	public boolean usesLinkedDeletion() {
		return linkMap != null;
	}

	@Override
	public void shutdown()
	{
		executor.shutdown();
	}

	@Override
	public void onEvent(@Nonnull GenericEvent event)
	{
		if(event instanceof MessageReceivedEvent)
			onMessageReceived((MessageReceivedEvent)event);

		else if(event instanceof SlashCommandInteractionEvent)
			onSlashCommand((SlashCommandInteractionEvent)event);

		else if(event instanceof MessageContextInteractionEvent)
			onMessageContextMenu((MessageContextInteractionEvent)event);
		else if(event instanceof UserContextInteractionEvent)
			onUserContextMenu((UserContextInteractionEvent)event);

		else if (event instanceof CommandAutoCompleteInteractionEvent)
			onCommandAutoComplete((CommandAutoCompleteInteractionEvent)event);

		else if(event instanceof MessageDeleteEvent && usesLinkedDeletion())
			onMessageDelete((MessageDeleteEvent) event);

		else if(event instanceof ReadyEvent)
			onReady((ReadyEvent)event);
		else if(event instanceof ShutdownEvent)
		{
			if(shutdownAutomatically)
				shutdown();
		}
	}

	private void onReady(ReadyEvent event)
	{
		if(!event.getJDA().getSelfUser().isBot())
		{
			LOG.error("JDA-Utilities does not support CLIENT accounts.");
			event.getJDA().shutdown();
			return;
		}
		textPrefix = prefix.equals(DEFAULT_PREFIX) ? "@"+event.getJDA().getSelfUser().getName()+" " : prefix;

		if(activity != null)
			event.getJDA().getPresence().setPresence(status==null ? OnlineStatus.ONLINE : status,
				"default".equals(activity.getName()) ? Activity.playing("Type "+textPrefix+helpWord) : activity);

		// Upsert slash commands, if not manual
		if (!manualUpsert)
		{
			upsertInteractions(event.getJDA());
		}
	}

	@Override
	public void upsertInteractions(JDA jda)
	{
		if (devGuildIds == null) {
			upsertInteractions(jda, forcedGuildId);
		} else {
			upsertInteractions(jda, devGuildIds);
		}
		
	}

	@Override
	public void upsertInteractions(JDA jda, String serverId)
	{
		// Get all commands
		List<CommandData> data = new ArrayList<>();
		List<SlashCommand> slashCommands = getSlashCommands();
		//Map<String, SlashCommand> slashCommandMap = new HashMap<>();
		List<ContextMenu> contextMenus = getContextMenus();
		//Map<String, ContextMenu> contextMenuMap = new HashMap<>();

		// Build the command and privilege data
		for (SlashCommand command : slashCommands)
		{
			data.add(command.buildCommandData());
			//slashCommandMap.put(command.getName(), command);
		}

		for (ContextMenu menu : contextMenus) {
			data.add(menu.buildCommandData());
			//contextMenuMap.put(menu.getName(), menu);
		}

		// Upsert the commands
		if (serverId != null)
		{
			// Attempt to retrieve the provided guild
			Guild server = jda.getGuildById(serverId);
			if (server == null)
			{
				LOG.error("Specified forced guild is null! Slash Commands will NOT be added! Is the bot added?");
				return;
			}
			// Upsert the commands + their privileges
			server.updateCommands().addCommands(data)
				.queue(
					priv -> LOG.debug("Successfully added " + slashCommands.size() + " slash commands and " + contextMenus.size() + " menus to server " + server.getName()),
					error -> LOG.error("Could not upsert commands! Does the bot have the applications.commands scope?" + error)
				);
		}
		else
			jda.updateCommands().addCommands(data)
				.queue(commands -> LOG.debug("Successfully added " + commands.size() + " slash commands!"));
	}

	@Override
	public void upsertInteractions(JDA jda, String[] serverIds) {
		// Get all commands
		List<CommandData> data = new ArrayList<>();
		List<CommandData> dataDev = new ArrayList<>();
		List<SlashCommand> slashCommands = getSlashCommands();
		//Map<String, SlashCommand> slashCommandMap = new HashMap<>();
		List<ContextMenu> contextMenus = getContextMenus();
		//Map<String, ContextMenu> contextMenuMap = new HashMap<>();

		// Build the command and privilege data
		for (SlashCommand command : slashCommands)
		{
			if (!command.isOwnerCommand()) {
				data.add(command.buildCommandData());
			} else {
				dataDev.add(command.buildCommandData());
			}
			//slashCommandMap.put(command.getName(), command);
		}
		for (ContextMenu menu : contextMenus) {
			data.add(menu.buildCommandData());
			//contextMenuMap.put(menu.getName(), menu);
		}

		jda.updateCommands().addCommands(data)
			.queue(commands -> LOG.debug("Successfully added " + commands.size() + " slash commands globally!"));

		// Upsert the commands
		for (String serverId : serverIds) {
			// Attempt to retrieve the provided guild
			if (serverId == null) {
				LOG.error("One of the specified developer guild id is null! Check provided values.");
				return;
			}
			Guild server = jda.getGuildById(serverId);
			if (server == null)
			{
				LOG.error("Specified forced guild is null! Slash Commands will NOT be added! Is the bot added?");
				return;
			}
			// Upsert the commands + their privileges
			server.updateCommands().addCommands(dataDev)
				.queue(
					priv -> LOG.debug("Successfully added " + dataDev.size() + " slash commands to server " + server.getName()),
					error -> LOG.error("Could not upsert commands! Does the bot have the applications.commands scope?" + error)
				);
		}
	}

	private void onMessageReceived(MessageReceivedEvent event)
	{
		// Return if it's a bot
		if(event.getAuthor().isBot())
			return;

		final MessageParts parts = getParts(event);

		if(parts!=null) //starts with valid prefix
		{
			if(useHelp && parts.command.equalsIgnoreCase(helpWord))
			{
				CommandEvent cevent = new CommandEvent(event, parts.prefixUsed, parts.args, this);
				if(listener!=null)
					listener.onCommand(cevent, null);
				helpConsumer.accept(cevent); // Fire help consumer
				if(listener!=null)
					listener.onCompletedCommand(cevent, null);
				return; // Help Consumer is done
			}
			else if(event.isFromType(ChannelType.PRIVATE) || event.getChannel().canTalk())
			{
				String name = parts.command;
				String args = parts.args;
				final Command command; // this will be null if it's not a command
				synchronized(commandIndex)
				{
					int i = commandIndex.getOrDefault(name.toLowerCase(Locale.ROOT), -1);
					command = i != -1? commands.get(i) : null;
				}

				if(command != null)
				{
					CommandEvent cevent = new CommandEvent(event, parts.prefixUsed, args, this);

					if(listener != null)
						listener.onCommand(cevent, command);
					uses.put(command.getName(), uses.getOrDefault(command.getName(), 0) + 1);
					if (commandPreProcessFunction != null || commandPreProcessBiFunction != null)
					{
						// Apply both pre-process functions
						if (commandPreProcessFunction != null && commandPreProcessFunction.apply(event))
						{
							command.run(cevent);
							return;
						}

						if (commandPreProcessBiFunction != null && commandPreProcessBiFunction.apply(event, command))
						{
							command.run(cevent);
							return;
						}

						// If we are here, neither function returned true, so we can just return
						return;
					}
					else
					{
						command.run(cevent);
					}
					return; // Command is done
				}
			}
		}

		if(listener != null)
			listener.onNonCommandMessage(event);
	}

	@Nullable
	private MessageParts getParts(MessageReceivedEvent event) {
		String rawContent = event.getMessage().getContentRaw();

		// Check for prefix or alternate prefix (@mention cases)
		if(prefix.equals(DEFAULT_PREFIX)) {
			if(rawContent.startsWith("<@"+ event.getJDA().getSelfUser().getId()+">") ||
					rawContent.startsWith("<@!"+ event.getJDA().getSelfUser().getId()+">")) {
				// Since we now use substring into makeMessageParts function and a indexOf here, we need to do a +1 to get the good substring
				// On top of that we need to do another +1 because the default @mention prefix will always be followed by a space
				// So we need to add 2 characters to get the correct substring
				final int prefixLength = rawContent.indexOf('>') + 2;
				return makeMessageParts(rawContent, prefixLength);
			}
		}

		// Check for prefix
		// Run Function check if there is one, then fallback to normal prefixes
		if (prefixFunction != null) {
			String prefix = prefixFunction.apply(event);
			// Don't lowercase, up to Function to handle this
			if (prefix != null && rawContent.startsWith(prefix)) {
				final int prefixLength = prefix.length();
				return makeMessageParts(rawContent, prefixLength);
			}
		}

		final String lowerCaseContent = rawContent.toLowerCase(Locale.ROOT);
		// Check for default prefix
		if (lowerCaseContent.startsWith(prefix.toLowerCase(Locale.ROOT))) {
			final int prefixLength = prefix.length();
			return makeMessageParts(rawContent, prefixLength);
		}

		return null;
	}

	/**
	 * Processes the message raw content and returns the "parts" of the message
	 * <br>These parts include:
	 * <ul>
	 *     <li>Used prefix</li>
	 *     <li>Command name</li>
	 *     <li>Arguments</li>
	 * </ul>
	 *
	 * @param rawContent
	 *        The raw content of the incoming message
	 * @param prefixLength
	 *        The length of the prefix that has been successfully detected before calling this method
	 * @return A MessageParts objects with all the parts cited above
	 */
	@NotNull
	private CommandClientImpl.MessageParts makeMessageParts(String rawContent, int prefixLength) {
		//Replacement method below
//        final String[] split = rawContent.substring(prefixLength).trim().split("\\s+", 2);

		//What we do is search for the first whitespace after the prefix, this gets us the command name
		// To then get the arguments, we find the first occurrence of a character other than a whitespace, after the command index, and take the string from that index
		String cmd = null;
		for (int i = prefixLength; i < rawContent.length(); i++) {
			if (Character.isWhitespace(rawContent.charAt(i))) { //If a whitespace assume we found the end of the command name
				cmd = rawContent.substring(prefixLength, i);
				break;
			}
		}

		String args = "";
		if (cmd == null) { //Assume there are no args since there were absolutely no whitespace
			cmd = rawContent.substring(prefixLength);
		} else {
			for (int i = prefixLength + cmd.length(); i < rawContent.length(); i++) {
				if (!Character.isWhitespace(rawContent.charAt(i))) { //If not a whitespace assume we found the start of the arguments
					args = rawContent.substring(i);
					break;
				}
			}
		}

		//Just in case something fucked up
		LOG.trace("Received command named '{}' with args '{}'", cmd, args);

		return new MessageParts(
			rawContent.substring(0, prefixLength),
			cmd,
			args
		);
	}

	private void onSlashCommand(SlashCommandInteractionEvent event)
	{
		// this will be null if it's not a command
		final SlashCommand command = findSlashCommand(event.getFullCommandName());

		// Wrap the event in a SlashCommandEvent
		final SlashCommandEvent commandEvent = new SlashCommandEvent(event, this);

		if(command != null)
		{
			if(listener != null)
				listener.onSlashCommand(commandEvent, command);
			uses.put(command.getName(), uses.getOrDefault(command.getName(), 0) + 1);
			command.run(commandEvent);
			// Command is done
		}
	}

	private void onCommandAutoComplete(CommandAutoCompleteInteractionEvent event)
	{
		// this will be null if it's not a command
		final SlashCommand command = findSlashCommand(event.getFullCommandName());

		if(command != null)
		{
			command.onAutoComplete(event);
		}
	}

	private SlashCommand findSlashCommand(String path)
	{
		String[] parts = path.split(" ");

		final SlashCommand command; // this will be null if it's not a command
		synchronized(slashCommandIndex)
		{
			int i = slashCommandIndex.getOrDefault(parts[0].toLowerCase(Locale.ROOT), -1);
			command = i != -1? slashCommands.get(i) : null;
		}

		if (command == null)
			return null;

		switch (parts.length) {
			case 1: // Slash command with no children
				return command;
			case 2: // Slash command with children
				// child check
				for(SlashCommand cmd: command.getChildren())
					if(cmd.isCommandFor(parts[1]))
						return cmd;

				return null;
			case 3: // Slash command with a group and a child
				for(SlashCommand cmd: command.getChildren())
					if(cmd.isCommandFor(parts[2]) && cmd.getSubcommandGroup().getName().equals(parts[1]))
						return cmd;

				return null;
		}

		// How did we get here?
		return null;
	}

	private void onUserContextMenu(UserContextInteractionEvent event)
	{
		final UserContextMenu menu; // this will be null if it's not a command
		synchronized(contextMenuIndex)
		{
			ContextMenu c;
			int i = contextMenuIndex.getOrDefault(event.getName(), -1);
			c = i != -1 ? contextMenus.get(i) : null;

			if (c instanceof UserContextMenu)
				menu = (UserContextMenu) c;
			else
				menu = null;
		}

		final UserContextMenuEvent menuEvent = new UserContextMenuEvent(event.getJDA(), event.getResponseNumber(), event,this);

		if(menu != null)
		{
			if(listener != null)
				listener.onUserContextMenu(menuEvent, menu);
			uses.put(menu.getName(), uses.getOrDefault(menu.getName(), 0) + 1);
			menu.run(menuEvent);
			// Command is done
		}
	}

	private void onMessageContextMenu(MessageContextInteractionEvent event)
	{
		final MessageContextMenu menu; // this will be null if it's not a command
		synchronized(contextMenuIndex)
		{
			ContextMenu c;
			// Do not lowercase, as there could be 2 menus with the same name, but different letter cases
			int i = contextMenuIndex.getOrDefault(event.getName(), -1);
			c = i != -1 ? contextMenus.get(i) : null;

			if (c instanceof MessageContextMenu)
				menu = (MessageContextMenu) c;
			else
				menu = null;
		}

		final MessageContextMenuEvent menuEvent = new MessageContextMenuEvent(event.getJDA(), event.getResponseNumber(), event,this);

		if(menu != null)
		{
			if(listener != null)
				listener.onMessageContextMenu(menuEvent, menu);
			uses.put(menu.getName(), uses.getOrDefault(menu.getName(), 0) + 1);
			menu.run(menuEvent);
			// Command is done
		}
	}

	private void onMessageDelete(MessageDeleteEvent event)
	{
		// Check we are in a guild since there is no guild specific event now
		if (!event.isFromGuild()) return;

		// We don't need to cover whether or not this client usesLinkedDeletion() because
		// that is checked in onEvent(Event) before this is even called.
		synchronized(linkMap)
		{
			if(linkMap.contains(event.getMessageIdLong()))
			{
				Set<Message> messages = linkMap.get(event.getMessageIdLong());
				if(messages.size()>1 && event.getGuild().getSelfMember()
						.hasPermission(event.getChannel().asTextChannel(), Permission.MESSAGE_MANAGE))
					event.getChannel().asTextChannel().deleteMessages(messages).queue(unused -> {}, ignored -> {});
				else if(messages.size()>0)
					messages.forEach(m -> m.delete().queue(unused -> {}, ignored -> {}));
			}
		}
	}

	/**
	 * <b>DO NOT USE THIS!</b>
	 *
	 * <p>This is a method necessary for linking a bot's response messages
	 * to their corresponding call message ID.
	 * <br><b>Using this anywhere in your code can and will break your bot.</b>
	 *
	 * @param  callId
	 *         The ID of the call Message
	 * @param  message
	 *         The Message to link to the ID
	 */
	public void linkIds(long callId, Message message)
	{
		// We don't use linked deletion, so we don't do anything.
		if(!usesLinkedDeletion())
			return;

		synchronized(linkMap)
		{
			Set<Message> stored = linkMap.get(callId);
			if(stored != null)
				stored.add(message);
			else
			{
				stored = new HashSet<>();
				stored.add(message);
				linkMap.add(callId, stored);
			}
		}
	}

	private static class MessageParts {
		private final String prefixUsed;
		private final String command;
		private final String args;

		private MessageParts(String prefixUsed, String command, String args) {
			this.prefixUsed = prefixUsed;
			this.command = command;
			this.args = args;
		}
	}
}
