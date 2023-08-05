package union.commands.owner;

import java.util.List;
import java.util.Map;

import union.App;
import union.commands.CommandBase;
import union.objects.command.SlashCommandEvent;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import org.codehaus.groovy.runtime.powerassert.PowerAssertionError;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;

public class EvalCmd extends CommandBase {
	
	public EvalCmd(App bot) {
		super(bot);
		this.name = "eval";
		this.path = "bot.owner.eval";
		this.options = List.of(
			new OptionData(OptionType.STRING, "code", lu.getText(path+".code.help"), true) 
			// Я блять ненавижу эту штуку
			// Нужно переделовать через modals, но для этого нужно вначале получить комманду от пользователя
			// позже выслать форму для заполения и только потом обработать ее
			// ............пиздец
		);
		this.category = CmdCategory.OWNER;
		this.ownerCommand = true;
		this.guildOnly = false;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply(true).queue();

		DiscordLocale userLocale = event.getUserLocale();

		String args = event.optString("code");
		if (args == null) {
			return;
		}
		args = args.trim();
		if (args.startsWith("```") && args.endsWith("```")) {
			if (args.startsWith("```java")) {
				args = args.substring(4);
			}
			args = args.substring(3, args.length() - 3);
		}

		Map<String, Object> variables = Map.of(
			"bot", bot,
			"event", event,
			"jda", event.getJDA(),
			"guild", (event.isFromGuild() ? event.getGuild() : "null"),
			"client", event.getClient(),
			"helper", (bot.getHelper() != null ? bot.getHelper() : "null")
		);

		Binding binding = new Binding(variables);
		GroovyShell shell = new GroovyShell(binding);

		long startTime = System.currentTimeMillis();

		try {
			String reply = String.valueOf(shell.evaluate(args));

			editHookEmbed(event, formatEvalEmbed(userLocale, args, reply,
				lu.getLocalized(userLocale, "bot.owner.eval.time")
					.replace("{time}", String.valueOf(System.currentTimeMillis() - startTime))
	 			, true));
		} catch (PowerAssertionError | Exception ex) {
			editHookEmbed(event, formatEvalEmbed(userLocale, args, ex.getMessage(),
				lu.getLocalized(userLocale, "bot.owner.eval.time")
					.replace("{time}", String.valueOf(System.currentTimeMillis() - startTime))
				, false));
		}
	}

	private MessageEmbed formatEvalEmbed(DiscordLocale locale, String input, String output, String footer, boolean success) {		
		EmbedBuilder embed = bot.getEmbedUtil().getEmbed()
			.setColor(success ? Constants.COLOR_SUCCESS : Constants.COLOR_FAILURE)
			.addField(lu.getLocalized(locale, "bot.owner.eval.input"), String.format(
				"```java\n"+
					"%s\n"+
					"```",
				input.substring(0, Math.min(input.length(), 1000))
				), false)
			.addField(lu.getLocalized(locale, "bot.owner.eval.output"), String.format(
				"```groovy\n"+
					"%s\n"+
					"```",
				output.substring(0, Math.min(output.length(), 1000))
				), false)
			.setFooter(footer, null);

		return embed.build();
	}
}