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
