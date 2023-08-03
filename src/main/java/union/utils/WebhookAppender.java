package union.utils;

import java.io.IOException;

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
	private String json = "{"
			+ "\"embeds\": [{"
			+ "\"color\": 16711680, "
			+ "\"description\": {message}"
			+ "}]"
			+ "}";

	private long lastSend = 0L;
	private long interval = 10L;

	private OkHttpClient client = new OkHttpClient();

	@Override
	protected void append(ILoggingEvent event) {
		// Not Error level
		if (!event.getLevel().isGreaterOrEqual(Level.ERROR)) return;

		if (url == null) return;

		// Limit send rate
		if (event.getTimeStamp() < lastSend + interval) return;

		// Encode message
		String message = new String(encoder.encode(event));

		// Send message
		send(message);
		lastSend = event.getTimeStamp();
	}

	private void send(String message) {
		// Replace json variables
		String payload = json.replace("{message}", JSONObject.quote(message));

		// Create HTTP POST request
		Request request = new Request.Builder()
			.url(url)
			.post(RequestBody.create(payload, MediaType.parse("application/json; charset=utf-8")))
			.build();

		// Execute HTTP POST request
		client.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException ex) {
				logger.warn("Webhook call failed. Payload:\n"+payload, ex);
			}

			@Override
			public void onResponse(Call call, Response response) {
				// Do nothing
			}
		});
	}

	public Encoder<ILoggingEvent> getEncoder() {
		return encoder;
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
