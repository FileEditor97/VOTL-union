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

import net.dv8tion.jda.api.interactions.InteractionContextType;
import org.jetbrains.annotations.NotNull;
import union.objects.CmdAccessLevel;
import union.utils.exception.CheckException;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.util.Set;

public abstract class MessageContextMenu extends ContextMenu
{
	/**
	 * Runs checks for the {@link MessageContextMenu} with the given {@link MessageContextMenuEvent} that called it.
	 * <br>Will terminate, and possibly respond with a failure message, if any checks fail.
	 *
	 * @param  event
	 *         The MessageContextMenuEvent that triggered this menu
	 */
	public final void run(MessageContextMenuEvent event) {
		// client 
		final CommandClient client = event.getClient();

		// owner check
		if (ownerCommand && !event.isOwner()) {
			terminate(event, bot.getEmbedUtil().getError(event, "errors.command.not_owner"), client);
			return;
		}

		// cooldown check, ignoring owner
		if (cooldown>0 && !event.isOwner()) {
			String key = getCooldownKey(event);
			int remaining = client.getRemainingCooldown(key);
			if (remaining > 0) {
				terminate(event, getCooldownError(event, event.getGuild(), remaining), client);
				return;
			}
			else client.applyCooldown(key, cooldown);
		}

		// checks
		if (event.isFromGuild()) {
			Guild guild = event.getGuild();
			Member author = event.getMember();
			try {
				bot.getCheckUtil()
				// check module enabled
					.moduleEnabled(event, guild, getModule())
				// check access
					.hasAccess(event, author, getAccessLevel())
				// check user perms
					.hasPermissions(event, getUserPermissions(), author)
				// check bots perms
					.hasPermissions(event, getBotPermissions());
			} catch (CheckException ex) {
				terminate(event, ex.getCreateData(), client);
				return;
			}
		} else if (guildOnly) {
			terminate(event, bot.getEmbedUtil().getError(event, "errors.command.guild_only"), client);
			return;
		}

		// run
		try {
			execute(event);
		} catch (Throwable t) {
			if (client.getListener() != null) {
				client.getListener().onMessageContextMenuException(event, this, t);
				return;
			}
			// otherwise we rethrow
			throw t;
		}

		if (client.getListener() != null)
			client.getListener().onCompletedMessageContextMenu(event, this);
	}

	/**
	 * The main body method of a {@link MessageContextMenu}.
	 * <br>This is the "response" for a successful
	 * {@link MessageContextMenu#run(MessageContextMenuEvent)}
	 *
	 * @param  event
	 *         The {@link MessageContextMenuEvent} that triggered this menu.
	 */
	protected abstract void execute(MessageContextMenuEvent event);

	private void terminate(MessageContextMenuEvent event, @NotNull MessageEmbed embed, CommandClient client) {
		terminate(event, MessageCreateData.fromEmbeds(embed), client);
	}

	private void terminate(MessageContextMenuEvent event, MessageCreateData message, CommandClient client) {
		if (message!=null)
			event.reply(message).setEphemeral(true).queue();
		if (client.getListener()!=null)
			client.getListener().onTerminatedMessageContextMenu(event, this);
	}

	@Override
	public CommandData buildCommandData() {
		// Set attributes
		this.nameLocalization = lu.getFullLocaleMap(getPath()+".name", lu.getText(getPath()+".name"));

		// Make the command data
		CommandData data = Commands.message(getName());

		// Check name localizations
		if (!getNameLocalization().isEmpty()) {
			//Add localizations
			data.setNameLocalizations(getNameLocalization());
		}
		
		if (!isOwnerCommand() || getAccessLevel().isLowerThan(CmdAccessLevel.ADMIN))
			data.setDefaultPermissions(DefaultMemberPermissions.enabledFor(getUserPermissions()));
		else
			data.setDefaultPermissions(DefaultMemberPermissions.DISABLED);

		data.setContexts(this.guildOnly ? Set.of(InteractionContextType.GUILD) : Set.of(InteractionContextType.GUILD, InteractionContextType.BOT_DM));

		return data;
	}
}
