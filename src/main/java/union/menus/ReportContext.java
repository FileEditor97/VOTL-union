package union.menus;

import union.App;
import union.objects.CmdModule;
import union.objects.command.CooldownScope;
import union.objects.command.MessageContextMenu;
import union.objects.command.MessageContextMenuEvent;
import union.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public class ReportContext extends MessageContextMenu {
	
	public ReportContext(App bot) {
		this.bot = bot;
		this.lu = bot.getLocaleUtil();
		this.path = "menus.report";
		this.module = CmdModule.REPORT;
		this.cooldown = 20;
		this.cooldownScope = CooldownScope.USER_GUILD;
	}

	@Override
	protected void execute(MessageContextMenuEvent event) {
		String channelId = bot.getDBUtil().guild.getReportChannelId(event.getGuild().getId());
		if (channelId == null || event.getTarget().getAuthor().isBot()) {
			event.reply(Constants.FAILURE).setEphemeral(true).queue();
			return;
		}
		TextChannel channel = event.getGuild().getTextChannelById(channelId);
		if (channel == null) {
			event.reply(Constants.FAILURE).setEphemeral(true).queue();
			return;
		}
		event.deferReply(true).queue();

		MessageEmbed reportEmbed = getReportEmbed(event);
		Button delete = Button.danger("delete:%s:%s".formatted(event.getMessageChannel().getId(), event.getTarget().getId()), lu.getLocalized(event.getGuildLocale(), path+".delete")).withEmoji(Emoji.fromUnicode("🗑️"));
		Button link = Button.link(event.getTarget().getJumpUrl(), lu.getLocalized(event.getGuildLocale(), path+".link"));
		channel.sendMessageEmbeds(reportEmbed).addActionRow(link, delete).queue();

		event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed()
			.setDescription(lu.getText(event, path+".done"))
			.build()
		).queue();
	}

	private MessageEmbed getReportEmbed(MessageContextMenuEvent event) {
		String content = event.getTarget().getContentStripped();
		if (content.length() > 1024) content = content.substring(0, Math.min(1020, content.length()))+"...";
		return new EmbedBuilder().setColor(Constants.COLOR_WARNING)
			.setTitle(lu.getLocalized(event.getGuildLocale(), path+".title"))
			.addField(lu.getLocalized(event.getGuildLocale(), path+".user"), event.getTarget().getAuthor().getAsMention(), true)
			.addField(lu.getLocalized(event.getGuildLocale(), path+".channel"), event.getMessageChannel().getAsMention(), true)
			.addField(lu.getLocalized(event.getGuildLocale(), path+".complain"), event.getMember().getAsMention(), false)
			.addField(lu.getLocalized(event.getGuildLocale(), path+".content"), content, false)
			.setFooter("Message ID: %s".formatted(event.getTarget().getId()))
			.build();
	}


}
