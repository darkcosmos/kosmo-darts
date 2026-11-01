package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.HistoryFilters
import io.github.darkcosmos.kosmodarts.history.HistoryList
import io.github.darkcosmos.kosmodarts.history.HistoryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.concurrent.Executor

/** HS7, AR9: the History read goes through the read executor and always answers, with the list or `Failed`. */
class PersistenceHistoryTest {
    private val direct = Executor { it.run() }
    private val stores = MemoryStore()

    private fun read(persistence: Persistence, filters: HistoryFilters = HistoryFilters()): DbResult<HistoryList> {
        var answer: DbResult<HistoryList>? = null
        persistence.loadHistory(filters) { answer = it }
        return checkNotNull(answer) { "no answer" }
    }

    @Test
    fun `the list of the repository is given as it is`() {
        val list = DbResult.Ok(HistoryList(emptyList(), emptyList()))
        val asked = mutableListOf<HistoryFilters>()
        val persistence = Persistence(stores, MemoryStore(), direct, history = { asked += it; list })

        val filters = HistoryFilters(profileId = 4)
        assertSame(list, read(persistence, filters))
        assertEquals(listOf(filters), asked)
    }

    @Test
    fun `a failing repository gives Failed, and the next read asks the repository again`() {
        var answer: DbResult<HistoryList> = DbResult.Failed
        var reads = 0
        val persistence = Persistence(stores, MemoryStore(), direct, history = HistoryRepository { reads++; answer })

        assertEquals(DbResult.Failed, read(persistence))
        answer = DbResult.Ok(HistoryList(emptyList(), emptyList()))
        assertEquals(answer, read(persistence))
        assertEquals(2, reads)
    }

    @Test
    fun `without a repository the read gives Failed`() {
        assertEquals(DbResult.Failed, read(Persistence(stores, MemoryStore(), direct)))
    }

    @Test
    fun `the read runs on the read executor, not the save executor`() {
        val ran = mutableListOf<String>()
        val save = Executor { ran += "save"; it.run() }
        val readThread = Executor { ran += "read"; it.run() }
        val persistence = Persistence(
            stores,
            MemoryStore(),
            save,
            history = { DbResult.Ok(HistoryList(emptyList(), emptyList())) },
            readExecutor = readThread,
        )

        read(persistence)

        assertEquals(listOf("read"), ran)
    }
}
