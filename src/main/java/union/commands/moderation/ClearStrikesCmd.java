package union.commands.moderation;

import java.util.List;

import union.App;
import union.base.command.CooldownScope;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.internal.utils.tuple.Pair;

public class ClearStrikesCmd extends CommandBase {

	public ClearStrikesCmd(App bot) {
		super(bot);
		this.name = "clearstrikes";
		this.path = "bot.moderation.clearstrikes";
		this.options = List.of(
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true)
		);
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.STRIKES;
		this.accessLevel = CmdAccessLevel.ADMIN;
		this.cooldown = 10;
		this.cooldownScope = CooldownScope.GUILD;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();

		Member tm = event.optMember("user");
		if (tm == null) {
			editError(event, path+".not_found");
			return;
		}
		if (tm.getUser().isBot()) {
			editError(event, path+".is_bot");
			return;
		}

		long guildId = event.getGuild().getIdLong();
		Pair<Integer, String> strikeData = bot.getDBUtil().strike.getData(guildId, tm.getIdLong());
		if (strikeData == null) {
			editHookEmbed(event, bot.getEmbedUtil().getEmbed().setDescription(lu.getText(event, path+".no_strikes")).build());
			return;
		}
		int activeCount = strikeData.getLeft();
		// Clear strike DB
		bot.getDBUtil().strike.removeGuildUser(guildId, tm.getIdLong());
		// Set all strikes cases inactive
		bot.getDBUtil().cases.setInactiveStrikeCases(guildId, tm.getIdLong());
		// Reply
		editHookEmbed(event, bot.getEmbedUtil().getEmbed()
			.setColor(Constants.COLOR_SUCCESS)
			.setDescription(lu.getText(event, path+".done").formatted(activeCount, tm.getUser().getName()))
			.build()
		);
	}
	
}
