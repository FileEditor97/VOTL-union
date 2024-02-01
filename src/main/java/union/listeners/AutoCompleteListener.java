package union.listeners;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.annotation.Nonnull;
import union.base.command.CommandClient;
import union.base.command.SlashCommand;
import union.utils.database.DBUtil;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;

public class AutoCompleteListener extends ListenerAdapter {

	private final List<SlashCommand> cmds;

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
		else if (focusedOption.equals("group_owned")) {
			List<Integer> groupIds = db.group.getOwnedGroups(event.getGuild().getIdLong());
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
		else if (focusedOption.equals("group_joined")) {
			List<Integer> groupIds = db.group.getGuildGroups(event.getGuild().getIdLong());
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
		else if (focusedOption.equals("group")) {
			List<Integer> groupIds = new ArrayList<Integer>();
			groupIds.addAll(db.group.getOwnedGroups(event.getGuild().getIdLong()));
			groupIds.addAll(db.group.getManagedGroups(event.getGuild().getIdLong()));
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
		else if (focusedOption.equals("panel_id")) {
			String value = event.getFocusedOption().getValue();
			String guildId = event.getGuild().getId();
			if (value.isBlank()) {
				// if input is blank, show max 25 choices
				List<Choice> choices = db.panels.getPanelsText(guildId).entrySet().stream()
					.map(panel -> {
						return new Choice("%s | %s".formatted(panel.getKey(), panel.getValue()), panel.getKey());
					})
					.collect(Collectors.toList());
				event.replyChoices(choices).queue();
				return;
			}

			Integer id = null;
			try {
				id = Integer.valueOf(value);
			} catch(NumberFormatException ex) {}
			if (id != null) {
				// if able to convert input to Integer
				String title = db.panels.getPanelTitle(id);
				if (title != null) {
					// if found panel with matching Id
					event.replyChoice("%s | %s".formatted(id, title.substring(0, Math.min(90, title.length()))), id).queue();
					return;
				}
			}
			event.replyChoices(Collections.emptyList()).queue();
		}
		else if (focusedOption.equals("tag_id")) {
			String value = event.getFocusedOption().getValue();
			String guildId = event.getGuild().getId();
			if (value.isBlank()) {
				// if input is blank, show max 25 choices
				List<Choice> choices = db.tags.getTagsText(guildId).entrySet().stream()
					.map(panel -> {
						return new Choice("%s | %s".formatted(panel.getKey(), panel.getValue()), panel.getKey());
					})
					.collect(Collectors.toList());
				event.replyChoices(choices).queue();
				return;
			}

			Integer id = null;
			try {
				id = Integer.valueOf(value);
			} catch(NumberFormatException ex) {}
			if (id != null) {
				// if able to convert input to Integer
				String title = db.tags.getTagText(id);
				if (title != null) {
					// if found panel with matching Id
					event.replyChoice("%s - %s".formatted(id, title), id).queue();
					return;
				}
			}
			event.replyChoices(Collections.emptyList()).queue();
		}
	}

}

