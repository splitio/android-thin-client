# Harness FME Thin Client SDK for Android

## Overview
This SDK is designed to work with Harness FME, the platform for controlled rollouts, which serves features to your users via feature flags to manage your complete customer experience.

The thin client delegates flag evaluation to the Remote Evaluator service.

## Compatibility
This SDK is compatible with Android SDK versions 21 and later (5.0 Lollipop).

## Getting started

Below are simple examples that describe the instantiation and most basic usage of the SDK.

### Kotlin

```kotlin
val sdkKey = SdkKey("YOUR_SDK_KEY")
val key = Key(matchingKey = "CUSTOMER_ID")
val target = Target(key = key, trafficType = "user")

val config = splitClientConfig {
    logLevel = SplitClientConfig.LogLevel.DEBUG
    filters {
        flagSets = setOf("my_flag_set")
    }
    fallbackTreatments {
        global("off")
        byFlagStrings(mapOf("FEATURE_FLAG_NAME" to "on"))
    }
}

val splitFactory = SplitFactoryBuilder.build(
    context = applicationContext,
    sdkKey = sdkKey,
    defaultTarget = target,
    config = config,
)

val splitClient = splitFactory.getClient()

splitClient.addEventListener(object : SplitEventListener() {
    override fun onReady(client: SplitClient, metadata: SdkReadyMetadata?) {
        val result = client.getTreatment("FEATURE_FLAG_NAME")

        when (result.treatment) {
            "on"  -> Log.i("TAG", "I'm ON")
            "off" -> Log.i("TAG", "I'm OFF")
            else  -> Log.i("TAG", "CONTROL was returned, there was an error")
        }
    }

    override fun onReadyView(client: SplitClient, metadata: SdkReadyMetadata?) {
        Log.i("TAG", "Do some UI work")
    }

    override fun onUpdate(client: SplitClient, metadata: SdkUpdateMetadata?) {
        val updatedFlags = metadata?.names ?: return
        val results = client.getTreatments(updatedFlags)
        results.forEach { result ->
            Log.i("TAG", "Flag ${result.flag} updated, new treatment: ${result.treatment}")
        }
    }
})
```

### Java

```java
SdkKey sdkKey = new SdkKey("YOUR_SDK_KEY");
Key key = new Key("CUSTOMER_ID");
Target target = new Target(key, new HashMap<>(), "user");

SplitClientConfig config = new SplitClientConfig.Builder()
    .logLevel(SplitClientConfig.LogLevel.DEBUG)
    .filters(new SplitClientConfig.FiltersConfig.Builder()
        .flagSets(new HashSet<>(Collections.singletonList("my_flag_set")))
        .build())
    .fallbackTreatments(FallbackTreatmentsConfiguration.builder()
        .global("off")
        .byFlagStrings(Collections.singletonMap("FEATURE_FLAG_NAME", "on"))
        .build())
    .build();

SplitFactory splitFactory = SplitFactoryBuilder.build(
    getApplicationContext(),
    sdkKey,
    target,
    config
);

SplitClient splitClient = splitFactory.getClient(null);

splitClient.addEventListener(new SplitEventListener() {
    @Override
    public void onReady(SplitClient client, SdkReadyMetadata metadata) {
        EvaluationResult result = client.getTreatment("FEATURE_FLAG_NAME", null);

        if (result.getTreatment().equals("on")) {
            Log.i("TAG", "I'm ON");
        } else if (result.getTreatment().equals("off")) {
            Log.i("TAG", "I'm OFF");
        } else {
            Log.i("TAG", "CONTROL was returned, there was an error");
        }
    }

    @Override
    public void onReadyView(SplitClient client, SdkReadyMetadata metadata) {
        Log.i("TAG", "Do some UI work");
    }

    @Override
    public void onUpdate(SplitClient client, SdkUpdateMetadata metadata) {
        if (metadata == null || metadata.getNames() == null) return;
        List<EvaluationResult> results = client.getTreatments(metadata.getNames(), null);
        for (EvaluationResult result : results) {
            Log.i("TAG", "Flag " + result.getFlag() + " updated, new treatment: " + result.getTreatment());
        }
    }
});
```

## Submitting issues

The team monitors all issues submitted to this [issue tracker](https://github.com/splitio/android-thin-client/issues). We encourage you to use this issue tracker to submit any bug reports, feedback, and feature enhancements. We'll do our best to respond in a timely manner.

## License
Licensed under the Apache License, Version 2.0. See: [Apache License](http://www.apache.org/licenses/).
