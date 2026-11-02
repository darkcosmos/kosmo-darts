package io.github.darkcosmos.kosmodarts.history

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The committed schema of version 1 (ADR 0012 "Tables", plan D26, D31 to D33): tables, mandatory columns, keys and
 * indexes. The build regenerates this file from the entities; `git status` stays clean when they match.
 */
@RunWith(RobolectricTestRunner::class) // For org.json, which the Android framework provides.
class HistorySchemaTest {
    // The unit test working directory is the module directory.
    private val database: JSONObject = JSONObject(
        File("schemas/io.github.darkcosmos.kosmodarts.history.HistoryDatabase/1.json").readText(),
    ).getJSONObject("database")

    private val entities: Map<String, JSONObject> =
        database.getJSONArray("entities").objects().associateBy { it.getString("tableName") }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

    private fun columnsOf(table: String): Map<String, JSONObject> =
        entities.getValue(table).getJSONArray("fields").objects().associateBy { it.getString("columnName") }

    /** Index descriptions of [table]: column list and uniqueness. */
    private fun indexesOf(table: String): Set<Pair<List<String>, Boolean>> =
        entities.getValue(table).optJSONArray("indices")?.objects().orEmpty()
            .map { it.getJSONArray("columnNames").strings() to it.getBoolean("unique") }.toSet()

    private fun primaryKeyOf(table: String): List<String> =
        entities.getValue(table).getJSONObject("primaryKey").getJSONArray("columnNames").strings()

    @Test
    fun versionOneHasTheSixTables() {
        assertEquals(HistoryDatabase.VERSION, database.getInt("version"))
        assertEquals(
            setOf("profile", "session", "match_record", "match_player", "leg", "dart"),
            entities.keys,
        )
    }

    @Test
    fun everyColumnIsMandatoryExceptTheDartCoordinates() {
        val nullable = entities.keys.flatMap { table ->
            columnsOf(table).filterValues { !it.optBoolean("notNull", false) }.keys.map { "$table.$it" }
        }

        assertEquals(listOf("dart.x_mm", "dart.y_mm"), nullable.sorted())
    }

    @Test
    fun columnsAreThoseOfTheAdr() {
        val expected = mapOf(
            "profile" to setOf("id", "name", "name_key", "created_at", "last_used_at", "reset_at"),
            "session" to setOf("id", "type", "started_at", "ended_at", "rules_version", "app_version"),
            "match_record" to setOf("session_id", "match_uuid", "mode", "variant", "settings", "settings_version"),
            "match_player" to
                setOf("session_id", "position", "profile_id", "color_argb", "final_rank", "legs_won", "sets_won"),
            "leg" to
                setOf("id", "session_id", "ordinal", "set_number", "leg_number", "starter_position", "winner_position"),
            "dart" to setOf(
                "id", "leg_id", "player_position", "visit", "rank", "segment", "multiplier", "bust", "voided", "source",
                "x_mm", "y_mm", "entered_at",
            ),
        )

        assertEquals(expected, entities.keys.associateWith { columnsOf(it).keys })
        assertFalse(entities.keys.any { "has_journal" in columnsOf(it) })
    }

    @Test
    fun columnTypesFollowTheAdr() {
        assertEquals("REAL", columnsOf("dart").getValue("x_mm").getString("affinity"))
        assertEquals("REAL", columnsOf("dart").getValue("y_mm").getString("affinity"))
        listOf("session" to "type", "match_record" to "mode", "dart" to "source").forEach { (table, column) ->
            assertEquals("$table.$column", "TEXT", columnsOf(table).getValue(column).getString("affinity"))
        }
    }

    @Test
    fun keysAndIndexesAreThoseOfTheAdr() {
        assertEquals(setOf(listOf("name_key") to true, listOf("last_used_at") to false), indexesOf("profile"))
        assertEquals(setOf(listOf("ended_at") to false), indexesOf("session"))
        assertEquals(listOf("session_id"), primaryKeyOf("match_record"))
        assertEquals(setOf(listOf("match_uuid") to true, listOf("mode") to false), indexesOf("match_record"))
        assertEquals(listOf("session_id", "position"), primaryKeyOf("match_player"))
        assertEquals(setOf(listOf("profile_id") to false), indexesOf("match_player"))
        assertEquals(setOf(listOf("session_id", "ordinal") to true), indexesOf("leg"))
        assertEquals(setOf(listOf("leg_id", "visit", "rank") to false), indexesOf("dart"))
    }

    @Test
    fun noForeignKeyDeletesOrUpdatesAnything() {
        val actions = entities.values.flatMap { entity ->
            entity.optJSONArray("foreignKeys")?.objects().orEmpty()
                .flatMap { listOf(it.getString("onDelete"), it.getString("onUpdate")) }
        }

        assertEquals(setOf("NO ACTION"), actions.toSet())
        assertEquals(5, actions.size / 2)
    }
}
