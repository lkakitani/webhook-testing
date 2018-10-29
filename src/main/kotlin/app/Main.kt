package app

import app.model.Events
import app.model.Event
import app.model.IssueEvent
import app.user.User
import app.user.UserDao
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.Javalin
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileNotFoundException
import java.net.URL
import java.net.URLDecoder

fun main(args: Array<String>) {

    val userDao = UserDao()

    Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver")
    //Database.connect("jdbc:h2:~/test", driver = "org.h2.Driver")
    transaction {
        addLogger(StdOutSqlLogger)
        SchemaUtils.create(Events)
    }

    val app = Javalin.create().apply {
        exception(Exception::class.java) { e, ctx -> e.printStackTrace() }
        error(404) { ctx -> ctx.json("not found") }
    }.start(7002)

    app.routes {

        get("/users") { ctx ->
            ctx.json(userDao.users)
        }

        get("/users/:user-id") { ctx ->
            ctx.json(userDao.findById(ctx.pathParam("user-id").toInt())!!)
        }

        get("/users/email/:email") { ctx ->
            ctx.json(userDao.findByEmail(ctx.pathParam("email"))!!)
        }

        post("/users") { ctx ->
            val user = ctx.body<User>()
            userDao.save(name = user.name, email = user.email)
            ctx.status(201)
        }

        patch("/users/:user-id") { ctx ->
            val user = ctx.body<User>()
            userDao.update(
                    id = ctx.pathParam("user-id").toInt(),
                    user = user
            )
            ctx.status(204)
        }

        delete("/users/:user-id") { ctx ->
            userDao.delete(ctx.pathParam("user-id").toInt())
            ctx.status(204)
        }

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

        /*
        get("/querydb") { ctx ->
            transaction {
                var jsonArray = JSONArray()
                Event.all().forEach {
                    var jsonObject = JSONObject()
                    jsonObject.put("id", it.id)
                    jsonObject.put("type", it.type)
                    jsonObject.put("delivery_guid", it.deliveryGuid)
                    jsonObject.put("payload", URLDecoder.decode(it.payload, "UTF-8"))
                    jsonArray.put(jsonObject)
                    }
                ctx.html(jsonArray.toString())
            }
        }
        */
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

/*
                // todo properties file
                val issueJson = URL("https://api.github.com/repos/lkakitani/webhook-testing/issues/${ctx.pathParam("issue-id")}/events").readText()
                when (issueJson is String) {
                    true -> ctx.result(issueJson).contentType("application/json")
                    else -> throw RuntimeException()
                }
*/
            } catch (e: Exception) {
                ctx.status(400).json(mapOf("message" to "error"))
            }

        }

    }

}