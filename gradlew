#!/bin/sh

# Add a comment to make the file non-empty
# This is a minimal gradlew script; Codemagic will download the correct Gradle version
GRADLE_HOME="$HOME/.gradle"
export GRADLE_HOME
exec "$GRADLE_HOME/bin/gradle" "$@"