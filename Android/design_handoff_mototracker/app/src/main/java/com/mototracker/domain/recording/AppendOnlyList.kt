package com.mototracker.domain.recording

/**
 * Growable list whose read-only [snapshot]s cost O(1) and stay valid while the list keeps growing.
 *
 * A snapshot captures the current backing array and size. Later [add]s only write slots past
 * every existing snapshot's size (or into a freshly grown copy), and [clear] swaps in a new
 * array, so a snapshot never observes a change. This lets [RecordingEngine] hand its growing
 * track to the UI and the crash-recovery journal on every GPS fix without copying the whole
 * ride each time.
 *
 * Not thread-safe for concurrent writers: one owner appends; snapshots may be read from any
 * thread once handed over through a synchronising channel (coroutine launch, mutex, flow).
 */
internal class AppendOnlyList<T> {

    private var items: Array<Any?> = arrayOfNulls(INITIAL_CAPACITY)

    /** Number of elements appended since the last [clear]. */
    var size: Int = 0
        private set

    /** Appends [item], growing the backing array when full. */
    fun add(item: T) {
        if (size == items.size) items = items.copyOf(size * 2)
        items[size++] = item
    }

    /** Appends every element of [list] in order. */
    fun addAll(list: List<T>) {
        for (item in list) add(item)
    }

    /** Empties the list; snapshots taken earlier keep their contents. */
    fun clear() {
        items = arrayOfNulls(INITIAL_CAPACITY)
        size = 0
    }

    /** Returns an immutable view of the current contents in O(1). */
    fun snapshot(): List<T> = Snapshot(items, size)

    private class Snapshot<T>(
        private val items: Array<Any?>,
        override val size: Int,
    ) : AbstractList<T>(), RandomAccess {
        @Suppress("UNCHECKED_CAST")
        override fun get(index: Int): T {
            if (index < 0 || index >= size) throw IndexOutOfBoundsException("index=$index, size=$size")
            return items[index] as T
        }
    }

    private companion object {
        const val INITIAL_CAPACITY = 64
    }
}
