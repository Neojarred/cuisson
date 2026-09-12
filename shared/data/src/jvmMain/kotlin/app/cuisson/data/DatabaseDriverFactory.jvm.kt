package app.cuisson.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cuisson.data.db.CuissonDatabase

/**
 * A database on the JVM, which exists so the schema and the repository can be exercised
 * in tests without an emulator. No JVM build of Cuisson ships.
 */
actual class DatabaseDriverFactory(private val path: String = JdbcSqliteDriver.IN_MEMORY) {
    actual fun create(): SqlDriver =
        JdbcSqliteDriver(path).also { CuissonDatabase.Schema.create(it) }
}
