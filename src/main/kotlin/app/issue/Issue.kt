package app.issue

import com.fasterxml.jackson.annotation.JsonProperty

data class Issue(
        @JsonProperty("id", required = true) val id: Int,
        @JsonProperty("created_at", required = true) val createdAt: String
)