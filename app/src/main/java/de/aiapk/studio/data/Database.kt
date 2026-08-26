package de.aiapk.studio.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val packageName: String,
    val type: String,
    val path: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastBuildStatus: String = "Bereit",
    val versionName: String = "0.1.0",
    val versionCode: Int = 1
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [ForeignKey(
        entity = ProjectEntity::class,
        parentColumns = ["id"], childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("projectId")]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val role: String,
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val baseUrl: String,
    val model: String,
    val keyAlias: String,
    val enabled: Boolean = true
)

@Entity(tableName = "builds", indices = [Index("projectId")])
data class BuildEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val success: Boolean,
    val output: String,
    val apkPath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface AppDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id=:id LIMIT 1")
    fun observeProject(id: Long): Flow<ProjectEntity?>
    @Query("SELECT * FROM projects WHERE id=:id LIMIT 1")
    suspend fun getProject(id: Long): ProjectEntity?
    @Query("UPDATE projects SET lastBuildStatus=:status, updatedAt=(strftime('%s','now') * 1000) WHERE id=:id")
    suspend fun updateProjectStatus(id: Long, status: String)

    @Insert suspend fun insertProject(project: ProjectEntity): Long
    @Update suspend fun updateProject(project: ProjectEntity)
    @Delete suspend fun deleteProject(project: ProjectEntity)

    @Query("SELECT * FROM chat_messages WHERE projectId=:projectId ORDER BY createdAt ASC")
    fun observeMessages(projectId: Long): Flow<List<ChatMessageEntity>>
    @Insert suspend fun insertMessage(message: ChatMessageEntity)

    @Query("SELECT * FROM providers ORDER BY id ASC")
    fun observeProviders(): Flow<List<ProviderEntity>>
    @Query("SELECT * FROM providers WHERE enabled=1 ORDER BY id ASC LIMIT 1")
    suspend fun activeProvider(): ProviderEntity?
    @Query("SELECT * FROM providers WHERE id=:id LIMIT 1")
    suspend fun getProvider(id: Long): ProviderEntity?
    @Insert suspend fun insertProvider(provider: ProviderEntity): Long
    @Update suspend fun updateProvider(provider: ProviderEntity)
    @Delete suspend fun deleteProvider(provider: ProviderEntity)

    @Query("SELECT * FROM builds WHERE projectId=:projectId ORDER BY createdAt DESC LIMIT 20")
    fun observeBuilds(projectId: Long): Flow<List<BuildEntity>>
    @Query("SELECT * FROM builds WHERE projectId=:projectId ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestBuild(projectId: Long): BuildEntity?
    @Insert suspend fun insertBuild(build: BuildEntity)
}

@Database(
    entities = [ProjectEntity::class, ChatMessageEntity::class, ProviderEntity::class, BuildEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao
    companion object {
        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "ai_apk_studio.db"
        ).fallbackToDestructiveMigration().build()
    }
}
