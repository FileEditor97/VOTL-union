package union.utils.message;

import java.time.ZonedDateTime;

import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.jetbrains.annotations.NotNull;
import union.objects.constants.Constants;
import union.utils.file.lang.LocaleUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

public class EmbedUtil {

	private final LocaleUtil lu;

	public EmbedUtil(LocaleUtil localeUtil) {
		this.lu = localeUtil;
	}

	@NotNull
	public EmbedBuilder getEmbed(int color) {
		return new EmbedBuilder().setColor(color);
	}

	@NotNull
	public EmbedBuilder getEmbed() {
		return getEmbed(Constants.COLOR_DEFAULT);
	}

	@NotNull
	public EmbedBuilder getEmbed(IReplyCallback replyCallback) {
		return getEmbed().setFooter(
			lu.getText(replyCallback, "embed.footer").formatted(replyCallback.getUser().getName()),
			replyCallback.getUser().getEffectiveAvatarUrl()
		);

	}

	@NotNull
	private EmbedBuilder getErrorEmbed(IReplyCallback replyCallback) {
		return getEmbed().setColor(Constants.COLOR_FAILURE).setTitle(lu.getText(replyCallback, "errors.title"));
	}

	@NotNull
	private EmbedBuilder getPermErrorEmbed(IReplyCallback replyCallback, GuildChannel channel, Permission perm, boolean self) {
		EmbedBuilder embed = getErrorEmbed(replyCallback);
		String msg;
		if (self) {
			if (channel == null) {
				msg = lu.getText(replyCallback, "errors.missing_perms.self")
					.replace("{permission}", perm.getName());
			} else {
				msg = lu.getText(replyCallback, "errors.missing_perms.self_channel")
					.replace("{permission}", perm.getName())
					.replace("{channel}", channel.getAsMention());
			}
		} else {
			if (channel == null) {
				msg = lu.getText(replyCallback, "errors.missing_perms.other")
					.replace("{permission}", perm.getName());
			} else {
				msg = lu.getText(replyCallback, "errors.missing_perms.other_channel")
					.replace("{permission}", perm.getName())
					.replace("{channel}", channel.getAsMention());
			}
		}

		return embed.setDescription(msg);
	}

	@NotNull
	public MessageEmbed getError(IReplyCallback replyCallback, @NotNull String path) {
		return getError(replyCallback, path, null);
	}

	@NotNull
	public MessageEmbed getError(IReplyCallback replyCallback, @NotNull String path, String reason) {
		EmbedBuilder embedBuilder = getErrorEmbed(replyCallback)
			.setDescription(lu.getText(replyCallback, path));

		if (reason != null)
			embedBuilder.addField(
				lu.getText(replyCallback, "errors.additional"),
				MessageUtil.limitString(reason, 1024),
				false
			);

		return embedBuilder.build();
	}

	@NotNull
	public MessageCreateData createPermError(IReplyCallback replyCallback, Permission perm, boolean self) {
		return createPermError(replyCallback, null, perm, self);
	}

	@NotNull
	public MessageCreateData createPermError(IReplyCallback replyCallback, GuildChannel channel, Permission perm, boolean self) {
		User user = replyCallback.getUser();
		if (perm.equals(Permission.MESSAGE_SEND)) {
			user.openPrivateChannel()
				.flatMap(ch -> ch.sendMessage(lu.getText(replyCallback, "errors.no_send_perm")))
				.queue();
			return MessageCreateData.fromContent("No MESSAGE_SEND perm"); //useless?
		}
		MessageCreateBuilder mb = new MessageCreateBuilder();

		if (perm.equals(Permission.MESSAGE_EMBED_LINKS)) {
			if (channel == null) {
				mb.setContent(
					lu.getText(replyCallback, "errors.missing_perms.self")
						.replace("{permission}", perm.getName())
				);
			} else {
				mb.setContent(
					lu.getText(replyCallback, "errors.missing_perms.self_channel")
						.replace("{permission}", perm.getName())
						.replace("{channel}", channel.getAsMention())
				);
			}
		} else {
			mb.setEmbeds(getPermErrorEmbed(replyCallback, channel, perm, self).build());
		}
		return mb.build();
	}

	public void sendUnknownError(InteractionHook interactionHook, DiscordLocale locale, String reason) {
		interactionHook.sendMessageEmbeds(new EmbedBuilder().setColor(Constants.COLOR_FAILURE)
				.setTitle(lu.getLocalized(locale, "errors.title"))
				.setDescription(lu.getLocalized(locale, "errors.unknown"))
				.addField(lu.getLocalized(locale, "errors.additional"), MessageUtil.limitString(reason, 1024), false)
				.build())
			.setEphemeral(true)
			.queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_INTERACTION));
	}

}
