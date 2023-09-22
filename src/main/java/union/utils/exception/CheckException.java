package union.utils.exception;

import jakarta.annotation.Nonnull;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

public class CheckException extends Exception {

	@Nonnull
	private MessageCreateData data;
	
	public CheckException(@Nonnull MessageEmbed embed) {
		super();
		this.data = MessageCreateData.fromEmbeds(embed);
	}

	public CheckException(@Nonnull MessageCreateData data) {
		super();
		this.data = data;
	}

	@Nonnull
	public MessageCreateData getCreateData() {
		return data;
	}

	@Nonnull
	public MessageEditData getEditData() {
		return MessageEditData.fromCreateData(data);
	}
}
