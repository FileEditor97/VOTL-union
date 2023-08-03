package union.listeners;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import union.objects.command.CommandClient;
import union.objects.command.SlashCommand;
import union.utils.database.DBUtil;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;

public class AutoCompleteListener extends ListenerAdapter {

	private final List<SlashCommand> cmds;
	private final List<String> groupOwnerCmds = List.of("group delete", "group add", "group remove", "group rename", "group view");
	private final List<String> groupManageCmds = List.of("sync ban", "sync unban", "sync kick");
	private final List<String> groupJoinedCmds = List.of("group leave", "group view");

	private DBUtil db;

	public AutoCompleteListener(CommandClient cc, DBUtil dbutil) {
		cmds = cc.getSlashCommands();
		db = dbutil;
	}
		
	@Override
	public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
		String cmdName = event.getFullCommandName();
		String focusedOption = event.getFocusedOption().getName();
		if (cmdName.equals("help") && focusedOption.equals("command")) {
			String value = event.getFocusedOption().getValue().toLowerCase().split(" ")[0];
			List<Choice> choices = cmds.stream()
				.filter(cmd -> cmd.getName().contains(value))
				.map(cmd -> new Choice(cmd.getName(), cmd.getName()))
				.collect(Collectors.toList());
			if (choices.size() > 25) {
				choices.subList(25, choices.size()).clear();
			}
			event.replyChoices(choices).queue();
		}
		else if (groupOwnerCmds.contains(cmdName) && focusedOption.equals("group_owned")) {
			List<Integer> groupIds = db.group.getOwnedGroups(event.getGuild().getId());
			if (groupIds.isEmpty()) {
				event.replyChoices(Collections.emptyList()).queue();
			} else {
				List<Choice> choices = groupIds.stream()
					.map(groupId -> {
						String groupName = db.group.getName(groupId);
						return new Choice("%s (ID: %s)".formatted(groupName, groupId), groupId);
					})
					.collect(Collectors.toList());
				event.replyChoices(choices).queue();
			}
		}
		else if (groupJoinedCmds.contains(cmdName) && focusedOption.equals("group_joined")) {
			List<Integer> groupIds = db.group.getGuildGroups(event.getGuild().getId());
			if (groupIds.isEmpty()) {
				event.replyChoices(Collections.emptyList()).queue();
			} else {
				List<Choice> choices = groupIds.stream()
					.map(groupId -> {
						String groupName = db.group.getName(groupId);
						return new Choice("%s (ID: %s)".formatted(groupName, groupId), groupId);
					})
					.collect(Collectors.toList());
				event.replyChoices(choices).queue();
			}
		}
		else if (groupManageCmds.contains(cmdName) && focusedOption.equals("group")) {
			List<Integer> groupIds = new ArrayList<Integer>();
			groupIds.addAll(db.group.getOwnedGroups(event.getGuild().getId()));
			groupIds.addAll(db.group.getManagedGroups(event.getGuild().getId()));
			if (groupIds.isEmpty()) {
				event.replyChoices(Collections.emptyList()).queue();
			} else {
				List<Choice> choices = groupIds.stream()
					.map(groupId -> {
						String groupName = db.group.getName(groupId);
						return new Choice("%s (ID: %s)".formatted(groupName, groupId), groupId);
					})
					.collect(Collectors.toList());
				event.replyChoices(choices).queue();
			}
		}
	}

}

