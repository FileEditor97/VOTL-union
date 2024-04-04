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

import union.App;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.annotation.NotNull;
import union.utils.file.lang.LocaleUtil;

import net.dv8tion.jda.api.Permission;

/**
 * A class that represents an interaction with a user.
 *
 * This is all information used for all forms of interactions. Namely, permissions and cooldowns.
 *
 * Any content here is safely functionality equivalent regardless of the source of the interaction.
 */
public abstract class Interaction
{
	/**
	 * {@code true} if the command may only be used in a {@link net.dv8tion.jda.api.entities.Guild Guild},
	 * {@code false} if it may be used in both a Guild and a DM.
	 * <br>Default {@code true}.
	 */
	protected boolean guildOnly = true;

	/**
	 * Any {@link Permission Permissions} a Member must have to use this interaction.
	 * <br>These are only checked in a {@link net.dv8tion.jda.api.entities.Guild server} environment.
	 * <br>To disable the command for everyone (for interactions), set this to {@code null}.
	 * <br>Keep in mind, commands may still show up if the channel permissions are updated in settings.
	 * Otherwise, commands will automatically be hidden unless a user has these perms.
	 * However, permissions are always checked, just in case. A user must have these permissions regardless.
	 */
	@NotNull
	protected Permission[] userPermissions = new Permission[0];

	/**
	 * Any {@link Permission Permissions} the bot must have to use a command.
	 * <br>These are only checked in a {@link net.dv8tion.jda.api.entities.Guild server} environment.
	 */
	@NotNull
	protected Permission[] botPermissions = new Permission[0];

	/**
	 * {@code true} if the interaction may only be used by a User with an ID matching the
	 * Owners or any of the CoOwners.<br>
	 * If enabled for a Slash Command, only owners (owner + up to 9 co-owners) will be added to the SlashCommand.
	 * All other permissions will be ignored.
	 * <br>Default {@code false}.
	 */
	protected boolean ownerCommand = false;

	/**
	 * An {@code int} number of seconds users must wait before using this command again.
	 */
	protected int cooldown = 0;

	/**
	 * The {@link CooldownScope CooldownScope}
	 * of the command. This defines how far the scope the cooldowns have.
	 * <br>Default {@link CooldownScope#USER CooldownScope.USER}.
	 */
	protected CooldownScope cooldownScope = CooldownScope.USER;

	/**
	 * Gets the {@link Interaction#cooldown cooldown} for the Interaction.
	 *
	 * @return The cooldown for the Interaction
	 */
	public int getCooldown()
	{
		return cooldown;
	}

	/**
	 * Gets the {@link Interaction#cooldown cooldown} for the Interaction.
	 *
	 * @return The cooldown for the Interaction
	 */
	public CooldownScope getCooldownScope()
	{
		return cooldownScope;
	}

	/**
	 * Gets the {@link Interaction#userPermissions userPermissions} for the Interaction.
	 *
	 * @return The userPermissions for the Interaction
	 */
	@NotNull
	public Permission[] getUserPermissions()
	{
		return userPermissions;
	}

	/**
	 * Gets the {@link Interaction#botPermissions botPermissions} for the Interaction.
	 *
	 * @return The botPermissions for the Interaction
	 */
	@NotNull
	public Permission[] getBotPermissions()
	{
		return botPermissions;
	}

	/**
	 * Checks whether this is an owner only Interaction, meaning only the owner and co-owners can use it.
	 *
	 * @return {@code true} if the command is an owner interaction, otherwise {@code false} if it is not
	 */
	public boolean isOwnerCommand()
	{
		return ownerCommand;
	}
	
	/**
	 * Gets the help text path based on {@link Interaction#path Interaction.path}.
	 *
	 * @return The path for command's help string in locale file.
	 */
	@NotNull
	public String getHelpPath()
	{
		return path+".help";
	}

	/**
	 * Gets the usage text path based on {@link Interaction#path Interaction.path}.
	 *
	 * @return The path for command's usage description string in locale file.
	 */
	@NotNull
	public String getUsagePath()
	{
		return path+".usage";
	}

	/**
	 * Path to the command strings. Must by set, otherwise will display Unknown text.
	 */
	@NotNull
	protected String path = "misc.command";
	
	/**
	 * Gets the {@link Interaction#path Interaction.path} for the Command.
	 *
	 * @return The path for command's string in locale file.
	 */
	@NotNull
	public String getPath()
	{
		return path;
	}

	protected App bot = null;

	protected LocaleUtil lu = null;

	protected CmdModule module = null;

	protected CmdAccessLevel accessLevel = CmdAccessLevel.ALL;

	public App getApp() {
		return bot;
	}

	public LocaleUtil getLocaleUtil() {
		return lu;
	}

	public CmdAccessLevel getAccessLevel() {
		return accessLevel;
	}

	public CmdModule getModule() {
		return module;
	}
	
}
