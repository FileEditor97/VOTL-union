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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import union.base.command.CommandClient;
import union.base.command.CommandListener;
import union.base.command.ContextMenu;
import union.base.command.MessageContextMenu;
import union.base.command.MessageContextMenuEvent;
import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.base.command.UserContextMenu;
import union.base.command.UserContextMenuEvent;
import union.base.utils.SafeIdUtil;
import union.objects.annotation.NotNull;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.internal.utils.Checks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of {@link union.base.command.CommandClient CommandClient} to be used by a bot.
 *
 * <p>This is a listener usable with {@link net.dv8tion.jda.api.JDA JDA}, as it implements
 * {@link net.dv8tion.jda.api.hooks.EventListener EventListener} in order to catch and use different kinds of
 * {@link net.dv8tion.jda.api.events.Event Event}s.
 *
 * @author John Grosh (jagrosh)
 */
public class CommandClientImpl implements CommandClient, EventListener {
	private static final Logger LOG = LoggerFactory.getLogger(CommandClient.class);

	private final OffsetDateTime start;
	private final Activity activity;
	private final OnlineStatus status;
	private final String ownerId;
	private final String serverInvite;
	private final HashMap<String, Integer> slashCommandIndex;
	private final ArrayList<SlashCommand> slashCommands;
	private final ArrayList<ContextMenu> contextMenus;
	private final HashMap<String, Integer> contextMenuIndex;
	private final String forcedGuildId;
	private final String[] devGuildIds;
	private final boolean manualUpsert;
	private final HashMap<String,OffsetDateTime> cooldowns;
	private final HashMap<String,Integer> uses;
	private final boolean shutdownAutomatically;
	private final ScheduledExecutorService executor;

	private CommandListener listener = null;

	public CommandClientImpl(String ownerId, Activity activity, OnlineStatus status, String serverInvite,
							 ArrayList<SlashCommand> slashCommands, ArrayList<ContextMenu> contextMenus, String forcedGuildId, String[] devGuildIds, boolean manualUpsert,
							 boolean shutdownAutomatically, ScheduledExecutorService executor)
	{
		Checks.check(ownerId != null, "Owner ID was set null or not set! Please provide an User ID to register as the owner!");

		if (!SafeIdUtil.checkId(ownerId))
			LOG.warn(String.format("The provided Owner ID (%s) was found unsafe! Make sure ID is a non-negative long!", ownerId));

		this.start = OffsetDateTime.now();

		this.ownerId = ownerId;

		this.activity = activity;
		this.status = status;
		this.serverInvite = serverInvite;
		this.slashCommandIndex = new HashMap<>();
		this.slashCommands = new ArrayList<>();
		this.contextMenus = new ArrayList<>();
		this.contextMenuIndex = new HashMap<>();
		this.forcedGuildId = forcedGuildId;
		this.devGuildIds = devGuildIds==null || devGuildIds.length==0 ? null : devGuildIds;
		this.manualUpsert = manualUpsert;
		this.cooldowns = new HashMap<>();
		this.uses = new HashMap<>();
		this.shutdownAutomatically = shutdownAutomatically;
		this.executor = executor==null ? Executors.newSingleThreadScheduledExecutor() : executor;

		// Load slash commands
		for (SlashCommand command : slashCommands) {
			addSlashCommand(command);
		}

		// Load context menus
		for (ContextMenu menu : contextMenus) {
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
	public int getRemainingCooldown(String name) {
		if(cooldowns.containsKey(name)) {
			int time = (int) Math.ceil(OffsetDateTime.now().until(cooldowns.get(name), ChronoUnit.MILLIS) / 1000D);
			if (time<=0) {
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
	public void cleanCooldowns() {
		OffsetDateTime now = OffsetDateTime.now();
		cooldowns.keySet().stream().filter((str) -> (cooldowns.get(str).isBefore(now)))
				.toList().forEach(cooldowns::remove);
	}

	@Override
	public int getCommandUses(SlashCommand command) {
		return getCommandUses(command.getName());
	}

	@Override
	public int getCommandUses(String name)
	{
		return uses.getOrDefault(name, 0);
	}

	@Override
	public HashMap<String, Integer> getCommandUses() {
		return uses;
	}

	@Override
	public void addSlashCommand(SlashCommand command)
	{
		addSlashCommand(command, slashCommands.size());
	}

	@Override
	public void addSlashCommand(SlashCommand command, int index) {
		if (index>slashCommands.size() || index<0)
			throw new ArrayIndexOutOfBoundsException("Index specified is invalid: ["+index+"/"+slashCommands.size()+"]");
		synchronized(slashCommandIndex) {
			String name = command.getName().toLowerCase(Locale.ROOT);
			//check for collision
			if (slashCommandIndex.containsKey(name))
				throw new IllegalArgumentException("Command added has a name that has already been indexed: \""+name+"\"!");
			//shift if not append
			if (index<slashCommands.size()) {
				slashCommandIndex.entrySet().stream().filter(entry -> entry.getValue()>=index).toList()
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
	public void addContextMenu(ContextMenu menu, int index) {
		if (index>contextMenus.size() || index<0)
			throw new ArrayIndexOutOfBoundsException("Index specified is invalid: ["+index+"/"+contextMenus.size()+"]");
		synchronized(contextMenuIndex) {
			String name = menu.getName();
			//check for collision
			if (contextMenuIndex.containsKey(name)) {
				// Compare the existing menu's class to the new menu's class
				if (contextMenuIndex.get(name).getClass().getName().equals(menu.getClass().getName())) {
					throw new IllegalArgumentException("Context Menu added has a name and class that has already been indexed: \"" + name + "\"!");
				}
			}
			//shift if not append
			if (index<contextMenuIndex.size()) {
				contextMenuIndex.entrySet().stream().filter(entry -> entry.getValue()>=index).toList()
					.forEach(entry -> contextMenuIndex.put(entry.getKey(), entry.getValue()+1));
			}
			//add
			contextMenuIndex.put(name, index);
		}
		contextMenus.add(index,menu);
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
	public void shutdown()
	{
		executor.shutdown();
	}

	@Override
	public void onEvent(@NotNull GenericEvent event) {
		if (event instanceof SlashCommandInteractionEvent)
			onSlashCommand((SlashCommandInteractionEvent)event);

		else if (event instanceof MessageContextInteractionEvent)
			onMessageContextMenu((MessageContextInteractionEvent)event);
		else if (event instanceof UserContextInteractionEvent)
			onUserContextMenu((UserContextInteractionEvent)event);

		else if (event instanceof CommandAutoCompleteInteractionEvent)
			onCommandAutoComplete((CommandAutoCompleteInteractionEvent)event);

		else if (event instanceof ReadyEvent)
			onReady((ReadyEvent)event);
		else if (event instanceof ShutdownEvent) {
			if (shutdownAutomatically)
				shutdown();
		}
	}

	private void onReady(ReadyEvent event) {
		if (!event.getJDA().getSelfUser().isBot()) {
			LOG.error("JDA-Utilities does not support CLIENT accounts.");
			event.getJDA().shutdown();
			return;
		}

		if (activity != null)
			event.getJDA().getPresence().setPresence(status==null ? OnlineStatus.ONLINE : status,
				"default".equals(activity.getName()) ? Activity.playing("Type /help") : activity);

		// Upsert slash commands, if not manual
		if (!manualUpsert) {
			upsertInteractions(event.getJDA());
		}
	}

	@Override
	public void upsertInteractions(JDA jda) {
		if (devGuildIds == null) {
			upsertInteractions(jda, forcedGuildId);
		} else {
			upsertInteractions(jda, devGuildIds);
		}
	}

	@Override
	public void upsertInteractions(JDA jda, String serverId) {
		// Get all commands
		List<CommandData> data = new ArrayList<>();
		List<SlashCommand> slashCommands = getSlashCommands();
		//Map<String, SlashCommand> slashCommandMap = new HashMap<>();
		List<ContextMenu> contextMenus = getContextMenus();
		//Map<String, ContextMenu> contextMenuMap = new HashMap<>();

		// Build the command and privilege data
		for (SlashCommand command : slashCommands) {
			data.add(command.buildCommandData());
			//slashCommandMap.put(command.getName(), command);
		}

		for (ContextMenu menu : contextMenus) {
			data.add(menu.buildCommandData());
			//contextMenuMap.put(menu.getName(), menu);
		}

		// Upsert the commands
		if (serverId != null) {
			// Attempt to retrieve the provided guild
			Guild server = jda.getGuildById(serverId);
			if (server == null) {
				LOG.error("Specified forced guild is null! Slash Commands will NOT be added! Is the bot added?");
				return;
			}
			// Upsert the commands + their privileges
			server.updateCommands().addCommands(data)
				.queue(
					priv -> LOG.debug("Successfully added {} slash commands and {} menus to server {}", slashCommands.size(), contextMenus.size(), server.getName()),
					error -> LOG.error("Could not upsert commands! Does the bot have the applications.commands scope?", error)
				);
		}
		else
			jda.updateCommands().addCommands(data)
				.queue(commands -> LOG.debug("Successfully added {} slash commands!", commands.size()));
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
		for (SlashCommand command : slashCommands) {
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
			.queue(commands -> LOG.debug("Successfully added {} slash commands globally!", commands.size()));

		// Upsert the commands
		for (String serverId : serverIds) {
			// Attempt to retrieve the provided guild
			if (serverId == null) {
				LOG.error("One of the specified developer guild id is null! Check provided values.");
				return;
			}
			Guild server = jda.getGuildById(serverId);
			if (server == null) {
				LOG.error("Specified forced guild is null! Slash Commands will NOT be added! Is the bot added?");
				return;
			}
			// Upsert the commands + their privileges
			server.updateCommands().addCommands(dataDev)
				.queue(
					priv -> LOG.debug("Successfully added {} slash commands to server {}", dataDev.size(), server.getName()),
					error -> LOG.error("Could not upsert commands! Does the bot have the applications.commands scope?", error)
				);
		}
	}

	private void onSlashCommand(SlashCommandInteractionEvent event) {
		// this will be null if it's not a command
		final SlashCommand command = findSlashCommand(event.getFullCommandName());

		// Wrap the event in a SlashCommandEvent
		final SlashCommandEvent commandEvent = new SlashCommandEvent(event, this);

		if (command != null) {
			if (listener != null)
				listener.onSlashCommand(commandEvent, command);
			uses.put(command.getName(), uses.getOrDefault(command.getName(), 0) + 1);
			command.run(commandEvent);
			// Command is done
		}
	}

	private void onCommandAutoComplete(CommandAutoCompleteInteractionEvent event) {
		// this will be null if it's not a command
		final SlashCommand command = findSlashCommand(event.getFullCommandName());

		if (command != null) {
			command.onAutoComplete(event);
		}
	}

	private SlashCommand findSlashCommand(String path) {
		String[] parts = path.split(" ");

		final SlashCommand command; // this will be null if it's not a command
		synchronized(slashCommandIndex) {
			int i = slashCommandIndex.getOrDefault(parts[0].toLowerCase(Locale.ROOT), -1);
			command = i != -1? slashCommands.get(i) : null;
		}

		if (command == null)
			return null;

		return switch (parts.length) {
			case 1 -> // Slash command with no children
				command;
			case 2 -> {
				for (SlashCommand cmd : command.getChildren())
					if (cmd.isCommandFor(parts[1]))
						yield cmd;
				yield null; // Slash command with children
				// child check
			}
			case 3 -> {
				for (SlashCommand cmd : command.getChildren())
					if (cmd.isCommandFor(parts[2]) && cmd.getSubcommandGroup().getName().equals(parts[1]))
						yield cmd;
				yield null; // Slash command with a group and a child
			}
			default ->
				// How did we get here?
				null;
		};
	}

	private void onUserContextMenu(UserContextInteractionEvent event) {
		final UserContextMenu menu; // this will be null if it's not a command
		synchronized(contextMenuIndex) {
			ContextMenu c;
			int i = contextMenuIndex.getOrDefault(event.getName(), -1);
			c = i != -1 ? contextMenus.get(i) : null;

			if (c instanceof UserContextMenu)
				menu = (UserContextMenu) c;
			else
				menu = null;
		}

		final UserContextMenuEvent menuEvent = new UserContextMenuEvent(event.getJDA(), event.getResponseNumber(), event,this);

		if (menu != null) {
			if (listener != null)
				listener.onUserContextMenu(menuEvent, menu);
			uses.put(menu.getName(), uses.getOrDefault(menu.getName(), 0) + 1);
			menu.run(menuEvent);
			// Command is done
		}
	}

	private void onMessageContextMenu(MessageContextInteractionEvent event) {
		final MessageContextMenu menu; // this will be null if it's not a command
		synchronized(contextMenuIndex) {
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

		if (menu != null) {
			if (listener != null)
				listener.onMessageContextMenu(menuEvent, menu);
			uses.put(menu.getName(), uses.getOrDefault(menu.getName(), 0) + 1);
			menu.run(menuEvent);
			// Command is done
		}
	}

}
