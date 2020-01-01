package main.kotlin.io.gitstars

import me.liuwj.ktorm.database.Database
import me.liuwj.ktorm.dsl.*
import me.liuwj.ktorm.schema.*
import me.liuwj.ktorm.support.postgresql.PostgreSqlDialect
import java.time.LocalDateTime

val db = Database.connect(
    url = "jdbc:postgresql://localhost:5432/gitstars",
    driver = "org.postgresql.Driver",
    user = "gitstars_admin",
    password = "localadmin",
    dialect = PostgreSqlDialect()
)

object Users : Table<Nothing>("users") {
    val id by int("id").primaryKey()
    val userName by text("user_name")
    val email by text("email")
    val password by text("password")
    val githubUserName by text("github_user_name")
    val githubUserId by long("github_user_id")
    val accessToken by text("access_token")
    val tokenRefreshedAt by datetime("token_refreshed_at")
    val lastLoginAt by datetime("last_login_at")
    val createdAt by datetime("created_at")
    val updatedAt by datetime("updated_at")
}

object Repo : Table<Nothing>("repo") {
    val id by int("id").primaryKey()
    val userId by int("user_id")
    val repoId by long("repo_id")
    val repoName by text("repo_name")
    val githubLink by text("github_link")
    val githubDescription by text("github_description")
    val starCount by int("star_count")
    val ownerAvatarUrl by text("owner_avatar_url")
    val tags by bytes("tags")
}


fun insertGitstarsUser(githubUser: GithubUser, token: String): Int {
    return Users.insert {
        it.userName to githubUser.name
        it.email to githubUser.email
        it.password to "hello_it_me"
        it.githubUserName to githubUser.login
        it.githubUserId to githubUser.id
        it.accessToken to token
        it.tokenRefreshedAt to LocalDateTime.now()
        it.lastLoginAt to LocalDateTime.now()
        it.createdAt to LocalDateTime.now()
        it.updatedAt to LocalDateTime.now()
    } as Int
}

fun updateGitstarsUser(githubUser: GithubUser, oldAccessToken: String, newAccessToken: String): Int {
    return if (oldAccessToken != newAccessToken) {
        Users.update {
            it.userName to githubUser.name
            it.githubUserName to githubUser.login
            it.githubUserId to githubUser.id
            it.accessToken to newAccessToken
            it.tokenRefreshedAt to LocalDateTime.now()
            it.lastLoginAt to LocalDateTime.now()
            it.updatedAt to LocalDateTime.now()
        }
    } else {
        Users.update {
            it.userName to githubUser.name
            it.githubUserName to githubUser.login
            it.githubUserId to githubUser.id
            it.lastLoginAt to LocalDateTime.now()
        }
    }
}

fun getCurrentUserByGithubUserId(userId: Long): List<GitstarUser> {
    return Users.select()
        .where { Users.githubUserId eq userId }
        .map { row ->
            GitstarUser(
                id = row[Users.id]!!,
                userName = row[Users.userName]!!,
                email = row[Users.email]!!,
                githubUserName = row[Users.githubUserName]!!,
                githubUserId = row[Users.githubUserId]!!,
                accessToken = row[Users.accessToken]!!,
                createdAt = row[Users.createdAt]!!,
                updatedAt = row[Users.updatedAt]!!
            )
        }
}

fun insertRepo(stargazingResponse: StargazingResponse, userId: Int) {
    Repo.insert {
        it.userId to userId
        it.repoId to stargazingResponse.id
        it.repoName to stargazingResponse.name
        it.githubLink to stargazingResponse.url
        it.githubDescription to stargazingResponse.description
        it.starCount to stargazingResponse.stargazersCount
        it.ownerAvatarUrl to stargazingResponse.owner.avatarUrl
    }
}