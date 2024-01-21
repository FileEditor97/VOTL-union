package union.utils.message;

import java.time.ZonedDateTime;

import jakarta.annotation.Nonnull;
import union.objects.constants.Constants;

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

	@Nonnull
	public EmbedBuilder getEmbed() {
		return new EmbedBuilder().setColor(Constants.COLOR_DEFAULT).setTimestamp(ZonedDateTime.now());
	}

	@Nonnull
	public EmbedBuilder getEmbed(IReplyCallback replyCallback) {
		return getEmbed().setFooter(
			lu.getText(replyCallback, "embed.footer").formatted(replyCallback.getUser().getName()),
			replyCallback.getUser().getEffectiveAvatarUrl()
		);
	}

	@Nonnull
	private EmbedBuilder getErrorEmbed(IReplyCallback replyCallback) {
		return getEmbed(replyCallback).setColor(Constants.COLOR_FAILURE).setTitle(lu.getText(replyCallback, "errors.title"));
	}

	@Nonnull
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

	@Nonnull
	public MessageEmbed getError(IReplyCallback replyCallback, @Nonnull String path) {
		return getError(replyCallback, path, null);
	}

	@Nonnull
	public MessageEmbed getError(IReplyCallback replyCallback, @Nonnull String path, String reason) {
		EmbedBuilder embedBuilder = getErrorEmbed(replyCallback)
			.setDescription(lu.getText(replyCallback, path));

		if (reason != null)
			embedBuilder.addField(
				lu.getText(replyCallback, "errors.additional"),
				reason,
				false
			);

		return embedBuilder.build();
	}

	@Nonnull
	public MessageCreateData createPermError(IReplyCallback replyCallback, Permission perm, boolean self) {
		return createPermError(replyCallback, null, perm, self);
	}

	@Nonnull
	public MessageCreateData createPermError(IReplyCallback replyCallback, GuildChannel channel, Permission perm, boolean self) {
		User user = replyCallback.getUser();
		if (perm.equals(Permission.MESSAGE_SEND)) {
			user.openPrivateChannel()
				.flatMap(ch -> ch.sendMessage(lu.getText(replyCallback, "errors.no_send_perm")))
				.queue();
			return MessageCreateData.fromContent("No MESSAGE_SEND perm"); //useles?
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

}
