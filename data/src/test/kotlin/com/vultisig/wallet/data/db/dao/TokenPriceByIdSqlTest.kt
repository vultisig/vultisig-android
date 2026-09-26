package com.vultisig.wallet.data.db.dao

import java.sql.DriverManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class TokenPriceByIdSqlTest {

    @Test
    fun `exact id wins over an older case variant and the other row stays`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    "CREATE TABLE tokenPrice (" +
                        "tokenId TEXT NOT NULL, currency TEXT NOT NULL, price TEXT NOT NULL, " +
                        "PRIMARY KEY (tokenId, currency))"
                )
            }
            insert(connection, "CAKE-BSC", "1.96")
            insert(connection, "Cake-BSC", "2.80")

            assertEquals("2.80", priceFor(connection, "Cake-BSC"))
            assertEquals("1.96", priceFor(connection, "CAKE-BSC"))
            assertEquals(
                1,
                connection.createStatement().use { statement ->
                    statement
                        .executeQuery(
                            "SELECT COUNT(*) FROM tokenPrice " +
                                "WHERE tokenId IN ('CAKE-BSC') AND currency = 'usd'"
                        )
                        .use { cursor ->
                            cursor.next()
                            cursor.getInt(1)
                        }
                },
            )
        }
    }

    private fun insert(connection: java.sql.Connection, tokenId: String, price: String) {
        connection.prepareStatement(
            "INSERT INTO tokenPrice (tokenId, currency, price) VALUES (?, 'usd', ?)"
        ).use { statement ->
            statement.setString(1, tokenId)
            statement.setString(2, price)
            statement.executeUpdate()
        }
    }

    private fun priceFor(connection: java.sql.Connection, tokenId: String): String {
        val sql = TOKEN_PRICE_BY_ID.replace(":tokenId", "?").replace(":currency", "?")
        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, tokenId)
            statement.setString(2, "usd")
            statement.setString(3, tokenId)
            statement.executeQuery().use { cursor ->
                check(cursor.next())
                cursor.getString(1)
            }
        }
    }
}
