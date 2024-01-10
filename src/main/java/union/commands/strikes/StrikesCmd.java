package union.commands.strikes;

import java.util.Arrays;
import java.util.List;

import union.App;
import union.base.command.CooldownScope;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.utils.database.managers.CaseManager.CaseData;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.dv8tion.jda.internal.utils.tuple.Pair;

public class StrikesCmd extends CommandBase {
	
	public StrikesCmd(App bot) {
		super(bot);
		this.name = "strikes";
		this.options = List.of(
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"))
		);
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.STRIKES;
		this.accessLevel = CmdAccessLevel.MOD;
		this.cooldown = 5;
		this.cooldownScope = CooldownScope.GUILD;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply(true).queue();
		
		Member tm;
		if (event.hasOption("user")) {
			tm = event.optMember("user", event.getMember());
			if (!tm.equals(event.getMember()) && !bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.MOD)) {
				editError(event, path+".no_perms");
				return;
			}
		} else {
			tm = event.getMember();
		}

		Pair<Integer, String> strikeData = bot.getDBUtil().strike.getData(event.getGuild().getIdLong(), tm.getIdLong());
		if (strikeData == null) {
			editHookEmbed(event, bot.getEmbedUtil().getEmbed().setDescription(lu.getText(event, path+".no_active")).build());
			return;
		}
		List<String> cases = Arrays.asList(strikeData.getRight().split(";"));
		if (cases.get(0).isEmpty()) {
			editError(event, "errors.unknown", "Strikes data is empty");
			return;
		}

		StringBuffer buffer = new StringBuffer();
		cases.forEach(c -> {
			String[] args = c.split("-");
			Integer caseId = Integer.valueOf(args[0]);
			Integer strikeAmount = Integer.valueOf(args[1]);
			CaseData caseData = bot.getDBUtil().cases.getInfo(caseId);
			buffer.append("`%4d` %s | %s - %s\nBy: %s".formatted(
				caseId,
				getSquares(strikeAmount, caseData.getCaseType().getType()-20),
				bot.getMessageUtil().limitString(caseData.getReason(), 120),
				TimeFormat.DATE_SHORT.format(caseData.getTimeStart()),
				caseData.getModTag()
			));
		});
		
		editHookEmbed(event, bot.getEmbedUtil().getEmbed()
			.setTitle(lu.getText(event, path+".title").formatted(strikeData.getLeft(), tm.getUser().getName(), tm.getId()))
			.setDescription(buffer.toString())
			.build()
		);
	}

	private String getSquares(int active, int max) {
		return "🟥".repeat(active) + "🔲".repeat(max-active);
	}

}
