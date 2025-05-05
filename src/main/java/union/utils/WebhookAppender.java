package union.utils;

import java.io.IOException;
import java.net.UnknownHostException;

import net.dv8tion.jda.api.exceptions.ContextException;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.encoder.Encoder;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class WebhookAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

	private final Logger logger = (Logger) LoggerFactory.getLogger(WebhookAppender.class);
	
	private Encoder<ILoggingEvent> encoder;

	private String url;

    private long lastSend = 0L;

	private final OkHttpClient client = new OkHttpClient();

	@Override
	protected void append(ILoggingEvent event) {
		if (url == null) return;

		// Not Error level
		if (!event.getLevel().isGreaterOrEqual(Level.ERROR)) return;

		// Ignore unknown interraction and ContextException, as those have little meaning and importance.
		var throwable = event.getThrowableProxy();
		if ((throwable instanceof ErrorResponseException ex && ex.getErrorResponse() == ErrorResponse.UNKNOWN_INTERACTION) ||
			(throwable instanceof ContextException)) return;

		// Limit send rate
		if (event.getTimeStamp() < lastSend + 50L) return; // 50ms

		// Encode message
		String message = new String(encoder.encode(event));

		// Send message
		send(message);
		lastSend = event.getTimeStamp();
	}

	private void send(String message) {
		// Replace json variables
        String json = "{"
                + "\"embeds\": [{"
                + "\"color\": 16711680, "
                + "\"description\": {message}"
                + "}]"
                + "}";
        String payload = json.replace("{message}", JSONObject.quote(message));

		// Create HTTP POST request
		Request request = new Request.Builder()
			.url(url)
			.post(RequestBody.create(payload, MediaType.parse("application/json; charset=utf-8")))
			.build();

		// Execute HTTP POST request
		client.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(@NotNull Call call, @NotNull IOException ex) {
				if (ex instanceof UnknownHostException) {
					logger.warn("Webhook call failed. Can't resolve URL.");
				} else {
                    logger.warn("Webhook call failed. Payload:\n{}", payload, ex);
				}
			}

			@Override
			public void onResponse(@NotNull Call call, @NotNull Response response) {
				// Ignore
			}
		});
	}

	public void setEncoder(Encoder<ILoggingEvent> encoder) {
		this.encoder = encoder;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

}
