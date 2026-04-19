#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
./apps/android/gradlew -p apps/android :app:testDebugUnitTest \
  --tests 'io.github.baba983.nativedo.feature.browser.AppBrowserStateTest' \
  --tests 'io.github.baba983.nativedo.feature.browser.AppBrowserSheetContainerTest' \
  --tests 'io.github.baba983.nativedo.feature.browser.AppBrowserSheetMotionModelTest' \
  --tests 'io.github.baba983.nativedo.feature.browser.AppBrowserReaderSheetSettleTest' \
  --tests 'io.github.baba983.nativedo.ui.BrowserTabsTransitionContractTest' \
  --tests 'io.github.baba983.nativedo.ui.NativeDoAppStateTest' \
  --quiet
