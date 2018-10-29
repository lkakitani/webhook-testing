package app.model


import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

import org.jetbrains.exposed.dao.*

object Events : IntIdTable() {
    val type = varchar("type", length = 50)
    val deliveryGuid = varchar("delivery_guid", length = 100)
    val payload = text("payload")
}

class Event(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Event>(Events)

    var type by Events.type
    var deliveryGuid by Events.deliveryGuid
    var payload by Events.payload


}

data class IssueEvent(
        val action: String,
        val createdAt: String
)
