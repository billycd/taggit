package io.gitstars

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import main.kotlin.io.gitstars.*
import org.http4k.client.ApacheClient
import org.http4k.core.*
import org.http4k.core.Method.*
import org.http4k.core.Status.Companion.ACCEPTED
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.PERMANENT_REDIRECT
import org.http4k.core.Status.Companion.TEMPORARY_REDIRECT
import org.http4k.filter.CorsPolicy
import org.http4k.filter.ServerFilters
import org.http4k.format.Jackson.asJsonObject
import org.http4k.format.Jackson.asPrettyJsonString
import org.http4k.format.Jackson.auto
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import org.http4k.security.InsecureCookieBasedOAuthPersistence
import org.http4k.security.OAuthProvider
import org.http4k.security.gitHub
import org.http4k.server.Netty
import org.http4k.server.asServer

fun main() {

    val githubClientId = System.getenv("GITHUB_CLIENT_ID")
    val githubClientSecret = System.getenv("GITHUB_CLIENT_SECRET")
    val port = 9000

    val callbackUri = Uri.of("http://localhost:$port/callback")

    val oauthPersistence = InsecureCookieBasedOAuthPersistence("gitstars")

    val metadataArrayLens = Body.auto<Metadata>().toLens()

    val oauthProvider = OAuthProvider.gitHub(
        ApacheClient(),
        Credentials(githubClientId, githubClientSecret),
        callbackUri,
        oauthPersistence
    )

    val app: HttpHandler =
        routes(
            callbackUri.path bind GET to oauthProvider.callback,
            "/login2" bind GET to {
                Response(PERMANENT_REDIRECT).header("location", "https://github.com/login/oauth/authorize?client_id=04a50fefd14124ecfb84&response_type=code&scope=user&redirect_uri=http://localhost:9000/callback").header("Access-Control-Allow-Origin", "*")
            },
            "/login" bind GET to oauthProvider.authFilter.then {
                println(it)
                val token = oauthPersistence.retrieveToken(it)?.value?.substringBefore("&scope")?.split("=")?.last()
                val savedUserId = loginOrRegister(token!!)
                Response(TEMPORARY_REDIRECT).header("location", "http://localhost:8080/home")
            },
            "/user/{userId}/sync" bind POST to { request ->
                val syncJobId = syncUserRepos(request.path("userId")?.toUUID()
                    ?: throw IllegalArgumentException("userId param cannot be left null"))
                Response(ACCEPTED).headers((listOf(Pair("Location", "/sync/$syncJobId"))))
            },
            "/sync/{jobId}" bind GET to { request ->
                Response(OK).body(getRepoSyncJobUsingId(request.path("jobId")?.toUUID()
                    ?: throw IllegalArgumentException("jobId param cannot be left null")).asJsonObject().asPrettyJsonString())
            },
            "repo" bind routes(
                "{repoId}/tags" bind POST to { request ->
                    Response(OK).body(addTags(request.path("repoId")?.toUUID()
                        ?: throw IllegalArgumentException("repoId param cannot be left null"), metadataArrayLens(request)).asJsonObject().asPrettyJsonString())
                }
            )
        )

    ServerFilters.Cors(CorsPolicy.UnsafeGlobalPermissive)
        .then(app)
        .asServer(Netty(port))
        .start()
        .block()
}
