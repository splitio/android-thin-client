package io.split.client.thin.internal.persistence

class RoomEventsPersistence(
    private val dao: EventDao
) : PersistentEventsStorage {

    override fun push(eventJson: String) {
        dao.insert(EventEntity(0, eventJson, System.currentTimeMillis()))
    }

    override fun pop(count: Int): List<StoredEvent> {
        return dao.getOldest(count).map { StoredEvent(it.id, it.body) }
    }

    override fun delete(ids: List<Long>) {
        dao.deleteByIds(ids)
    }

    override fun clear() {
        dao.deleteAll()
    }

    override fun count(): Int {
        return dao.count()
    }
}
