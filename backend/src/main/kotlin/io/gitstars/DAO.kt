package main.kotlin.io.gitstars

import me.liuwj.ktorm.database.Database
import me.liuwj.ktorm.dsl.*
import me.liuwj.ktorm.schema.*
import me.liuwj.ktorm.support.postgresql.PostgreSqlDialect
import java.time.LocalDateTime
import java.util.*

object DAO {

    val db = Database.connect(
        url = "jdbc:postgresql://localhost:5432/gitstars",
        driver = "org.postgresql.Driver",
        user = "gitstars_admin",
        password = "localadmin",
        dialect = PostgreSqlDialect()
    )

    object UsersTable : Table<Nothing>("users") {
        val id by uuid("id").primaryKey()
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

    object RepoTable : Table<Nothing>("repo") {
        val id by uuid("id").primaryKey()
        val userId by uuid("user_id")
        val repoId by long("repo_id")
        val repoName by text("repo_name")
        val githubLink by text("github_link")
        val githubDescription by text("github_description")
        val starCount by int("star_count")
        val ownerAvatarUrl by text("owner_avatar_url")
        val metadata by jsonb("metadata", typeRef<Metadata>())
    }

    object RepoSyncJobsTable : Table<Nothing>("repo_sync_jobs") {
        val id by uuid("id").primaryKey()
        val userId by uuid("user_id")
        val completed by boolean("completed")
        val createdAt by datetime("created_at")
    }

    fun getUserToken(userId: UUID): String {
        return UsersTable.select(UsersTable.accessToken)
            .where { UsersTable.id eq userId }
            .map { row -> row[UsersTable.accessToken]!! }[0]
    }

    fun insertGitstarsUser(githubUser: GithubUser, token: String): Int {
        return UsersTable.insert {
            it.id to UUID.randomUUID()
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
            UsersTable.update {
                it.userName to githubUser.name
                it.githubUserName to githubUser.login
                it.githubUserId to githubUser.id
                it.accessToken to newAccessToken
                it.tokenRefreshedAt to LocalDateTime.now()
                it.lastLoginAt to LocalDateTime.now()
                it.updatedAt to LocalDateTime.now()
            }
        } else {
            UsersTable.update {
                it.userName to githubUser.name
                it.githubUserName to githubUser.login
                it.githubUserId to githubUser.id
                it.lastLoginAt to LocalDateTime.now()
            }
        }
    }

    fun getGitStarUser(userId: UUID): List<GitstarUser> {
        return UsersTable.select()
            .where { UsersTable.id eq userId }
            .map { row ->
                GitstarUser(
                    id = row[UsersTable.id]!!,
                    userName = row[UsersTable.userName]!!,
                    email = row[UsersTable.email]!!,
                    githubUserName = row[UsersTable.githubUserName]!!,
                    githubUserId = row[UsersTable.githubUserId]!!,
                    accessToken = row[UsersTable.accessToken]!!,
                    createdAt = row[UsersTable.createdAt]!!,
                    updatedAt = row[UsersTable.updatedAt]!!
                )
            }
    }

    fun getCurrentUserByGithubUserId(githubUserId: Long): List<GitstarUser> {
        return UsersTable.select()
            .where { UsersTable.githubUserId eq githubUserId }
            .map { row ->
                GitstarUser(
                    id = row[UsersTable.id]!!,
                    userName = row[UsersTable.userName]!!,
                    email = row[UsersTable.email]!!,
                    githubUserName = row[UsersTable.githubUserName]!!,
                    githubUserId = row[UsersTable.githubUserId]!!,
                    accessToken = row[UsersTable.accessToken]!!,
                    createdAt = row[UsersTable.createdAt]!!,
                    updatedAt = row[UsersTable.updatedAt]!!
                )
            }
    }

    fun insertRepo(stargazingResponse: StargazingResponse, userId: UUID) {
        RepoTable.insert {
            it.id to UUID.randomUUID()
            it.userId to userId
            it.repoId to stargazingResponse.id
            it.repoName to stargazingResponse.name
            it.githubLink to stargazingResponse.url
            it.githubDescription to stargazingResponse.description
            it.starCount to stargazingResponse.stargazersCount
            it.ownerAvatarUrl to stargazingResponse.owner.avatarUrl
        }
    }

    fun getUserRepos(userId: UUID): List<GitStarsRepo> {
        return RepoTable.select()
            .where { RepoTable.userId eq userId }
            .map { row ->
                GitStarsRepo(
                    id = row[RepoTable.id]!!,
                    userId = row[RepoTable.userId]!!,
                    repoName = row[RepoTable.repoName]!!,
                    githubLink = row[RepoTable.githubLink]!!,
                    githubDescription = row[RepoTable.githubDescription],
                    ownerAvatarUrl = row[RepoTable.ownerAvatarUrl]!!,
                    metadata = row[RepoTable.metadata]
                )
            }
    }


    fun insertTagsInRepo(repoId: UUID, metadata: Metadata): GitStarsRepo {
        RepoTable.update {
            it.metadata to metadata
            where { it.id eq repoId }
        }
        return RepoTable.select()
            .where { RepoTable.id eq repoId }
            .map { row ->
                GitStarsRepo(
                    id = row[RepoTable.id]!!,
                    userId = row[RepoTable.userId]!!,
                    repoName = row[RepoTable.repoName]!!,
                    githubLink = row[RepoTable.githubLink]!!,
                    githubDescription = row[RepoTable.githubDescription]!!,
                    ownerAvatarUrl = row[RepoTable.ownerAvatarUrl]!!,
                    metadata = row[RepoTable.metadata]!!
                )
            }[0]
    }

    fun getRepoSyncJobUsingId(jobId: UUID): RepoSyncJob {
        return RepoSyncJobsTable.select()
            .where { RepoSyncJobsTable.id eq jobId }
            .map { row ->
                RepoSyncJob(
                    row[RepoSyncJobsTable.id]!!,
                    row[RepoSyncJobsTable.userId]!!,
                    row[RepoSyncJobsTable.completed]!!,
                    row[RepoSyncJobsTable.createdAt]!!)
            }[0]
    }

    fun getMostRecentUnfinishedRepoSyncJob(userId: UUID): RepoSyncJob {
        return RepoSyncJobsTable.select()
            .where {
                RepoSyncJobsTable.userId eq userId
                RepoSyncJobsTable.completed eq false
            }
            .orderBy(RepoSyncJobsTable.createdAt.desc())
            .map { row ->
                RepoSyncJob(
                    row[RepoSyncJobsTable.id]!!,
                    row[RepoSyncJobsTable.userId]!!,
                    row[RepoSyncJobsTable.completed]!!,
                    row[RepoSyncJobsTable.createdAt]!!)
            }[0]
    }

    fun completeRepoSyncJob(jobId: UUID) {
        RepoSyncJobsTable.update {
            it.completed to true
            where { RepoSyncJobsTable.id eq jobId }
        }
    }

    fun createNewRepoSyncJob(userId: UUID) {
        RepoSyncJobsTable.insert {
            it.id to UUID.randomUUID()
            it.userId to userId
            it.completed to false
        }
    }

}
