package app

import app.model.Events
import app.model.Event
import app.model.IssueEvent
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.Javalin
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.json.JSONObject
import java.net.URLDecoder

fun main(args: Array<String>) {

    Database.connect("jdbc:h2:~/test", driver = "org.h2.Driver")
    transaction {
        addLogger(StdOutSqlLogger)
        SchemaUtils.create(Events)
    }

    val app = Javalin.create().apply {
        exception(Exception::class.java) { e, ctx -> e.printStackTrace() }
        error(404) { ctx -> ctx.json("not found") }
    }.start(7000)

    app.routes {

        post("/webhook") { ctx ->
            ctx.json(ctx.body())
            transaction {
                Event.new {
                    type = ctx.header("X-Github-Event") ?: "unknown"
                    deliveryGuid = ctx.header("X-Github-Delivery") ?: "unknown"
                    payload = ctx.resultString() ?: "unknown"
                }
            }
            ctx.status(201)
        }

        get("/querydb") { ctx ->
            transaction {
                var html = "";
                Event.all().forEach {
                    html += """id: ${it.id}
                        |===
                        |type: ${it.type}
                        |===
                        |delivery guid: ${it.deliveryGuid}
                        |===
                        |payload: ${URLDecoder.decode(it.payload, "UTF-8")}
                        |
                        |
                        |""".trimMargin()}
                ctx.html(html)
            }
        }

        get("/issues/:issue-id/events") { ctx ->
            try {
                val issuesList: ArrayList<IssueEvent> = ArrayList()
                transaction {

                    val issues = Events.select { Events.type eq "issues" }
                    issues.forEach {
                        val pp = URLDecoder.decode(it[Events.payload], "UTF-8").removePrefix("\"payload=").removeSuffix("\"")
                        val payload = JSONObject(pp)
                        if (payload.getJSONObject("issue").getInt("number") == ctx.pathParam("issue-id").toInt()) {
                            val issue = IssueEvent(action = payload.getString("action"), createdAt = payload.getJSONObject("issue").getString("created_at"))
                            issuesList.add(issue)
                        }
                    }
                }
                ctx.json(issuesList)

            } catch (e: Exception) {
                ctx.status(400).json(mapOf("message" to "error"))
            }

        }

    }

}