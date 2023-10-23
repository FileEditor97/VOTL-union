package union.commands;

import union.App;
import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import jakarta.annotation.Nonnull;

public abstract class CommandBase extends SlashCommand {
	
	public CommandBase(App bot) {
		this.bot = bot;
		this.lu = bot.getLocaleUtil();
	}

	// reply to event
	public final void createReply(SlashCommandEvent event, @Nonnull String msg) {
		event.reply(msg).setEphemeral(true).queue();
	}

	public final void createReply(SlashCommandEvent event, @Nonnull MessageCreateData data) {
		event.reply(data).setEphemeral(true).queue();
	}

	public final void createReply(SlashCommandEvent event, Boolean ephemeral, @Nonnull String msg) {
		event.reply(msg).setEphemeral(true).queue();
	}

	public final void createReply(SlashCommandEvent event, Boolean ephemeral, @Nonnull MessageCreateData data) {
		event.reply(data).setEphemeral(ephemeral).queue();
	}

	public final void createReplyEmbed(SlashCommandEvent event, @Nonnull MessageEmbed... embeds) {
		event.deferReply(true).addEmbeds(embeds).queue();
	}

	public final void createReplyEmbed(SlashCommandEvent event, Boolean ephemeral, @Nonnull MessageEmbed... embeds) {
		event.deferReply(ephemeral).addEmbeds(embeds).queue();
	}

	// Error
	public final void createError(SlashCommandEvent event, @Nonnull String path) {
		createReplyEmbed(event, bot.getEmbedUtil().getError(event, path));
	}

	public final void createError(SlashCommandEvent event, @Nonnull String path, String reason) {
		createReplyEmbed(event, bot.getEmbedUtil().getError(event, path, reason));
	}

	// PermError
	public final void createPermError(SlashCommandEvent event, Permission perm, Boolean self) {
		createReply(event, bot.getEmbedUtil().createPermError(event, perm, self));
	}

	public final void createPermError(SlashCommandEvent event, TextChannel channel, Permission perm, Boolean self) {
		createReply(event, bot.getEmbedUtil().createPermError(event, channel, perm, self));
	}
	

	// editOriginal with InteractionHook
	public final void editHook(SlashCommandEvent event, @Nonnull String msg) {
		event.getHook().editOriginal(msg).queue();
	}

	public final void editHook(SlashCommandEvent event, @Nonnull MessageEditData data) {
		event.getHook().editOriginal(data).queue();
	}

	public final void editHookEmbed(SlashCommandEvent event, @Nonnull MessageEmbed... embeds) {
		event.getHook().editOriginalEmbeds(embeds).queue();
	}

	// Error
	public final void editError(SlashCommandEvent event, @Nonnull String path) {
		editHookEmbed(event, bot.getEmbedUtil().getError(event, path));
	}

	public final void editError(SlashCommandEvent event, @Nonnull String path, String reason) {
		editHookEmbed(event, bot.getEmbedUtil().getError(event, path, reason));
	}

	// PermError
	public final void editPermError(SlashCommandEvent event, Permission perm, Boolean self) {
		editHook(event, MessageEditData.fromCreateData(bot.getEmbedUtil().createPermError(event, perm, self)));
	}

	public final void editPermError(SlashCommandEvent event, TextChannel channel, Permission perm, Boolean self) {
		editHook(event, MessageEditData.fromCreateData(bot.getEmbedUtil().createPermError(event, channel, perm, self)));
	}
}
