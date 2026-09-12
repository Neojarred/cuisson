# Every schema change ships with a migration, tested off the device

Adding a column to the database now requires a matching `.sqm` migration and a test that
runs it against a database built at the old version. SQLDelight only executes the `CREATE
TABLE` statements on a fresh install, so a column added to the `.sq` file alone makes
every query referencing it fail on any device that already holds data.

## Why this is written down

It happened twice. `image_path` was added with no migration and went unnoticed only
because the single existing database had been wiped with `pm clear` for unrelated
reasons. `title_raw` was added the same way a fortnight later and made the app unable to
open on the only phone that had ever held real recipes, with
`no such column: recipe.title_raw`.

Neither mistake was visible in a build, in the test suite, or in a fresh install. Testing
an import worked fine, because the Review does not touch the database until the recipe is
saved.

## Consequences

`:shared:data` has a JVM target and a JVM SQLite driver for no reason other than this: a
migration can be run against a real database in a unit test, which is the only place to
discover it is wrong before someone's library is on the other side of it. `MigrationTest`
builds the previous schema, inserts a row, migrates, and asserts the row survived.

The JVM `DatabaseDriverFactory` exists only to serve those tests. No JVM build of Cuisson
ships.
