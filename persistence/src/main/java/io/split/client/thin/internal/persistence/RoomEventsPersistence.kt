package io.split.client.thin.internal.persistence

class RoomEventsPersistence(
    private val dao: EventDao
) : PersistentEventsStorage {

    override fun push(eventJson: String) {
        val entity = EventEntity(
            0,
            eventJson,
            System.currentTimeMillis()
        )
        dao.insert(entity)
    }

    override fun pop(count: Int): List<String> {
        val entities = dao.getOldest(count)
        val eventJsons = entities.map { it.body }

        if (entities.isNotEmpty()) {
            dao.delete(entities)
        }

        return eventJsons
    }

    override fun clear() {
        dao.deleteAll()
    }

    override fun count(): Int {
        return dao.count()
    }
}
