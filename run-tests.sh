#!/bin/sh

./gradlew -i check

echo
echo View lint report:
echo -n file://
realpath build/outputs/lint-results-debug.html

echo
echo View local unit test reports:
echo -n file://
realpath build/reports/tests/debug/index.html
echo -n file://
realpath build/reports/tests/release/index.html

