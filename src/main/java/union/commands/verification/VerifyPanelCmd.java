package union.commands.verification;

import java.util.List;

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
import union.utils.exception.CheckException;

public class VerifyPanelCmd extends CommandBase {
	
	public VerifyPanelCmd() {
		this.name = "vfpanel";
		this.path = "bot.verification.vfpanel";
		this.children = new SlashCommand[]{new Create(), new Preview(), new SetText()};
		this.botPermissions = new Permission[]{Permission.MESSAGE_SEND};
		this.module = CmdModule.VERIFICATION;
		this.category = CmdCategory.VERIFICATION;
		this.accessLevel = CmdAccessLevel.ADMIN;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Create extends SlashCommand {
		public Create() {
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
			try {
				bot.getCheckUtil().hasPermissions(
					event,
					new Permission[]{Permission.VIEW_CHANNEL, Permission.MANAGE_WEBHOOKS},
					channel
				);
			} catch (CheckException ex) {
				editMsg(event, ex.getEditData());
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

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").replace("{channel}", tc.getAsMention()))
				.build()
			);
		}
	}

	private class Preview extends SlashCommand {
		public Preview() {
			this.name = "preview";
			this.path = "bot.verification.vfpanel.preview";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();
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
			
			editEmbed(event, main, help);
		}
	}

	private class SetText extends SlashCommand {
		public SetText() {
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
