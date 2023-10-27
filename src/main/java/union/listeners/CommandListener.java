package union.listeners;

import net.dv8tion.jda.api.entities.channel.ChannelType;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import union.base.command.MessageContextMenu;
import union.base.command.MessageContextMenuEvent;
import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.base.command.UserContextMenu;
import union.base.command.UserContextMenuEvent;

public class CommandListener implements union.base.command.CommandListener {

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
