/*
 * Copyright 2018-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.liuwj.ktorm.database

import me.liuwj.ktorm.expression.SqlExpression
import me.liuwj.ktorm.expression.SqlFormatter
import me.liuwj.ktorm.schema.Column
import java.sql.PreparedStatement
import java.util.ServiceLoader

/**
 * Representation of a SQL dialect.
 *
 * It's known that there is a uniform standard for SQL language, but beyond the standard, many databases still have
 * their special features. The interface provides an extension mechanism for Ktorm and its extension modules to support
 * those dialect-specific SQL features.
 *
 * Implementations of this interface are recommended to be published as separated modules independent of ktorm-core.
 *
 * To enable a dialect, applications should add the dialect module to the classpath first, then configure the `dialect`
 * parameter to the dialect implementation while creating database instances via [Database.connect] functions.
 *
 * Since version 2.4, Ktorm's dialect modules start following the convention of JDK [ServiceLoader] SPI, so we don't
 * need to specify the `dialect` parameter explicitly anymore while creating [Database] instances. Ktorm auto detects
 * one for us from the classpath. We just need to insure the dialect module exists in the dependencies.
 */
interface SqlDialect {

    /**
     * Create a [SqlFormatter] instance, formatting SQL expressions as strings with their execution arguments.
     *
     * @param database the current database instance executing the formatted SQL.
     * @param beautifySql if we should output beautiful SQL strings with line-wrapping and indentation.
     * @param indentSize the indent size.
     * @return a [SqlFormatter] object, generally typed of subclasses to support dialect-specific sql expressions.
     */
    fun createSqlFormatter(database: Database, beautifySql: Boolean, indentSize: Int): SqlFormatter {
        return SqlFormatter(database, beautifySql, indentSize)
    }

    /**
     * Format the provided [SqlExpression] to a SQL string with its execution arguments, then create
     * a [PreparedStatement] from the global database using the SQL string and execute the specific
     * callback function with it. After the callback function completes, the statement will be
     * closed automatically.
     *
     * The default implementation simply calls the inline function [SqlExpression.prepareStatement]. Specific
     * dialects may override this if the default technique is unimplemented.
     *
     * @param expression The expression to prepare as a statement
     * @param autoGeneratedKeys a flag indicating whether auto-generated keys should be made available for retrieval.
     * @param func the executed callback function.
     * @return the result of the callback function.
     */
    fun <T> prepareStatement(
        expression: SqlExpression,
        autoGeneratedKeys: Boolean = false,
        func: (PreparedStatement) -> T
    ): T {
        return expression.prepareStatement(autoGeneratedKeys, func)
    }

    /**
     * Given a [PreparedStatement] that has executed an insert operation, and a [Column] representing the
     * primary key of the table, return the generated primary key.
     * Note that while the default implementation requires a non-null primary key column,
     * overriding implementations may not.
     *
     * @param statement The statement used to execute an insert operation
     * @param primaryKey The column representing the primary key
     */

    fun <T : Any> generatedKey(statement: PreparedStatement, primaryKey: Column<T>?): T {
        return statement.generatedKeys.use { rs ->
            if (rs.next()) {
                val sqlType = primaryKey?.sqlType ?: error("Table  must have a primary key.")
                sqlType.getResult(rs, 1) ?: error("Generated key is null.")
            } else {
                error("No generated key returns by database.")
            }
        }
    }
}

/**
 * Thrown to indicate that a feature is not supported by the current dialect.
 *
 * @param message the detail message, which is saved for later retrieval by [Throwable.message].
 * @param cause the cause, which is saved for later retrieval by [Throwable.cause].
 */
class DialectFeatureNotSupportedException(
    message: String? = null,
    cause: Throwable? = null
) : UnsupportedOperationException(message, cause) {

    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Auto detect a dialect implementation.
 */
internal fun detectDialectImplementation(): SqlDialect {
    val dialects = ServiceLoader.load(SqlDialect::class.java).toList()
    return when (dialects.size) {
        0 -> object : SqlDialect {}
        1 -> dialects[0]
        else -> error("More than one dialect implementations found in the classpath, " +
            "please choose one manually, they are: $dialects")
    }
}
