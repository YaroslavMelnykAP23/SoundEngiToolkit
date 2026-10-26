package com.example.soundengitoolkit

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** One saved recording: metadata in the database, audio in filesDir/projects/<wavFile>. */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val bpm: Float,
    val cursorPos: Int,
    val loopA: Int,
    val loopB: Int,
    val loopOn: Boolean,
    val durationSamples: Int,
    val wavFile: String,
    val effectsJson: String
)

/** One executed AI command. */
@Entity(tableName = "ai_history")
data class AiEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val prompt: String,
    val json: String,
    val log: String,
    val source: String,
    val ok: Boolean,
    val createdAt: Long
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    fun all(): Flow<List<ProjectEntity>>
    @Insert suspend fun insert(p: ProjectEntity): Long
    @Delete suspend fun delete(p: ProjectEntity)
}

@Dao
interface AiDao {
    @Query("SELECT * FROM ai_history ORDER BY createdAt DESC LIMIT 30")
    fun recent(): Flow<List<AiEntryEntity>>
    @Insert suspend fun insert(e: AiEntryEntity)
    @Query("DELETE FROM ai_history WHERE id NOT IN (SELECT id FROM ai_history ORDER BY createdAt DESC LIMIT 200)")
    suspend fun trim()
    @Query("DELETE FROM ai_history")
    suspend fun clear()
}

@Database(entities = [ProjectEntity::class, AiEntryEntity::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun projects(): ProjectDao
    abstract fun ai(): AiDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "soundengitoolkit.db")
                .build().also { inst = it }
        }
    }
}
