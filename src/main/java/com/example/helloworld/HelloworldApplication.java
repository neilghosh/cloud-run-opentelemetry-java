package com.example.helloworld;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.logging.Logger;

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
		// OpenTelemetry openTelemetry;

		// private final Tracer tracer;

		// public HelloworldController(OpenTelemetry openTelemetry) {
		// this.tracer = openTelemetry.getTracer("application");
		// }

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
		String doWork(@RequestHeader Map<String, String> headers) {
			logger.info(String.format("Trade Header ", headers.get("x-cloud-trace-context")));
			String[] ids = (headers.get("x-cloud-trace-context").split(";"))[0].split("/");
			logger.info(String.format("Trace Parent Context  '%s' ", ids[0]));
			logger.info(String.format("Span Parent Context  '%s' ", ids[1]));

			SpanContext remoteContext = SpanContext.createFromRemoteParent(
					ids[0],
					ids[1],
					TraceFlags.getSampled(),
					TraceState.getDefault());

			Span span = tracer
					.spanBuilder("Make Call Out")
					.setParent(Context.current().with(Span.wrap(remoteContext)))
					.startSpan();
					
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
				// Span rootSpan = Span.current();
				Span childSpan = tracer.spanBuilder("HTTP Request")
						.setAttribute("method", request.method())
						.setAttribute("url", request.uri().toString())
						.setParent(Context.current().with(span))
						.startSpan();
				response = client.send(request, BodyHandlers.ofString());
				childSpan.end();
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
			span.end();
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