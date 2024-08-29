package union.commands.strikes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import union.base.command.CooldownScope;
import union.base.command.SlashCommandEvent;
import union.base.waiter.EventWaiter;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;
import union.utils.database.managers.CaseManager.CaseData;
import union.utils.message.MessageUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import union.utils.message.TimeUtil;

public class DeleteStikeCmd extends CommandBase {

	private final EventWaiter waiter;
	
	public DeleteStikeCmd(EventWaiter waiter) {
		this.name = "delstrike";
		this.path = "bot.moderation.delstrike";
		this.options = List.of(
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true)
		);
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.STRIKES;
		this.accessLevel = CmdAccessLevel.MOD;
		this.cooldown = 10;
		this.cooldownScope = CooldownScope.GUILD;
		this.waiter = waiter;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();

		User tu = event.optUser("user");
		if (tu == null) {
			editError(event, path+".not_found");
			return;
		}
		if (tu.isBot()) {
			editError(event, path+".is_bot");
			return;
		}

		Pair<Integer, String> strikeData = bot.getDBUtil().strike.getData(event.getGuild().getIdLong(), tu.getIdLong());
		if (strikeData == null) {
			editHookEmbed(event, bot.getEmbedUtil().getEmbed().setDescription(lu.getText(event, path+".no_strikes")).build());
			return;
		}
		String[] cases = strikeData.getRight().split(";");
		if (cases[0].isEmpty()) {
			editError(event, "errors.unknown", "Strikes data is empty");
			return;
		}
		List<SelectOption> options = buildOptions(cases);
		if (options.isEmpty()) {
			editError(event, "errors.unknown", "Strikes options are empty");
			return;
		}
		StringSelectMenu caseSelectMenu = StringSelectMenu.create("delete-strike")
			.setPlaceholder(lu.getText(event, path+".select_strike"))
			.addOptions(options)
			.build();
		event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed()
			.setTitle(lu.getText(event, path+".select_title").formatted(tu.getName(), strikeData.getLeft()))
			.setFooter("User ID: "+tu.getId())
			.build()
		).setActionRow(caseSelectMenu).queue(msg -> {
			waiter.waitForEvent(
				StringSelectInteractionEvent.class,
				e -> e.getMessageId().equals(msg.getId()) && e.getUser().equals(event.getUser()),
				selectAction -> strikeSelected(selectAction, msg, cases, tu),
				60,
				TimeUnit.SECONDS,
				() -> msg.editMessageComponents(ActionRow.of(
					caseSelectMenu.createCopy().setPlaceholder(lu.getText(event, "errors.timed_out")).setDisabled(true).build()
				)).queue()
			);
		});
	}

	private void strikeSelected(StringSelectInteractionEvent event, Message msg, String[] strikesInfoArray, User tu) {
		event.deferEdit().queue();

		List<String> strikesInfo = new ArrayList<>(List.of(strikesInfoArray));
		String[] selected = event.getValues().get(0).split("-");
		Integer caseId = Integer.valueOf(selected[0]);
		
		CaseData caseData = bot.getDBUtil().cases.getInfo(caseId);
		if (!caseData.isActive()) {
			msg.editMessageEmbeds(bot.getEmbedUtil().getError(event, "errors.unknown", "Case is not active (strike can't be removed)"))
				.setComponents().queue();
			bot.getAppLogger().error("At DeleteStrike: Case inside strikes info is not active. Unable to remove. Perform manual removal.\nCase ID: {}", caseId);
			return;
		}

		int activeAmount = Integer.parseInt(selected[1]);
		if (activeAmount == 1) {
			long guildId = event.getGuild().getIdLong();
			// As only one strike remains - delete case from strikes data and set case inactive
			
			strikesInfo.remove(event.getValues().get(0));

			bot.getDBUtil().cases.setInactive(caseId);
			if (strikesInfo.isEmpty())
				bot.getDBUtil().strike.removeGuildUser(guildId, tu.getIdLong());
			else
				bot.getDBUtil().strike.removeStrike(guildId, tu.getIdLong(),
					Instant.now().plus(bot.getDBUtil().getGuildSettings(guildId).getStrikeExpires(), ChronoUnit.DAYS),
					1, String.join(";", strikesInfo)
				);
			
			// Send dm
			tu.openPrivateChannel().queue(pm -> {
				MessageEmbed embed = bot.getModerationUtil().getDelstrikeEmbed(1, event.getGuild(), event.getUser());
				if (embed == null) return;
				pm.sendMessageEmbeds(embed).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
			});
			// Log
			bot.getLogger().mod.onStrikeDeleted(event, tu, caseId, 1, activeAmount);
			// Reply
			msg.editMessageEmbeds(bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done_one").formatted(caseData.getReason(), tu.getName()))
				.build()
			).setComponents().queue();
		} else {
			// Provide user with options, delete 1,2 or 3(maximum) strikes for user
			int max = caseData.getCaseType().getType()-20;
			List<Button> buttons = new ArrayList<>();
			for (int i=1; i<activeAmount; i++) {
				buttons.add(Button.secondary(caseId+"-"+i, getSquares(activeAmount, i, max)));
			}
			buttons.add(Button.secondary(caseId+"-"+activeAmount,
				lu.getText(event, path+".button_all")+" "+getSquares(activeAmount, activeAmount, max)));
			
			msg.editMessageEmbeds(bot.getEmbedUtil().getEmbed()
				.setTitle(lu.getText(event, path+".button_title"))
				.build()
			).setActionRow(buttons).queue(msgN -> {
				waiter.waitForEvent(
					ButtonInteractionEvent.class,
					e -> e.getMessageId().equals(msg.getId()) && e.getUser().equals(event.getUser()),
					buttonAction -> buttonPressed(buttonAction, msgN, strikesInfo, tu, activeAmount),
					30,
					TimeUnit.SECONDS,
					() -> msg.editMessageEmbeds(new EmbedBuilder(msgN.getEmbeds().get(0)).appendDescription("\n\n"+lu.getText(event, "errors.timed_out")).build())
						.setComponents().queue()
				);
			});
		}
	}

	private void buttonPressed(ButtonInteractionEvent event, Message msg, List<String> cases, User tu, int activeAmount) {
		event.deferEdit().queue();
		String[] value = event.getComponentId().split("-");
		int caseId = Integer.parseInt(value[0]);

		CaseData caseData = bot.getDBUtil().cases.getInfo(caseId);
		if (!caseData.isActive()) {
			msg.editMessageEmbeds(bot.getEmbedUtil().getError(event, "errors.unknown", "Case is not active (strike can't be removed)"))
				.setComponents().queue();
			bot.getAppLogger().error("At DeleteStrike: Case inside strikes info is not active. Unable to remove. Perform manual removal.\nCase ID: {}", caseId);
			return;
		}

		long guildId = event.getGuild().getIdLong();
		int removeAmount = Integer.parseInt(value[1]);
		if (removeAmount == activeAmount) {
			
			// Delete all strikes, set case inactive
			cases.remove(event.getComponentId());
			bot.getDBUtil().cases.setInactive(caseId);
			if (cases.isEmpty())
				bot.getDBUtil().strike.removeGuildUser(guildId, tu.getIdLong());
			else
				bot.getDBUtil().strike.removeStrike(guildId, tu.getIdLong(),
					Instant.now().plus(bot.getDBUtil().getGuildSettings(guildId).getStrikeExpires(), ChronoUnit.DAYS),
					removeAmount, String.join(";", cases)
				);
		} else {
			// Delete selected amount of strikes (not all)
			Collections.replaceAll(cases, caseId+"-"+activeAmount, caseId+"-"+(activeAmount-removeAmount));
			bot.getDBUtil().strike.removeStrike(guildId, tu.getIdLong(),
				Instant.now().plus(bot.getDBUtil().getGuildSettings(guildId).getStrikeExpires(), ChronoUnit.DAYS),
				removeAmount, String.join(";", cases)
			);
		}
		// Send dm
		tu.openPrivateChannel().queue(pm -> {
			MessageEmbed embed = bot.getModerationUtil().getDelstrikeEmbed(removeAmount, event.getGuild(), event.getUser());
			if (embed == null) return;
			pm.sendMessageEmbeds(embed).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
		});
		// Log
		bot.getLogger().mod.onStrikeDeleted(event, tu, caseId, removeAmount, activeAmount);
		// Reply
		msg.editMessageEmbeds(bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
			.setDescription(lu.getText(event, path+".done").formatted(removeAmount, activeAmount, caseData.getReason(), tu.getName()))
			.build()
		).setComponents().queue();
	}

	private List<SelectOption> buildOptions(String[] cases) {
		List<SelectOption> options = new ArrayList<>();
		for (String c : cases) {
			String[] args = c.split("-");
			int caseId = Integer.parseInt(args[0]);
			int strikeAmount = Integer.parseInt(args[1]);
			CaseData caseData = bot.getDBUtil().cases.getInfo(caseId);
			options.add(SelectOption.of(
				"%s | %s".formatted(getSquares(strikeAmount, caseData.getCaseType().getType()-20), MessageUtil.limitString(caseData.getReason(), 50)),
				caseId+"-"+strikeAmount
			).withDescription(TimeUtil.timeToString(caseData.getTimeStart())+" | By: "+caseData.getModTag()));
		}
		return options;
	}

	private String getSquares(int active, int max) {
		return "🟥".repeat(active) + "🔲".repeat(max-active);
	}

	private String getSquares(int active, int delete, int max) {
		return "🟩".repeat(delete) + "🟥".repeat(active-delete) + "🔲".repeat(max-active);
	}

}
