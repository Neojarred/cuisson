package app.cuisson.data

import app.cash.sqldelight.db.SqlDriver

/** Each platform supplies its own SQLite driver; everything above this line is shared. */
expect class DatabaseDriverFactory {
    fun create(): SqlDriver
}
