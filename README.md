# OpenTelemetry Tracing in Google Cloud Run

## Deploy to Cloud Run

```
  gcloud builds submit --region=us-central1
```

Invoke endpoint

```
curl https://o11y-b3zzuedwgq-uc.a.run.app/dowork
```

<img width="875" alt="image" src="https://github.com/user-attachments/assets/1091c5a3-8f79-4019-9a67-e47c7902c371">

```
hey https://o11y-b3zzuedwgq-uc.a.run.app/dowork
```

https://cloud.google.com/trace/docs/trace-context
https://cloud.google.com/trace/docs/overview
https://cloud.google.com/run/docs/deploying
https://opentelemetry.io/docs/languages/java/instrumentation/#context-propagation
https://github.com/open-telemetry/opentelemetry-java-examples/tree/main/autoconfigure/src/main/java/io/opentelemetry/example/autoconfigure
https://opentelemetry.io/docs/languages/java/instrumentation/
