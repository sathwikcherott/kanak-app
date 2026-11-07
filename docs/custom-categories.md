# Custom categories

Quick Entry offers **+ New category** beside the category picker. Creation saves and selects the category without saving or clearing the expense draft. Names are trimmed, whitespace/Unicode-normalized, limited to 40 code points and unique regardless of case. Blank/control-only names and duplicates of built-in category names are rejected. Up to 100 custom categories are supported.

The original ten built-in names and stored identities are unchanged. Custom categories use stable UUID-based identities in a Room category table. Existing expense category strings and all financial columns remain unchanged. Custom labels appear in Quick Entry, recent expenses, Home, Transactions, editing, category filters and Analytics. A generic existing icon is used for custom categories. Creation is available in Quick Entry; rename/archive/delete are not part of this change.

Voice/manual descriptions can recognize an unambiguous full custom label when no established built-in rule matches. Existing rules, including Swathi/Girlfriend priority, remain first. A manually selected custom category is retained during dictation and description editing.

Room version 4 adds the category table via migration. Migration tests start from exported schemas 1, 2 and 3 and preserve existing amounts, dates, notes and built-in identities. The historical 1→2 migration is corrected to leave retry columns to the 2→3 migration.

Backup version 2 includes the custom-category catalog, including unused categories. Every assigned custom identity must resolve to that catalog. Duplicate IDs/names, invalid labels and dangling references are rejected before mutation. Version 1 backups remain importable with an empty custom catalog. Restoring replaces the full category catalog along with expenses and jobs in the same Room transaction, so an older backup also removes categories created after it; confirmation discloses that replacement. A failed transaction rolls all three tables back. Version 2 exports cannot be imported into older app builds that only support version 1. Export filenames now end in v2.json; older named files remain recognizable by cleanup.

On-device acceptance:
1. Export a recovery backup before upgrading. Install without uninstalling; verify existing expenses and Girlfriend/Swathi categories remain.
2. Enter an amount/note, choose + New category, create “Pet care”, then save. Verify the category is selected, the draft is retained and the expense appears under its own category.
3. Restart and verify the category remains available. Try duplicate, blank and overlong names; cancel the dialog and verify no category is created.
4. Filter/search Transactions by the new category, edit an expense to it, and check Home/Analytics totals.
5. Dictate an amount after explicitly selecting the custom category; confirm it stays selected.
6. Export and restore a v2 backup; verify unused and assigned categories survive. Import a v1 backup only when intentionally replacing current expenses and custom categories.
