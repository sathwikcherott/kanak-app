#!/usr/bin/env bash
# SDK-independent checks against the actual production models, date rules and JSON codec.
set -euo pipefail
cd "$(dirname "$0")/.."
kanak_check_dir=$(mktemp -d)
trap 'rm -rf "$kanak_check_dir"' EXIT
curl --fail --location --retry 2 -o "$kanak_check_dir/kotlin.zip" \
  https://github.com/JetBrains/kotlin/releases/download/v2.2.10/kotlin-compiler-2.2.10.zip
unzip -q "$kanak_check_dir/kotlin.zip" -d "$kanak_check_dir"
curl --fail --location --retry 2 -o "$kanak_check_dir/junit.jar" \
  https://repo.maven.apache.org/maven2/junit/junit/4.13.2/junit-4.13.2.jar
curl --fail --location --retry 2 -o "$kanak_check_dir/hamcrest.jar" \
  https://repo.maven.apache.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar
kanak_coroutines="$kanak_check_dir/kotlinc/lib/kotlinx-coroutines-core-jvm.jar"
"$kanak_check_dir/kotlinc/bin/kotlinc" \
  app/src/main/java/com/kanak/app/model/*.kt \
  app/src/main/java/com/kanak/app/voice/MathExpressionParser.kt \
  app/src/main/java/com/kanak/app/voice/VoiceDraftParser.kt \
  app/src/main/java/com/kanak/app/data/local/datastore/ThemePreference.kt \
  app/src/main/java/com/kanak/app/data/backup/AutoBackupSchedule.kt \
  app/src/main/java/com/kanak/app/data/backup/AutomaticBackup.kt \
  app/src/main/java/com/kanak/app/data/backup/BackupFileCatalog.kt \
  app/src/main/java/com/kanak/app/data/backup/BackupModels.kt \
  app/src/main/java/com/kanak/app/data/backup/BackupFileNames.kt \
  app/src/main/java/com/kanak/app/data/backup/KanakBackupSerializer.kt \
  app/src/main/java/com/kanak/app/ui/entry/QuickEntryUiState.kt \
  app/src/main/java/com/kanak/app/ui/entry/ExpenseDateRules.kt \
  app/src/test/java/com/kanak/app/data/AutomaticBackupTest.kt \
  app/src/test/java/com/kanak/app/data/BackupRepositoryTest.kt \
  app/src/test/java/com/kanak/app/data/BackupFileNamesTest.kt \
  app/src/test/java/com/kanak/app/data/CustomCategoryTest.kt \
  app/src/test/java/com/kanak/app/ui/ExpenseDateRulesTest.kt \
  app/src/test/java/com/kanak/app/voice/MathExpressionParserTest.kt \
  app/src/test/java/com/kanak/app/voice/VoiceDraftParserTest.kt \
  -classpath "$kanak_check_dir/junit.jar:$kanak_coroutines" -include-runtime -d "$kanak_check_dir/checks.jar"
java -cp "$kanak_check_dir/checks.jar:$kanak_check_dir/junit.jar:$kanak_check_dir/hamcrest.jar:$kanak_coroutines" \
  org.junit.runner.JUnitCore com.kanak.app.data.AutomaticBackupTest com.kanak.app.data.CustomCategoryTest com.kanak.app.data.BackupFileNamesTest com.kanak.app.data.BackupRepositoryTest com.kanak.app.ui.ExpenseDateRulesTest \
  com.kanak.app.voice.MathExpressionParserTest com.kanak.app.voice.VoiceDraftParserTest
