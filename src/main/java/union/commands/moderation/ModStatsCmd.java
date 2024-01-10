package union.commands.moderation;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import union.App;
import union.base.command.CooldownScope;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CaseType;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class ModStatsCmd extends CommandBase {
	
	public ModStatsCmd(App bot) {
		super(bot);
		this.name = "modstats";
		this.path = "bot.moderation.modstats";
		this.options = List.of(
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"))
		);
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.MOD;
		this.cooldown = 15;
		this.cooldownScope = CooldownScope.USER;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();

		Member mod = event.optMember("user", event.getMember());
		long guildId = event.getGuild().getIdLong();
		Map<Integer, Integer> count7 = bot.getDBUtil().cases.countCasesByMod(guildId, mod.getIdLong(), Instant.now().minus(7, ChronoUnit.DAYS));
		if (count7 == null) {
			editError(event, "errors.unknown", "Received null map");
			return;
		}
		Map<Integer, Integer> count30 = bot.getDBUtil().cases.countCasesByMod(guildId, mod.getIdLong(), Instant.now().minus(30, ChronoUnit.DAYS));
		if (count30 == null) {
			editError(event, "errors.unknown", "Received null map");
			return;
		}

		EmbedBuilder builder = new EmbedBuilder().setColor(Constants.COLOR_DEFAULT)
			.setAuthor(mod.getUser().getName(), mod.getAvatarUrl())
			.setTitle(lu.getText(event, path+".title"))
			.setFooter("ID: "+mod.getId())
			.setTimestamp(Instant.now());

		int strikes7 = count7.getOrDefault(CaseType.STRIKE_1.getType(), 0)+count7.getOrDefault(CaseType.STRIKE_2.getType(), 0)+count7.getOrDefault(CaseType.STRIKE_3.getType(), 0);
		int strikes30 = count30.getOrDefault(CaseType.STRIKE_1.getType(), 0)+count30.getOrDefault(CaseType.STRIKE_2.getType(), 0)+count30.getOrDefault(CaseType.STRIKE_3.getType(), 0);
		builder.addField(lu.getText(event, path+".strikes"), lu.getText(event, path+".last").formatted(strikes7, strikes30), false)
			.addField(lu.getText(event, path+".mutes"), lu.getText(event, path+".last").formatted(count7.getOrDefault(CaseType.MUTE.getType(), 0), count30.getOrDefault(CaseType.MUTE.getType(), 0)), false)
			.addField(lu.getText(event, path+".kicks"), lu.getText(event, path+".last").formatted(count7.getOrDefault(CaseType.KICK.getType(), 0), count30.getOrDefault(CaseType.KICK.getType(), 0)), false)
			.addField(lu.getText(event, path+".bans"), lu.getText(event, path+".last").formatted(count7.getOrDefault(CaseType.BAN.getType(), 0), count30.getOrDefault(CaseType.BAN.getType(), 0)), false)
			.addField(lu.getText(event, path+".total"), lu.getText(event, path+".last").formatted(count7.values().stream().reduce(0, Integer::sum), count30.values().stream().reduce(0, Integer::sum)), false);
		
		editHookEmbed(event, builder.build());
	}
}
