package union.utils.exception;

import union.objects.annotation.NotNull;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

public class CheckException extends Exception {

	@NotNull
	private MessageCreateData data;
	
	public CheckException(@NotNull MessageEmbed embed) {
		super();
		this.data = MessageCreateData.fromEmbeds(embed);
	}

	public CheckException(@NotNull MessageCreateData data) {
		super();
		this.data = data;
	}

	@NotNull
	public MessageCreateData getCreateData() {
		return data;
	}

	@NotNull
	public MessageEditData getEditData() {
		return MessageEditData.fromCreateData(data);
	}
}
