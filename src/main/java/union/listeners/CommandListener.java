package union.listeners;

import union.objects.command.MessageContextMenu;
import union.objects.command.MessageContextMenuEvent;
import union.objects.command.SlashCommand;
import union.objects.command.SlashCommandEvent;
import union.objects.command.UserContextMenu;
import union.objects.command.UserContextMenuEvent;

import net.dv8tion.jda.api.entities.channel.ChannelType;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;

public class CommandListener implements union.objects.command.CommandListener {

	private final Logger LOGGER = (Logger) LoggerFactory.getLogger(CommandListener.class);
	
	@Override
	public void onSlashCommand(SlashCommandEvent event, SlashCommand command) {
		LOGGER.debug("SlashCommand @ {}\n- Name: '{}'; Full: '{}'\n- Author: {}\n- Guild: {}", event.getResponseNumber(), event.getName(), event.getCommandString(), event.getUser(),
			(event.getChannelType() != ChannelType.PRIVATE ? "'"+event.getGuild()+"' @ "+event.getChannel() : "PrivateChannel"));
	}

	@Override
	public void onMessageContextMenu(MessageContextMenuEvent event, MessageContextMenu menu) {
		LOGGER.debug("MessageContextMenu @ {}\n- Name: '{}'; Full: '{}'\n- Author: {}\n- Guild: {}", event.getResponseNumber(), event.getName(), event.getCommandString(), event.getUser(),
			(event.getChannelType() != ChannelType.PRIVATE ? "'"+event.getGuild()+"' @ "+event.getChannel() : "PrivateChannel"));
	}

	@Override
	public void onUserContextMenu(UserContextMenuEvent event, UserContextMenu menu) {
		LOGGER.debug("UserContextMenu @ {}\n- Name: '{}'; Full: '{}'\n- Author: {}\n- Guild: {}", event.getResponseNumber(), event.getName(), event.getCommandString(), event.getUser(),
			(event.getChannelType() != ChannelType.PRIVATE ? "'"+event.getGuild()+"' @ "+event.getChannel() : "PrivateChannel"));
	}

	@Override
	public void onCompletedSlashCommand(SlashCommandEvent event, SlashCommand command) {
		LOGGER.debug("SlashCommand Completed @ {}", event.getResponseNumber());
	}

	@Override
	public void onCompletedMessageContextMenu(MessageContextMenuEvent event, MessageContextMenu menu) {
		LOGGER.debug("MessageContextMenu Completed @ {}", event.getResponseNumber());
	}

	@Override
	public void onCompletedUserContextMenu(UserContextMenuEvent event, UserContextMenu menu) {
		LOGGER.debug("UserContextMenu Completed @ {}", event.getResponseNumber());
	}

	@Override
	public void onTerminatedSlashCommand(SlashCommandEvent event, SlashCommand command) {
		LOGGER.debug("SlashCommand Terminated @ {}", event.getResponseNumber());
	}

	@Override
	public void onTerminatedMessageContextMenu(MessageContextMenuEvent event, MessageContextMenu menu) {
		LOGGER.debug("MessageContextMenu Terminated @ {}", event.getResponseNumber());
	}

	@Override
	public void onTerminatedUserContextMenu(UserContextMenuEvent event, UserContextMenu menu) {
		LOGGER.debug("UserContextMenu Terminated @ {}", event.getResponseNumber());
	}

}
