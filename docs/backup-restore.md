# Local Backup & Restore

Settings exports expenses and currency/theme preferences to an **unencrypted JSON file** using Android's document picker. Schema version 1 is independent of Room version 3. Import enforces the codec's 10 MiB / 50,000-record limits and validates the entire file before offering a preview.

The preview is read-only. Confirmation restores that in-memory validated data, not a second read of the external file. Cancelling the picker or preview makes no persistence changes. An empty backup is a valid replacement and its preview explicitly shows zero records.

## Replacement boundary

- The application uses one `OfflineExpenseRepository` instance. Its mutex coordinates saves, deletes, preference changes, export snapshots, restore and enrichment result application.
- Restore revalidates a private copy before any mutation. One Room transaction deletes all enrichment jobs, deletes expenses, and inserts the validated records with their original IDs, amounts, currencies, dates, descriptions and categorization fields. SQL failure rolls back both tables; preferences are not touched.
- After Room commits, one DataStore edit applies currency and theme. A failure here returns `EXPENSES_RESTORED_PREFERENCES_FAILED`: restored expenses stay in place, previous preferences remain, and Settings presents a persistent warning. It does not tell the user the financial restore failed.
- Once the replacement starts, coroutine cancellation does not interrupt completion of the Room/DataStore outcome. Cancellation before acquiring the write lock does not replace anything.
- Room and DataStore do **not** form one cross-store transaction. A process kill or power loss after the Room commit may leave restored expenses with the previous preferences. Financial records remain transactionally protected; settings can be changed afterward.
- Network categorization runs outside the mutex. A restore generation and job identity check discard results from before replacement, including when an expense ID is reused. Imported backups never replay the old enrichment queue.
- Last-exported time belongs to this installation and is not imported. API keys, audio, hardware identifiers and enrichment jobs are excluded.

## Samsung M34 acceptance check

1. Export a backup in Settings and retain that file. Confirm the last-exported time updates. This is your recovery copy.
2. Add one identifiable temporary expense. Optionally change display currency/theme. Import the saved file: verify the preview's record count, date and preferences. Cancel once; confirm the temporary expense is still present.
3. Import again and choose **Replace & Restore**. Verify the temporary expense is removed, the original amounts/dates/categories remain, and the backed-up currency/theme return. Check Home, Transactions and Analytics.
4. Cancel each system file picker once. Confirm no operation remains stuck and current data is unchanged.
5. Select an invalid JSON file. Confirm it reports an error before offering restoration and leaves current records intact.

CI runs the existing JVM suite, debug APK and instrumentation APK compilation, independent codec/calendar checks, and real Room/DataStore/Compose tests on Android 35. The emulator uses isolated test databases and temporary test files; it does not exercise a real document provider's system picker. Physical-device picker behavior and visual QA still require the acceptance check above.

## Filenames and manual cleanup

New exports use `Kanak_Backup_yyyy-MM-dd_HH-mm-ss-SSS_v1.json`, with the device's local creation time and Latin digits regardless of device language. The date-first format sorts by local time; milliseconds reduce routine filename collisions. Android's Create Document picker still lets the user choose the folder and name and prevents overwriting an existing document automatically. Existing exports are not renamed or moved.

Settings → Backup & Restore → **Advanced** → **Manage backup files** opens manual cleanup. The user grants access to a chosen folder via the system picker; access and that folder selection are remembered. For tidy storage, use a dedicated Kanak subfolder. Android may prevent choosing the Downloads root or storage root; a subfolder can be used instead. Exports still go to the location chosen in the export picker, not automatically to the management folder.

Cleanup only scans direct children with `Kanak_Backup_…json` / legacy `kanak_backup_…json` style names and then verifies their full backup contents. Unrelated, invalid, unsupported-version and unreadable files are not offered for deletion. No recursive filesystem scan or broad storage permission is used. Limits of 500 directory entries and 100 candidate backups prevent an incomplete, unbounded cleanup scan.

The newest verified backup (by the JSON export timestamp) is retained. **Select older · keep newest 3** selects only deletable older entries; users can also individually select older files. Nothing is deleted before a confirmation naming the files. Execution rechecks folder membership, name, content fingerprint, provider support and which backup is newest. Read-only, missing or changed files are skipped. Providers do not expose an atomic compare-and-delete operation: a concurrent external edit after the final content check cannot be made transactional. Partial deletion is reported and the list is refreshed. Financial records and the last-exported timestamp are not modified by cleanup.

M34 check: export several disposable backups into one Kanak folder, expand Advanced, choose that folder, verify both legacy/new names are recognized, cancel deletion once, and then delete selected older files. Confirm the newest file and current expenses remain. Close/reopen and verify the folder is remembered. Also cancel the folder picker and try a read-only/revoked folder if available.
