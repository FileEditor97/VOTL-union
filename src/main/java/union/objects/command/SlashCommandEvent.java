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

import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Mentions;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import org.jetbrains.annotations.Contract;

/**
 * A wrapper class for a {@link SlashCommandInteractionEvent} and {@link CommandClient}.
 *
 * <p>From here, developers can invoke several useful and specialized methods to assist in Command function and
 * development. Because this extends SlashCommandInteractionEvent, all methods from it work fine.
 *
 * @author Olivia (Chew)
 */
public class SlashCommandEvent extends SlashCommandInteractionEvent {
	private final CommandClient client;

	public SlashCommandEvent(SlashCommandInteractionEvent event, CommandClient client)
	{
		super(event.getJDA(), event.getResponseNumber(), event);
		this.client = client;
	}

	/**
	 * The {@link CommandClient} that this event was triggered from.
	 *
	 * @return The CommandClient that this event was triggered from
	 */
	public CommandClient getClient()
	{
		return client;
	}

	/**
	 * Gets the provided Option Key as a String value, or returns {@code null} if the option cannot be found.
	 *
	 * @param key   The option we want
	 * @return The provided option, or null if the option is not present
	 */
	@Nullable
	public String optString(@Nonnull String key) {
		return optString(key, null);
	}

	/**
	 * Gets the provided Option Key as a String value, or returns the default one if the option cannot be found.
	 *
	 * @param key          The option we want
	 * @param defaultValue Nullable default value used in the absence of the option value
	 * @return The provided option, or the default value if the option is not present
	 */
	@Nullable
	@Contract("_, !null -> !null")
	public String optString(@Nonnull String key, @Nullable String defaultValue) {
		return getOption(key, defaultValue, OptionMapping::getAsString);
	}

	/**
	 * Gets the provided Option Key as a boolean value, or returns {@code false} if the option cannot be found.
	 *
	 * @param key   The option we want
	 * @return The provided option, or false if the option is not present
	 */
	public boolean optBoolean(@Nonnull String key) {
		return optBoolean(key, false);
	}

	/**
	 * Gets the provided Option Key as a boolean value, or returns the default one if the option cannot be found.
	 *
	 * @param key          The option we want
	 * @param defaultValue The fallback option in case of the absence of the option value
	 * @return The provided option, or the default value if the option is not present
	 */
	public boolean optBoolean(@Nonnull String key, boolean defaultValue) {
		return getOption(key, defaultValue, OptionMapping::getAsBoolean);
	}

	/**
	 * Gets the provided Option Key as a int value, or returns {@code null} if the option cannot be found.
	 *
	 * @param key   The option we want
	 * @return The provided option, or null if the option is not present
	 */
	public Integer optInteger(@Nonnull String key) {
		return optInteger(key, null);
	}

	/**
	 * Gets the provided Option Key as a int value, or returns the default one if the option cannot be found.
	 *
	 * @param key          The option we want
	 * @param defaultValue The fallback option in case of the absence of the option value
	 * @return The provided option, or the default value if the option is not present
	 */
	public Integer optInteger(@Nonnull String key, Integer defaultValue) {
		return getOption(key, defaultValue, OptionMapping::getAsInt);
	}

	/**
	 * Gets the provided Option Key as a long value, or returns {@code 0} if the option cannot be found.
	 *
	 * @param key   The option we want
	 * @return The provided option, or 0 if the option is not present
	 */
	public long optLong(@Nonnull String key) {
		return optLong(key, 0);
	}

	/**
	 * Gets the provided Option Key as a long value, or returns the default one if the option cannot be found.
	 *
	 * @param key          The option we want
	 * @param defaultValue The fallback option in case of the absence of the option value
	 * @return The provided option, or the default value if the option is not present
	 */
	public long optLong(@Nonnull String key, long defaultValue) {
		return getOption(key, defaultValue, OptionMapping::getAsLong);
	}

	/**
	 * Gets the provided Option Key as a double value, or returns {@code 0.0} if the option cannot be found.
	 *
	 * @param key   The option we want
	 * @return The provided option, or 0.0 if the option is not present
	 */
	public double optDouble(@Nonnull String key) {
		return optDouble(key, 0.0);
	}

	/**
	 * Gets the provided Option Key as a double value, or returns the default one if the option cannot be found.
	 *
	 * @param key          The option we want
	 * @param defaultValue The fallback option in case of the absence of the option value
	 * @return The provided option, or the default value if the option is not present
	 */
	public double optDouble(@Nonnull String key, double defaultValue) {
		return getOption(key, defaultValue, OptionMapping::getAsDouble);
	}

	/**
	 * Gets the provided Option Key as a GuildChannel value, or returns {@code null} if the option cannot be found.
	 * <br>This will <b>always</b> return null when the SlashCommandEvent was not executed in a Guild.
	 *
	 * @param key   The option we want
	 * @return The provided option, or null if the option is not present
	 */
	@Nullable
	public GuildChannel optGuildChannel(@Nonnull String key) {
		return optGuildChannel(key, null);
	}

	/**
	 * Gets the provided Option Key as a GuildChannel value, or returns the default one if the option cannot be found.
	 * <br>This will <b>always</b> return the default value when the SlashCommandEvent was not executed in a Guild.
	 *
	 * @param key          The option we want
	 * @param defaultValue Nullable default value used in the absence of the option value
	 * @return The provided option, or the default value if the option is not present
	 */
	@Nullable
	@Contract("_, !null -> !null")
	public GuildChannel optGuildChannel(@Nonnull String key, @Nullable GuildChannel defaultValue) {
		if (!isFromGuild())
			return defaultValue;

		return getOption(key, defaultValue, optionMapping -> optionMapping.getAsChannel().asStandardGuildChannel());
	}

	/**
	 * Gets the provided Option Key as a Member value, or returns {@code null} if the option cannot be found.
	 * <br>This will <b>always</b> return null when the SlashCommandEvent was not executed in a Guild.
	 *
	 * @param key   The option we want
	 * @return The provided option, or null if the option is not present
	 */
	@Nullable
	public Member optMember(@Nonnull String key) {
		return optMember(key, null);
	}

	/**
	 * Gets the provided Option Key as a Member value, or returns the default one if the option cannot be found.
	 * <br>This will <b>always</b> return the default value when the SlashCommandEvent was not executed in a Guild.
	 *
	 * @param key          The option we want
	 * @param defaultValue Nullable default value used in the absence of the option value
	 * @return The provided option, or the default value if the option is not present
	 */
	@Nullable
	@Contract("_, !null -> !null")
	public Member optMember(@Nonnull String key, @Nullable Member defaultValue) {
		if (!isFromGuild())
			return defaultValue; // Non-guild commands do not have a member.

		return getOption(key, defaultValue, OptionMapping::getAsMember);
	}

	/**
	 * Gets the provided Option Key as a IMentionable value, or returns {@code null} if the option cannot be found.
	 *
	 * @param key   The option we want
	 * @return The provided option, or null if the option is not present
	 */
	@Nullable
	public IMentionable optMentionable(@Nonnull String key) {
		return optMentionable(key, null);
	}

	/**
	 * Gets the provided Option Key as a IMentionable value, or returns the default one if the option cannot be found.
	 *
	 * @param key          The option we want
	 * @param defaultValue Nullable default value used in the absence of the option value
	 * @return The provided option, or the default value if the option is not present
	 */
	@Nullable
	@Contract("_, !null -> !null")
	public IMentionable optMentionable(@Nonnull String key, @Nullable IMentionable defaultValue) {
		return getOption(key, defaultValue, OptionMapping::getAsMentionable);
	}

	/**
	 * Gets the provided Option Key as a Mentions value, or return {@code null} if the there were no mentions or the options cannot be found.
	 * 
	 * @param key  The option we want
	 * @return The provided options, or null if they are no mentions or option is not present
	 */
	@Nullable
	public Mentions optMentions(@Nonnull String key) {
		return getOption(key, null, OptionMapping::getMentions);
	}

	/**
	 * Gets the provided Option Key as a Role value, or returns {@code null} if the option cannot be found.
	 * <br>This will <b>always</b> return null when the SlashCommandEvent was not executed in a Guild.
	 *
	 * @param key   The option we want
	 * @return The provided option, or null if the option is not present
	 */
	@Nullable
	public Role optRole(@Nonnull String key) {
		return optRole(key, null);
	}

	/**
	 * Gets the provided Option Key as a Role value, or returns the default one if the option cannot be found.
	 * <br>This will <b>always</b> return the default value when the SlashCommandEvent was not executed in a Guild.
	 *
	 * @param key          The option we want
	 * @param defaultValue Nullable default value used in the absence of the option value
	 * @return The provided option, or the default value if the option is not present
	 */
	@Nullable
	@Contract("_, !null -> !null")
	public Role optRole(@Nonnull String key, @Nullable Role defaultValue) {
		if (!isFromGuild())
			return defaultValue;

		return getOption(key, defaultValue, OptionMapping::getAsRole);
	}

	/**
	 * Gets the provided Option Key as a User value, or returns {@code null} if the option cannot be found.
	 *
	 * @param key   The option we want
	 * @return The provided option, or null if the option is not present
	 */
	@Nullable
	public User optUser(@Nonnull String key) {
		return optUser(key, null);
	}

	/**
	 * Gets the provided Option Key as a User value, or returns the default one if the option cannot be found.
	 *
	 * @param key          The option we want
	 * @param defaultValue Nullable default value used in the absence of the option value
	 * @return The provided option, or the default value if the option is not present
	 */
	@Nullable
	@Contract("_, !null -> !null")
	public User optUser(@Nonnull String key, @Nullable User defaultValue) {
		return getOption(key, defaultValue, OptionMapping::getAsUser);
	}

	/**
	 * Gets the provided Option Key as a MessageChannel value, or returns {@code null} if the option cannot be found.
	 *
	 * @param key   The option we want
	 * @return The provided option, or null if the option is not present
	 */
	@Nullable
	public MessageChannel optMessageChannel(@Nonnull String key) {
		return optMessageChannel(key, null);
	}

	/**
	 * Gets the provided Option Key as a MessageChannel value, or returns the default one if the option cannot be found.
	 *
	 * @param key          The option we want
	 * @param defaultValue Nullable default value used in the absence of the option value
	 * @return The provided option, or the default value if the option is not present
	 */
	@Nullable
	@Contract("_, !null -> !null")
	public MessageChannel optMessageChannel(@Nonnull String key, @Nullable MessageChannel defaultValue) {
		return getOption(key, defaultValue, optionMapping -> optionMapping.getAsChannel().asGuildMessageChannel());
	}

	/**
	 * Gets the provided Option Key as an Attachment value, or returns {@code null} if the option cannot be found.
	 *
	 * @param key          The option we want
	 * @return The provided option, or the default value if the option is not present
	 */
	@Nullable
	public Message.Attachment optAttachment(@Nonnull String key) {
		return optAttachment(key, null);
	}

	/**
	 * Gets the provided Option Key as an Attachment value, or returns the default one if the option cannot be found.
	 *
	 * @param key          The option we want
	 * @param defaultValue Nullable default value used in the absence of the option value
	 * @return The provided option, or the default value if the option is not present
	 */
	@Nullable
	@Contract("_, !null -> !null")
	public Message.Attachment optAttachment(@Nonnull String key, @Nullable Message.Attachment defaultValue) {
		return getOption(key, defaultValue, OptionMapping::getAsAttachment);
	}

	/**
	 * Will return if the provided key resolves into a provided Option for the SlashCommand.
	 *
	 * @param key   the option we want
	 * @return true if the option exists, false otherwise
	 */
	public boolean hasOption(@Nonnull String key) {
		return getOption(key) != null;
	}

	/**
	 * Compares a provided {@link ChannelType} with the one this event occurred on,
	 * returning {@code true} if they are the same ChannelType.
	 *
	 * @param  channelType
	 *         The ChannelType to compare
	 *
	 * @return {@code true} if the CommandEvent originated from a {@link MessageChannel}
	 *         of the provided ChannelType, otherwise {@code false}.
	 */
	public boolean isFromType(ChannelType channelType)
	{
		return getChannelType() == channelType;
	}

	/**
	 * Gets the {@link TextChannel} that this CommandEvent
	 * may have taken place on, or {@code null} if it didn't happen on a TextChannel.
	 *
	 * @return The TextChannel this CommandEvent may have taken place on, or null
	 *         if it did not happen on a TextChannel.
	 */
	public TextChannel getTextChannel() {
		return getChannel().asTextChannel();
	}
}
