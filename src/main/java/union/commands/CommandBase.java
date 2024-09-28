package union.commands;

import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.objects.annotation.NotNull;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.util.concurrent.TimeUnit;

public abstract class CommandBase extends SlashCommand {

	// edit message with InteractionHook
	public final void editMsg(SlashCommandEvent event, @NotNull String msg) {
		event.getHook().editOriginal(msg).queue();
	}

	public final void editMsg(SlashCommandEvent event, @NotNull MessageEditData data) {
		event.getHook().editOriginal(data).queue();
	}

	public final void editEmbed(SlashCommandEvent event, @NotNull MessageEmbed... embeds) {
		event.getHook().editOriginalEmbeds(embeds).queue();
	}

	// Error

	public final void editError(SlashCommandEvent event, @NotNull MessageEditData data) {
		event.getHook().editOriginal(data)
			.queue(msg -> {
				if (!msg.isEphemeral())
					msg.delete().queueAfter(30, TimeUnit.SECONDS);
			});
	}

	public final void editError(SlashCommandEvent event, @NotNull MessageEmbed embed) {
		editError(event, new MessageEditBuilder()
			.setContent(lu.getText(event, "misc.temp_msg"))
			.setEmbeds(embed)
			.build()
		);
	}

	public final void editError(SlashCommandEvent event, @NotNull String path) {
		editError(event, path, null);
	}

	public final void editError(SlashCommandEvent event, @NotNull String path, String reason) {
		editError(event, bot.getEmbedUtil().getError(event, path, reason));
	}

	public final void editErrorOther(SlashCommandEvent event, String reason) {
		editError(event, bot.getEmbedUtil().getError(event, "errors.error", reason));
	}

	public final void editErrorUnknown(SlashCommandEvent event, String reason) {
		editError(event, bot.getEmbedUtil().getError(event, "errors.unknown", reason));
	}

	// PermError
	public final void editPermError(SlashCommandEvent event, Permission perm, boolean self) {
		editError(event, MessageEditData.fromCreateData(bot.getEmbedUtil().createPermError(event, perm, self)));
	}
}
