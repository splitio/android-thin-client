# Contributing to the Harness FME Android Thin Client SDK

This SDK is an open source project and we welcome feedback and contribution. The information below describes how to build the project with your changes, run the tests, and send a Pull Request (PR).

## Development

### Development process

1. Fork the repository and create a topic branch from `development`. Please use a descriptive name for your branch.
2. While developing, use descriptive messages in your commits. Avoid short or meaningless sentences like "fix bug".
3. Make sure to add tests for both positive and negative cases.
4. Run the linter task of the project and fix any issues you find.
5. Run the build and make sure it runs with no errors.
6. Run all tests and make sure there are no failures.
7. `git push` your changes to GitHub within your topic branch.
8. Open a Pull Request (PR) from your forked repo into the `development` branch of the original repository.
9. When creating your PR, please fill out all the fields of the PR template, as applicable, for the project.
10. Check for conflicts once the pull request is created to make sure your PR can be merged cleanly into `development`.
11. Keep an eye out for any feedback or comments from Split's SDK team.

### Building the SDK

This is a Gradle multi-module project. To build all modules from the command line:

```bash
./gradlew build
```

You can also open the project in Android Studio and use the Build menu's Make Project option.

### Running tests

To run the unit tests for all modules:

```bash
./gradlew testDebugUnitTest
```

To run the tests for a single module (e.g. `secure-http-client`):

```bash
./gradlew :secure-http-client:testDebugUnitTest
```

To view aggregated coverage:

```bash
./gradlew jacocoAggregateUnitTestReport
```

### Running end-to-end (e2e) tests

The `e2e` module verifies the published AAR from a consumer's perspective, exercising the public API as an SDK consumer would rather than the source modules directly. It requires an emulator or physical device to run against:

```bash
./gradlew :e2e:connectedAndroidTest
```

If you add new public API surface, add corresponding test cases under `e2e/src/androidTest/java/io/split/client/thin/consumer/` — see `e2e/README.md` for details.

### Linting and other useful checks

To run Android Lint for a given module from the command line:

```bash
./gradlew :<module-name>:lint
```

You can also go to Android Studio's Analyze menu and select Inspect Code, choosing your preferred linter options.

# Contact

If you have any other questions or need to contact us directly in a private manner send us a note at sdks@split.io
