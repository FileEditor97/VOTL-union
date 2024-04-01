package union.listeners;

import union.objects.annotation.NotNull;
import union.objects.logs.LogType;

import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateNameEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateTopicEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateVoiceStatusEvent;
import net.dv8tion.jda.api.events.guild.override.PermissionOverrideCreateEvent;
import net.dv8tion.jda.api.events.guild.override.PermissionOverrideDeleteEvent;
import net.dv8tion.jda.api.events.guild.override.PermissionOverrideUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class ChannelListener extends ListenerAdapter {
	
	private final LogType type = LogType.CHANNEL;
	
	@Override
	public void onChannelCreate(@NotNull ChannelCreateEvent event) {}

	@Override
	public void onChannelDelete(@NotNull ChannelDeleteEvent event) {}

	@Override
	public void onChannelUpdateName(@NotNull ChannelUpdateNameEvent event) {}

	@Override
	public void onChannelUpdateTopic(@NotNull ChannelUpdateTopicEvent event) {}

	@Override
	public void onChannelUpdateVoiceStatus(@NotNull ChannelUpdateVoiceStatusEvent event) {}

	@Override
	public void onPermissionOverrideDelete(@NotNull PermissionOverrideDeleteEvent event) {}

	@Override
	public void onPermissionOverrideUpdate(@NotNull PermissionOverrideUpdateEvent event) {}

	@Override
	public void onPermissionOverrideCreate(@NotNull PermissionOverrideCreateEvent event) {}

}
