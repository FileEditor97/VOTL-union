package union.commands.verification;

import java.util.List;

import union.App;
import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

public class VerifyPanelCmd extends CommandBase {
	
	public VerifyPanelCmd(App bot) { 
		super(bot);
		this.name = "vfpanel";
		this.path = "bot.verification.vfpanel";
		this.children = new SlashCommand[]{new Create(bot), new Preview(bot), new SetText(bot)};
		this.botPermissions = new Permission[]{Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS};
		this.module = CmdModule.VERIFICATION;
		this.category = CmdCategory.VERIFICATION;
		this.accessLevel = CmdAccessLevel.ADMIN;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Create extends SlashCommand {
		
		public Create(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "create";
			this.path = "bot.verification.vfpanel.create";
			this.options = List.of(
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.help"), true)
					.setChannelTypes(ChannelType.TEXT)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			Guild guild = event.getGuild();
			GuildChannel channel = event.optGuildChannel("channel");
			if (channel == null ) {
				editError(event, path+".no_channel", "Received: No channel");
				return;
			}
			TextChannel tc = (TextChannel) channel;

			if (bot.getDBUtil().getVerifySettings(guild).getRoleId() == null) {
				editError(event, path+".no_role");
				return;
			}

			Button next = Button.primary("verify", lu.getLocalized(event.getGuildLocale(), path+".continue"));

			tc.sendMessageEmbeds(new EmbedBuilder()
				.setColor(bot.getDBUtil().getGuildSettings(guild).getColor())
				.setDescription(bot.getDBUtil().getVerifySettings(guild).getMainText())
				.setFooter(event.getGuild().getName(), event.getGuild().getIconUrl())
				.build()
			).addActionRow(next).queue();

			editHookEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").replace("{channel}", tc.getAsMention()))
				.build()
			);
		}

	}

	private class Preview extends SlashCommand {

		public Preview(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "preview";
			this.path = "bot.verification.vfpanel.preview";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			int color = bot.getDBUtil().getGuildSettings(guild).getColor();
			
			MessageEmbed main = new EmbedBuilder().setColor(color)
				.setDescription(bot.getDBUtil().getVerifySettings(guild).getMainText())
				.setFooter(event.getGuild().getName(), event.getGuild().getIconUrl())
				.build();

			MessageEmbed help = new EmbedBuilder().setColor(color)
				.setTitle(lu.getLocalized(event.getGuildLocale(), "bot.verification.embed.title"))
				.setDescription(lu.getLocalized(event.getGuildLocale(), "bot.verification.embed.description"))
				.addField(lu.getLocalized(event.getGuildLocale(), "bot.verification.embed.howto"), lu.getLocalized(event.getGuildLocale(), "bot.verification.embed.guide"), false)
				.build();
			
			event.replyEmbeds(main, help).setEphemeral(true).queue();
		}
	}

	private class SetText extends SlashCommand {
		
		public SetText(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "text";
			this.path = "bot.verification.vfpanel.text";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			TextInput main = TextInput.create("main", lu.getText(event, path+".main"), TextInputStyle.PARAGRAPH)
				.setPlaceholder("Verify here")
				.setRequired(false)
				.build();

			event.replyModal(Modal.create("vfpanel", lu.getText(event, path+".panel")).addActionRow(main).build()).queue();
		}

	}

}
