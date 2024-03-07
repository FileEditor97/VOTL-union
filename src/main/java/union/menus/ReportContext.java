package union.menus;

import union.App;
import union.base.command.CooldownScope;
import union.base.command.MessageContextMenu;
import union.base.command.MessageContextMenuEvent;
import union.objects.CmdModule;
import union.objects.constants.Constants;
import union.utils.message.MessageUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public class ReportContext extends MessageContextMenu {
	
	public ReportContext(App bot) {
		this.bot = bot;
		this.lu = bot.getLocaleUtil();
		this.name = "report";
		this.path = "menus.report";
		this.module = CmdModule.REPORT;
		this.cooldown = 60;
		this.cooldownScope = CooldownScope.USER_GUILD;
	}

	@Override
	protected void execute(MessageContextMenuEvent event) {
		event.deferReply(true).queue();
	
		event.getGuild().retrieveMember(event.getTarget().getAuthor()).queue(member -> {
			Long channelId = bot.getDBUtil().getGuildSettings(event.getGuild()).getReportChannelId();
			if (channelId == null || member.getUser().isBot() || member.hasPermission(Permission.ADMINISTRATOR)) {
				event.getHook().editOriginal(Constants.FAILURE).queue();
				return;
			}
			TextChannel channel = event.getGuild().getTextChannelById(channelId);
			if (channel == null) {
				event.getHook().editOriginal(Constants.FAILURE).queue();
				return;
			}

			MessageEmbed reportEmbed = getReportEmbed(event);
			Button delete = Button.danger("delete:%s:%s".formatted(event.getMessageChannel().getId(), event.getTarget().getId()), lu.getLocalized(event.getGuildLocale(), path+".delete")).withEmoji(Emoji.fromUnicode("🗑️"));
			Button link = Button.link(event.getTarget().getJumpUrl(), lu.getLocalized(event.getGuildLocale(), path+".link"));
			channel.sendMessageEmbeds(reportEmbed).addActionRow(link, delete).queue();

			event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed()
				.setDescription(lu.getText(event, path+".done"))
				.build()
			).queue();
		}, failure -> {
			event.getHook().editOriginal(Constants.FAILURE).queue();
		});
		
	}

	private MessageEmbed getReportEmbed(MessageContextMenuEvent event) {
		String content = MessageUtil.limitString(event.getTarget().getContentStripped(), 1024);
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
