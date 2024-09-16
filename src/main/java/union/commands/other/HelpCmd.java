package union.commands.other;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import union.base.command.Category;
import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.Emotes;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class HelpCmd extends CommandBase {

	public HelpCmd() {
		this.name = "help";
		this.path = "bot.help";
		this.options = List.of(
			new OptionData(OptionType.STRING, "category", lu.getText(path+".category.help"))
				.addChoice("Guild", "guild")
				.addChoice("Owner", "owner")
				.addChoice("Webhook", "webhook")
				.addChoice("Moderation", "moderation")
				.addChoice("Verification", "verification")
				.addChoice("Ticketing", "ticketing")
				.addChoice("Voice", "voice")
				.addChoice("Roles", "roles")
				.addChoice("Games", "games")
				.addChoice("Images", "image")
				.addChoice("Other", "other"),
			new OptionData(OptionType.STRING, "command", lu.getText(path+".command.help"), false, true).setRequiredLength(3, 20),
			new OptionData(OptionType.BOOLEAN, "show", lu.getText(path+".show.help"))
		);
		this.category = CmdCategory.OTHER;
		this.guildOnly = false;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply(event.isFromGuild() && !event.optBoolean("show", false)).queue();

		String findCmd = event.optString("command");
				
		if (findCmd != null) {
			sendCommandHelp(event, findCmd.split(" ")[0].toLowerCase());
		} else {
			String filCat = event.optString("category");
			sendHelp(event, filCat);
		}
	}

	private void sendCommandHelp(SlashCommandEvent event, String findCmd) {
		DiscordLocale userLocale = event.getUserLocale();

		SlashCommand command = null;
		for (SlashCommand cmd : event.getClient().getSlashCommands()) {
			if (cmd.getName().equals(findCmd)) {
				command = cmd;
				break;
			}
		}

		if (command == null) {
			editError(event, "bot.help.command_info.no_command", "Requested: "+findCmd);
		} else {
			MessageEmbed embed = new EmbedBuilder().setColor(Constants.COLOR_DEFAULT)
				.setTitle(lu.getLocalized(userLocale, "bot.help.command_info.title").replace("{command}", command.getName()))
				.setDescription(lu.getLocalized(userLocale, "bot.help.command_info.value")
					.replace("{category}", Optional.ofNullable(command.getCategory())
						.map(cat -> lu.getLocalized(userLocale, "bot.help.command_menu.categories."+cat.name())).orElse(Constants.NONE))
					.replace("{owner}", command.isOwnerCommand() ? Emotes.CHECK_C.getEmote() : Emotes.CROSS_C.getEmote())
					.replace("{guild}", command.isGuildOnly() ? Emotes.CROSS_C.getEmote() : Emotes.CHECK_C.getEmote())
					.replace("{module}", Optional.ofNullable(command.getModule()).map(mod -> lu.getLocalized(userLocale, mod.getPath())).orElse(Constants.NONE)))
				.addField(lu.getLocalized(userLocale, "bot.help.command_info.help_title"), lu.getLocalized(userLocale, command.getHelpPath()), false)
				.addField(lu.getLocalized(userLocale, "bot.help.command_info.usage_title"), getUsageText(userLocale, command), false)
				.setFooter(lu.getLocalized(userLocale, "bot.help.command_info.usage_subvalue"))
				.build();
			
			editHookEmbed(event, embed);
		}
		
	}

	private String getUsageText(DiscordLocale locale, SlashCommand command) {
		StringBuilder builder = new StringBuilder();
		if (command.getChildren().length > 0) {
			String base = command.getName();
			for (SlashCommand child : command.getChildren()) {
				builder.append(
					lu.getLocalized(locale, "bot.help.command_info.usage_child")
						.replace("{base}", base)
						.replace("{usage}", lu.getLocalized(locale, child.getUsagePath()))
						.replace("{help}", lu.getLocalized(locale, child.getHelpPath()))
					).append("\n");
			}
		} else {
			builder.append(lu.getLocalized(locale, "bot.help.command_info.usage_value").replace("{usage}", lu.getLocalized(locale, command.getUsagePath()))).append("\n");
		}
		return builder.substring(0, Math.min(1024, builder.length()));
	}

	private void sendHelp(SlashCommandEvent event, String filCat) {
		DiscordLocale userLocale = event.getUserLocale();
		EmbedBuilder builder = bot.getEmbedUtil().getEmbed()
			.setTitle(lu.getLocalized(userLocale, "bot.help.command_menu.title"))
			.setDescription(lu.getLocalized(userLocale, "bot.help.command_menu.description.command_value"));

		Category category = null;
		String fieldTitle = "";
		StringBuilder fieldValue = new StringBuilder();
		List<SlashCommand> commands = (
			filCat == null ? 
			event.getClient().getSlashCommands() : 
			event.getClient().getSlashCommands().stream().filter(cmd -> {
				if (cmd.getCategory() == null) return false;
				return cmd.getCategory().name().contentEquals(filCat);
			}).toList()
		);
		for (SlashCommand command : commands) {
			if (!command.isOwnerCommand() || bot.getCheckUtil().isBotOwner(event.getUser())) {
				if (!Objects.equals(category, command.getCategory())) {
					if (category != null) {
						builder.addField(fieldTitle, fieldValue.toString(), false);
					}
					category = command.getCategory();
					if (category == null) continue;
					fieldTitle = lu.getLocalized(userLocale, "bot.help.command_menu.categories."+category.name());
					fieldValue = new StringBuilder();
				}
				fieldValue.append("`/%s` - %s\n".formatted(command.getName(), command.getDescriptionLocalization().get(userLocale)));
			}
		}
		if (category != null) {
			builder.addField(fieldTitle, fieldValue.toString(), false);
		}

		User owner = Optional.ofNullable(event.getClient().getOwnerId()).map(id -> event.getJDA().getUserById(id)).orElse(null);

		if (owner != null) {
			fieldTitle = lu.getLocalized(userLocale, "bot.help.command_menu.description.support_title");
			fieldValue = new StringBuilder()
				.append(lu.getLocalized(userLocale, "bot.help.command_menu.description.support_value").replace("{owner_name}", "@"+owner.getName()));
			builder.addField(fieldTitle, fieldValue.toString(), false);
		}
		
		editHookEmbed(event, builder.build());
	}
}
