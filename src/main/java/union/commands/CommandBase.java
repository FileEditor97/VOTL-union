package union.commands;

import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.objects.annotation.NotNull;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.util.concurrent.TimeUnit;

public abstract class CommandBase extends SlashCommand {

	// reply to event
	public final void createReply(SlashCommandEvent event, @NotNull String msg) {
		event.reply(msg).setEphemeral(true).queue();
	}

	public final void createReply(SlashCommandEvent event, @NotNull MessageCreateData data) {
		event.reply(data).setEphemeral(true).queue();
	}

	public final void createReply(SlashCommandEvent event, boolean ephemeral, @NotNull String msg) {
		event.reply(msg).setEphemeral(ephemeral).queue();
	}

	public final void createReply(SlashCommandEvent event, boolean ephemeral, @NotNull MessageCreateData data) {
		event.reply(data).setEphemeral(ephemeral).queue();
	}

	public final void createReplyEmbed(SlashCommandEvent event, @NotNull MessageEmbed... embeds) {
		event.deferReply(true).addEmbeds(embeds).queue();
	}

	public final void createReplyEmbed(SlashCommandEvent event, boolean ephemeral, @NotNull MessageEmbed... embeds) {
		event.deferReply(ephemeral).addEmbeds(embeds).queue();
	}

	// Error
	public final void createError(SlashCommandEvent event, @NotNull String path) {
		createReplyEmbed(event, bot.getEmbedUtil().getError(event, path));
	}

	public final void createError(SlashCommandEvent event, @NotNull String path, String reason) {
		createReplyEmbed(event, bot.getEmbedUtil().getError(event, path, reason));
	}

	// PermError
	public final void createPermError(SlashCommandEvent event, Permission perm, boolean self) {
		createReply(event, bot.getEmbedUtil().createPermError(event, perm, self));
	}

	public final void createPermError(SlashCommandEvent event, TextChannel channel, Permission perm, boolean self) {
		createReply(event, bot.getEmbedUtil().createPermError(event, channel, perm, self));
	}
	

	// editOriginal with InteractionHook
	public final void editHook(SlashCommandEvent event, @NotNull String msg) {
		event.getHook().editOriginal(msg).queue();
	}

	public final void editHook(SlashCommandEvent event, @NotNull MessageEditData data) {
		event.getHook().editOriginal(data).queue();
	}

	public final void editHookEmbed(SlashCommandEvent event, @NotNull MessageEmbed... embeds) {
		event.getHook().editOriginalEmbeds(embeds).queue();
	}

	// Error
	public final void editError(SlashCommandEvent event, @NotNull String path) {
		editHookEmbed(event, bot.getEmbedUtil().getError(event, path));
	}

	public final void editError(SlashCommandEvent event, @NotNull String path, String reason) {
		editHookEmbed(event, bot.getEmbedUtil().getError(event, path, reason));
	}

	public final void editErrorOther(SlashCommandEvent event, String reason) {
		editHookEmbed(event, bot.getEmbedUtil().getError(event, "errors.error", reason));
	}

	public final void editErrorUnknown(SlashCommandEvent event, String reason) {
		editHookEmbed(event, bot.getEmbedUtil().getError(event, "errors.unknown", reason));
	}

	public final void editErrorDeletable(SlashCommandEvent event, @NotNull String path) {
		editErrorDeletable(event, path, null);
	}

	public final void editErrorDeletable(SlashCommandEvent event, @NotNull String path, String reason) {
		event.getHook().editOriginal(lu.getText(event, "misc.temp_msg"))
			.setEmbeds(bot.getEmbedUtil().getError(event, path, reason))
			.queue(msg -> msg.delete().queueAfter(10, TimeUnit.SECONDS));
	}

	public final void editErrorUnknownDeletable(SlashCommandEvent event, String reason) {
		event.getHook().editOriginal(lu.getText(event, "misc.temp_msg"))
			.setEmbeds(bot.getEmbedUtil().getError(event, "errors.unknown", reason))
			.queue(msg -> msg.delete().queueAfter(10, TimeUnit.SECONDS));
	}

	// PermError
	public final void editPermError(SlashCommandEvent event, Permission perm, boolean self) {
		editHook(event, MessageEditData.fromCreateData(bot.getEmbedUtil().createPermError(event, perm, self)));
	}

	public final void editPermError(SlashCommandEvent event, TextChannel channel, Permission perm, boolean self) {
		editHook(event, MessageEditData.fromCreateData(bot.getEmbedUtil().createPermError(event, channel, perm, self)));
	}
}
