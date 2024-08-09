package com.example.helloworld;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.context.Context;
import java.util.Map;
import java.util.Collections;
import java.util.logging.Logger;
import com.google.cloud.MonitoredResource;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.LoggingOptions;
import com.google.cloud.logging.Payload.StringPayload;;
import com.google.cloud.logging.Severity;
import com.google.common.collect.ImmutableMap;

@SpringBootApplication
public class HelloworldApplication {
	OpenTelemetry openTelemetry = AutoConfiguredOpenTelemetrySdk.initialize().getOpenTelemetrySdk();
	Logger logger = Logger.getLogger(HelloworldApplication.class.getName());

	private static final String INSTRUMENTATION_NAME = HelloworldApplication.class.getName();

	@Value("${NAME:World}")
	String name;

	@RestController
	class HelloworldController {

		Tracer tracer = openTelemetry.getTracer(INSTRUMENTATION_NAME);

		@GetMapping("/")
		String hello() {
			return "Hello " + name + "!";
		}

		@GetMapping("/listHeaders")
		public ResponseEntity<String> listAllHeaders(
				@RequestHeader Map<String, String> headers) {
			headers.forEach((key, value) -> {
				logger.info(String.format("Header '%s' = %s", key, value));
			});
			return new ResponseEntity<String>(
					String.format("Listed %d headers", headers.size()), HttpStatus.OK);
		}

		@GetMapping("/dowork")
		String doWork(@RequestHeader Map<String, String> headers) throws Exception {
			// Use this to inject own trace id from calling service
			logger.info(String.format("traceparent %s", headers.get("traceparent'")));
			// TODO prioratise manually injected trace id over header based one

			// Auto Injected Trace Id
			logger.info(String.format("x-cloud-trace-context %s", headers.get("x-cloud-trace-context")));

			// Parse and log trace and span id from context
			String[] ids = (headers.get("x-cloud-trace-context").split(";"))[0].split("/");
			String traceId = ids[0];
			logger.info(String.format("Trace Parent Context  '%s' ", traceId));

			String spanId = ids[1];
			String spanIdHex = (new BigInteger(spanId)).toString(16);
			logger.info(String.format("Span Parent Context  '%s' ", spanIdHex));

			// String logName = "projects/demoneil/logs/run.googleapis.com%2Frequests";
			String logName = "my-log";

			// Custom Log Attached to Trace and Span Context
			try (Logging logging = LoggingOptions.getDefaultInstance().getService()) {
				LogEntry entry = LogEntry.newBuilder(StringPayload.of("About to make an HTTP call"))
						.setSeverity(Severity.INFO)
						.setLogName(logName)
						.setResource(MonitoredResource.newBuilder("global").build())
						.setTrace("projects/demoneil/traces/" + traceId)
						.setSpanId(spanId)
						.build();

				// Writes the log entry asynchronously
				logging.write(Collections.singleton(entry));

				// Optional - flush any pending log entries just before Logging is closed
				logging.flush();
			}

			// Initialise remote context from span and trace id received from request
			SpanContext remoteContext = SpanContext.createFromRemoteParent(
					traceId,
					spanIdHex,
					TraceFlags.getSampled(),
					TraceState.getDefault());

			Span parentSpan = tracer
					.spanBuilder("Make Call Out")
					.setParent(Context.current().with(Span.wrap(remoteContext)))
					.startSpan();

			// Add artificial delay to increase the span
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				System.err.format("InterruptedException : %s%n", e);
			}

			// An external API call
			HttpRequest request = null;
			try {
				request = HttpRequest.newBuilder()
						.uri(new URI("https://postman-echo.com/get"))
						.GET()
						.build();
			} catch (URISyntaxException e) {
				e.printStackTrace();
			}

			HttpClient client = HttpClient.newHttpClient();
			HttpResponse<String> response = null;

			try {
				// Internal Child Span
				Span childSpan = tracer.spanBuilder("HTTP Request")
						.setAttribute("method", request.method())
						.setAttribute("url", request.uri().toString())
						.setParent(Context.current().with(parentSpan))
						.startSpan();
				response = client.send(request, BodyHandlers.ofString());
				childSpan.addEvent("HTTP Call Sucessfull");
				childSpan.setAttribute("HTTP Response", response.statusCode());
				childSpan.end();
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}

			// Custom Log Attached to Trace and Span Context
			try (Logging logging = LoggingOptions.getDefaultInstance().getService()) {
				LogEntry entry = LogEntry.newBuilder(StringPayload.of("Done with HTTP call"))
						.setSeverity(Severity.INFO)
						.setLogName(logName)
						.setResource(MonitoredResource.newBuilder("global").build())
						.setTrace("projects/demoneil/traces/" + traceId)
						.setSpanId(parentSpan.getSpanContext().getSpanId())
						.build();

				// Writes the log entry asynchronously
				logging.write(Collections.singleton(entry));

				// Optional - flush any pending log entries just before Logging is closed
				logging.flush();
			}

			parentSpan.end();
			return response == null ? "" : response.body();
		}
	}

	public static void main(String[] args) {
		SpringApplication.run(HelloworldApplication.class, args);
	}

	@Configuration
	public class OpenTelemetryConfig {
		@Bean
		public OpenTelemetry openTelemetry() {
			return GlobalOpenTelemetry.get();
		}
	}
}