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

/**
 * An implementable "Listener" that can be added to a {@link CommandClient}.
 *
 * @author John Grosh (jagrosh)
 */
@SuppressWarnings("unused")
public interface CommandListener {
	/**
	 * Called when a {@link SlashCommand} is triggered by a {@link SlashCommandEvent SlashCommandEvent}.
	 *
	 * @param event   The SlashCommandEvent that triggered the Command
	 * @param command The SlashCommand that was triggered
	 */
	default void onSlashCommand(SlashCommandEvent event, SlashCommand command) {}

	/**
	 * Called when a {@link MessageContextMenu} is triggered by a {@link MessageContextMenuEvent}.
	 *
	 * @param event The MessageContextMenuEvent that triggered the MessageContextMenu
	 * @param menu  The MessageContextMenu that was triggered
	 */
	default void onMessageContextMenu(MessageContextMenuEvent event, MessageContextMenu menu) {}

	/**
	 * Called when a {@link UserContextMenu} is triggered by a {@link UserContextMenuEvent}.
	 *
	 * @param event The UserContextMenuEvent that triggered the UserContextMenu
	 * @param menu  The UserContextMenu that was triggered
	 */
	default void onUserContextMenu(UserContextMenuEvent event, UserContextMenu menu) {}

	/**
	 * Called when a {@link SlashCommand} is triggered by a {@link SlashCommandEvent} after it's completed successfully.
	 *
	 * <p>Note that a <i>successfully</i> completed slash command is one that has not encountered
	 * an error or exception. Calls that do face errors should be handled by
	 * {@link CommandListener#onSlashCommandException(SlashCommandEvent, SlashCommand, Throwable) CommandListener#onSlashCommandException}
	 *
	 * @param event   The SlashCommandEvent that triggered the Command
	 * @param command The SlashCommand that was triggered
	 */
	default void onCompletedSlashCommand(SlashCommandEvent event, SlashCommand command) {}

	/**
	 * Called when a {@link MessageContextMenu} is triggered by a {@link MessageContextMenuEvent}
	 * after it's completed successfully.
	 *
	 * <p>Note that a <i>successfully</i> completed context menu interaction is one that has not encountered
	 * an error or exception. Calls that do face errors should be handled by
	 * {@link CommandListener#onTerminatedMessageContextMenu(MessageContextMenuEvent, MessageContextMenu)}
	 *
	 * @param event The MessageContextMenuEvent that triggered the Menu
	 * @param menu  The MessageContextMenu that was triggered
	 */
	default void onCompletedMessageContextMenu(MessageContextMenuEvent event, MessageContextMenu menu) {}

	/**
	 * Called when a {@link UserContextMenu} is triggered by a {@link UserContextMenuEvent}
	 * after it's completed successfully.
	 *
	 * <p>Note that a <i>successfully</i> completed context menu interaction is one that has not encountered
	 * an error or exception. Calls that do face errors should be handled by
	 * {@link CommandListener#onTerminatedUserContextMenu(UserContextMenuEvent, UserContextMenu)}
	 *
	 * @param event The MessageContextMenuEvent that triggered the Menu
	 * @param menu  The MessageContextMenu that was triggered
	 */
	default void onCompletedUserContextMenu(UserContextMenuEvent event, UserContextMenu menu) {}

	/**
	 * Called when a {@link SlashCommand} is triggered by a {@link SlashCommandEvent} but is terminated before completion.
	 *
	 * @param event   The SlashCommandEvent that triggered the Command
	 * @param command The SlashCommand that was triggered
	 */
	default void onTerminatedSlashCommand(SlashCommandEvent event, SlashCommand command) {}

	/**
	 * Called when a {@link MessageContextMenu} is triggered by a {@link MessageContextMenuEvent} but is terminated before completion.
	 *
	 * @param event The ContextMenuEvent that triggered the Context Menu
	 * @param menu  The ContextMenu that was triggered
	 */
	default void onTerminatedMessageContextMenu(MessageContextMenuEvent event, MessageContextMenu menu) {}

	/**
	 * Called when a {@link UserContextMenu} is triggered by a {@link UserContextMenuEvent} but is terminated before completion.
	 *
	 * @param event The ContextMenuEvent that triggered the Context Menu
	 * @param menu  The ContextMenu that was triggered
	 */
	default void onTerminatedUserContextMenu(UserContextMenuEvent event, UserContextMenu menu) {}

	/**
	 * Called when a {@link union.base.command.SlashCommand SlashCommand}
	 * catches a {@link java.lang.Throwable Throwable} <b>during execution</b>.
	 *
	 * <p>This doesn't account for exceptions thrown during other pre-checks,
	 * and should not be treated as such!
	 * <p>
	 * The {@link java.lang.NullPointerException NullPointerException} thrown will not be caught by this method!
	 *
	 * @param event     The CommandEvent that triggered the Command
	 * @param command   The Command that was triggered
	 * @param throwable The Throwable thrown during Command execution
	 */
	default void onSlashCommandException(SlashCommandEvent event, SlashCommand command, Throwable throwable) {
		// Default rethrow as a runtime exception.
		throw throwable instanceof RuntimeException ? (RuntimeException) throwable : new RuntimeException(throwable);
	}

	/**
	 * Called when a {@link ContextMenu} catches a {@link java.lang.Throwable Throwable} <b>during execution</b>.
	 *
	 * <p>This doesn't account for exceptions thrown during other pre-checks,
	 * and should not be treated as such!
	 * <p>
	 * The {@link NullPointerException} thrown will not be caught by this method!
	 *
	 * @param event     The Context Menu Event that triggered the ContextMenu
	 * @param menu      The Context Menu that was triggered
	 * @param throwable The Throwable thrown during Command execution
	 */
	default void onMessageContextMenuException(MessageContextMenuEvent event, MessageContextMenu menu, Throwable throwable) {
		// Default rethrow as a runtime exception.
		throw throwable instanceof RuntimeException ? (RuntimeException) throwable : new RuntimeException(throwable);
	}

	/**
	 * Called when a {@link ContextMenu} catches a {@link java.lang.Throwable Throwable} <b>during execution</b>.
	 *
	 * <p>This doesn't account for exceptions thrown during other pre-checks,
	 * and should not be treated as such!
	 * <p>
	 * The {@link NullPointerException} thrown will not be caught by this method!
	 *
	 * @param event     The Context Menu Event that triggered the ContextMenu
	 * @param menu      The Context Menu that was triggered
	 * @param throwable The Throwable thrown during Command execution
	 */
	default void onUserContextMenuException(UserContextMenuEvent event, UserContextMenu menu, Throwable throwable) {
		// Default rethrow as a runtime exception.
		throw throwable instanceof RuntimeException ? (RuntimeException) throwable : new RuntimeException(throwable);
	}
}
