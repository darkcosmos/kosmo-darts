package io.github.darkcosmos.kosmodarts.capture.storage

/** In-memory [CaptureStorage]; each entry has an owner and a pending flag so foreign files can share a folder. */
internal class FakeCaptureStorage : CaptureStorage {
    class Item(val id: String, val folder: String, val name: String, val own: Boolean, var pending: Boolean) {
        var text: String = ""
    }

    val items = mutableListOf<Item>()
    val writes = mutableListOf<String>()
    var failWrites = false
    private var nextId = 1

    /** A file of another installation or app in [folder]. */
    fun addForeign(folder: String, name: String): Item = add(folder, name, own = false, pending = false)

    /** Simulates the camera creating a pending video or photo entry of this installation. */
    fun addPendingOwn(target: CaptureOutputTarget): Item = add(target.folder, target.displayName, true, pending = true)

    fun texts(folder: String, name: String): List<String> =
        items.filter { it.folder == folder && it.name == name }.map { it.text }

    override fun createTruthFile(folder: String): CaptureEntry {
        val item = add(folder, "truth.json", own = true, pending = false)
        return CaptureEntry(item.id, folder, item.name)
    }

    override fun writeTruth(entry: CaptureEntry, text: String) {
        if (failWrites) throw CaptureStorageException("disk full")
        val item = items.first { it.id == entry.id }
        item.text = text
        writes += text
    }

    override fun photoTarget(folder: String, file: String) = CaptureOutputTarget(folder, file, path(folder), "image/jpeg")

    override fun videoTarget(folder: String, file: String) = CaptureOutputTarget(folder, file, path(folder), "video/mp4")

    override fun deleteAllOwnSessions(): Int {
        val own = items.filter { it.own }
        items.removeAll(own.toSet())
        return own.size
    }

    override fun listOwnSessions(): Set<String> = items.filter { it.own }.mapTo(linkedSetOf()) { it.folder }

    private fun path(folder: String) = "$CAPTURE_ROOT$folder/"

    private fun add(folder: String, name: String, own: Boolean, pending: Boolean): Item =
        Item("fake-${nextId++}", folder, name, own, pending).also { items += it }
}
