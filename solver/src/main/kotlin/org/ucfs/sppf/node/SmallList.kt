package org.ucfs.sppf.node

class SmallList<T> : Iterable<T> {
	private var first: T? = null
	private var second: T? = null
	private var rest: ArrayList<T>? = null
	private var _size: Int = 0

	fun add(element: T) {
		when (_size) {
			0 -> first = element
			1 -> second = element
			else -> {
				if (rest == null) rest = ArrayList(4)
				rest!!.add(element)
			}
		}
		_size++
	}

	@Suppress("UNCHECKED_CAST")
	operator fun get(index: Int): T = when {
		index == 0 && _size > 0 -> first as T
		index == 1 && _size > 1 -> second as T
		index >= 2 -> rest!![index - 2]
		else -> throw IndexOutOfBoundsException("Index $index, size $_size")
	}

	val size: Int get() = _size

	fun isEmpty(): Boolean = _size == 0

	fun isNotEmpty(): Boolean = _size > 0

	fun findLast(predicate: (T) -> Boolean): T? {
		var result: T? = null
		for (item in this) {
			if (predicate(item)) result = item
		}
		return result
	}

	fun contains(element: T): Boolean {
		for (item in this) {
			if (item == element) return true
		}
		return false
	}

	override fun iterator(): Iterator<T> = object : Iterator<T> {
		var index = 0
		override fun hasNext() = index < _size
		override fun next(): T = get(index++)
	}
}
