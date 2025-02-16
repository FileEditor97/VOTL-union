package union.commands.guild;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.NotNull;
import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.base.waiter.EventWaiter;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.Emotes;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

public class ModuleCmd extends CommandBase {
	
	private static EventWaiter waiter;
	
	public ModuleCmd(EventWaiter waiter) {
		this.name = "module";
		this.path = "bot.guild.module";
		this.children = new SlashCommand[]{new Show(), new Disable(), new Enable()};
		this.category = CmdCategory.GUILD;
		this.accessLevel = CmdAccessLevel.OWNER;
		ModuleCmd.waiter = waiter;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Show extends SlashCommand {
		public Show() {
			this.name = "show";
			this.path = "bot.guild.module.show";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();
			long guildId = event.getGuild().getIdLong();

			StringBuilder builder = new StringBuilder();
			Set<CmdModule> disabled = getModules(guildId, false);
			for (CmdModule sModule : CmdModule.values()) {
				builder.append(format(lu.getText(event, sModule.getPath()), disabled.contains(sModule)))
					.append("\n");
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed()
				.setTitle(lu.getText(event, path+".embed.title"))
				.setDescription(lu.getText(event, path+".embed.value"))
				.addField(lu.getText(event, path+".embed.field"), builder.toString(), false)
				.build());
		}

		@NotNull
		private String format(String sModule, boolean check) {
			return (check ? Emotes.CROSS_C : Emotes.CHECK_C).getEmote() + " | " + sModule;
		}
	}

	private class Disable extends SlashCommand {
		public Disable() {
			this.name = "disable";
			this.path = "bot.guild.module.disable";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();
			InteractionHook hook = event.getHook();

			long guildId = event.getGuild().getIdLong();

			EmbedBuilder embed = bot.getEmbedUtil().getEmbed()
				.setTitle(lu.getText(event, path+".embed_title"));

			Set<CmdModule> enabled = getModules(guildId, true);
			if (enabled.isEmpty()) {
				embed.setDescription(lu.getText(event, path+".none"))
					.setColor(Constants.COLOR_FAILURE);
				editEmbed(event, embed.build());
				return;
			}

			embed.setDescription(lu.getText(event, path+".embed_value"));
			StringSelectMenu menu = StringSelectMenu.create("disable-module")
				.setPlaceholder(lu.getText(event, path+".select"))
				.setRequiredRange(1, 1)
				.addOptions(enabled.stream()
						.map(sModule -> SelectOption.of(lu.getText(event, sModule.getPath()), sModule.toString()))
						.toList()
				)
				.build();

			hook.editOriginalEmbeds(embed.build()).setActionRow(menu).queue(msg -> {
				waiter.waitForEvent(
					StringSelectInteractionEvent.class,
					e -> msg.getIdLong() == e.getMessageIdLong(),
					actionEvent -> {
						actionEvent.deferEdit().queue();

						CmdModule sModule = CmdModule.valueOf(actionEvent.getSelectedOptions().get(0).getValue());
						if (bot.getDBUtil().getGuildSettings(guildId).isDisabled(sModule)) {
							hook.editOriginalEmbeds(bot.getEmbedUtil().getError(event, path+".already")).setComponents().queue();
							return;
						}
						// set new data
						final int newData = bot.getDBUtil().getGuildSettings(guildId).getModulesOff() + sModule.getValue();
						if (!bot.getDBUtil().guildSettings.setModulesDisabled(guildId, newData)) {
							editErrorUnknown(event, "Database error.");
							return;
						}
						// Send reply
						hook.editOriginalEmbeds(bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
							.setTitle(lu.getText(event, path+".done").replace("{module}", lu.getText(event, sModule.getPath())))
							.build()
						).setComponents().queue();
						// Log
						bot.getLogger().botLog.onModuleDisabled(event.getGuild(), event.getUser(), sModule);
					},
					30,
					TimeUnit.SECONDS,
					() -> {
						hook.editOriginalComponents(
							ActionRow.of(menu.createCopy().setPlaceholder(lu.getText(event, "errors.timed_out")).setDisabled(true).build())
						).queue();
					}
				);
			});
		}
	}

	private class Enable extends SlashCommand {
		public Enable() {
			this.name = "enable";
			this.path = "bot.guild.module.enable";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();
			InteractionHook hook = event.getHook();

			long guildId = event.getGuild().getIdLong();

			EmbedBuilder embed = bot.getEmbedUtil().getEmbed()
				.setTitle(lu.getText(event, path+".embed_title"));

			Set<CmdModule> enabled = getModules(guildId, false);
			if (enabled.isEmpty()) {
				embed.setDescription(lu.getText(event, path+".none"))
					.setColor(Constants.COLOR_FAILURE);
				editEmbed(event, embed.build());
				return;
			}

			embed.setDescription(lu.getText(event, path+".embed_value"));
			StringSelectMenu menu = StringSelectMenu.create("enable-module")
				.setPlaceholder(lu.getText(event, path+".select"))
				.setRequiredRange(1, 1)
				.addOptions(enabled.stream()
						.map(sModule -> SelectOption.of(lu.getText(event, sModule.getPath()), sModule.toString()))
						.toList()
				)
				.build();

			hook.editOriginalEmbeds(embed.build()).setActionRow(menu).queue(msg -> {
				waiter.waitForEvent(
					StringSelectInteractionEvent.class,
					e -> msg.getIdLong() == e.getMessageIdLong(),
					actionEvent -> {
						actionEvent.deferEdit().queue();

						CmdModule sModule = CmdModule.valueOf(actionEvent.getSelectedOptions().get(0).getValue());
						if (!bot.getDBUtil().getGuildSettings(guildId).isDisabled(sModule)) {
							hook.editOriginalEmbeds(bot.getEmbedUtil().getError(event, path+".already")).setComponents().queue();
							return;
						}
						// set new data
						final int newData = bot.getDBUtil().getGuildSettings(guildId).getModulesOff() - sModule.getValue();
						if (!bot.getDBUtil().guildSettings.setModulesDisabled(guildId, newData)) {
							editErrorUnknown(event, "Database error.");
							return;
						}
						// Send reply
						hook.editOriginalEmbeds(bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
							.setTitle(lu.getText(event, path+".done").replace("{module}", lu.getText(event, sModule.getPath())))
							.build()
						).setComponents().queue();
						// Log
						bot.getLogger().botLog.onModuleEnabled(event.getGuild(), event.getUser(), sModule);
					},
					30,
					TimeUnit.SECONDS,
					() -> {
						hook.editOriginalComponents(
							ActionRow.of(menu.createCopy().setPlaceholder(lu.getText(event, "errors.timed_out")).setDisabled(true).build())
						).queue();
					}
				);
			});
		}
	}

	private Set<CmdModule> getModules(long guildId, boolean on) {
		Set<CmdModule> disabled = bot.getDBUtil().getGuildSettings(guildId).getDisabledModules();
		if (on) {
			Set<CmdModule> modules = new HashSet<>(Arrays.asList(CmdModule.values()));
			modules.removeAll(disabled);
			return modules;
		} else
			return disabled;
	}

}
