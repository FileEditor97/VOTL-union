package union.commands.image;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import net.dv8tion.jda.api.utils.FileUpload;
import union.App;
import union.base.command.CooldownScope;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.utils.imagegen.UserBackground;
import union.utils.imagegen.UserBackgroundHandler;
import union.utils.imagegen.renders.UserBackgroundRender;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

public class UserProfileCmd extends CommandBase {
	public UserProfileCmd() {
		this.name = "profile";
		this.path = "bot.image.profile";
		this.category = CmdCategory.IMAGE;
		this.module = CmdModule.IMAGE;
		this.options = List.of(
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help")),
			new OptionData(OptionType.INTEGER, "id", lu.getText(path+".id.help"))
				.setRequiredRange(0, 100)
		);
		this.cooldown = 30;
		this.cooldownScope = CooldownScope.USER;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();

		User target = event.optUser("user", event.getUser());
		if (target.isBot()) {
			editError(event, path+".bad_user");
			return;
		}

		int selectedBackgroundId = event.optInteger("id", 0);

		sendBackgroundMessage(event, target, selectedBackgroundId);
	}

	private void sendBackgroundMessage(
		SlashCommandEvent event,
		User target,
		int backgroundId
	) {
		UserBackground background = UserBackgroundHandler.getInstance().fromId(backgroundId);
		if (background == null) {
			editError(event, path+".failed", "Background not found");
			return;
		}

		UserBackgroundRender render = new UserBackgroundRender(target)
			.setBackground(background);

		String attachmentName = "%s-%s-user-bg.png".formatted(event.getGuild().getId(), target.getId());

		EmbedBuilder embed = new EmbedBuilder()
			.setImage("attachment://" + attachmentName)
			.setColor(App.getInstance().getDBUtil().getGuildSettings(event.getGuild()).getColor());

		try {
			event.getHook().editOriginalEmbeds(embed.build()).setFiles(FileUpload.fromData(
				new ByteArrayInputStream(render.renderToBytes()),
				attachmentName
			)).queue();
		} catch (IOException e) {
			App.getInstance().getAppLogger().error("Failed to generate the rank background: {}", e.getMessage(), e);
			editError(event, path+".failed", "Rendering exception");
		}
	}
}
