package app.kochi.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "recipe_summary")
data class RecipeSummaryEntity(
    @PrimaryKey val id: String,
    val slug: String,
    val name: String,
    val json: String,
    val addedOn: String,
)

@Entity(tableName = "recipe_detail")
data class RecipeDetailEntity(
    @PrimaryKey val slug: String,
    val id: String,
    val json: String,
    val cachedAt: Long,
)

@Entity(tableName = "cache")
data class CacheEntity(
    @PrimaryKey val key: String,
    val json: String,
    val cachedAt: Long,
)

@Entity(tableName = "pending_change")
data class PendingChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: String,
    val itemId: String,
    val kind: String,
    val payload: String,
    val createdAt: Long,
)

@Dao
interface RecipeDao {
    @Upsert
    suspend fun upsertSummaries(items: List<RecipeSummaryEntity>)

    @Query("SELECT * FROM recipe_summary WHERE name LIKE '%' || :query || '%' ORDER BY addedOn DESC, name LIMIT :limit")
    suspend fun search(query: String, limit: Int): List<RecipeSummaryEntity>

    @Upsert
    suspend fun upsertDetail(item: RecipeDetailEntity)

    @Query("SELECT * FROM recipe_detail WHERE slug = :slug")
    suspend fun detail(slug: String): RecipeDetailEntity?

    @Query("SELECT slug FROM recipe_detail")
    suspend fun cachedSlugs(): List<String>

    @Query("SELECT COUNT(*) FROM recipe_detail")
    fun detailCount(): Flow<Int>

    @Query("DELETE FROM recipe_detail WHERE slug = :slug")
    suspend fun deleteDetail(slug: String)

    @Query("DELETE FROM recipe_summary WHERE slug = :slug")
    suspend fun deleteSummary(slug: String)
}

@Dao
interface CacheDao {
    @Query("SELECT * FROM cache WHERE `key` = :key")
    suspend fun get(key: String): CacheEntity?

    @Query("SELECT * FROM cache WHERE `key` = :key")
    fun observe(key: String): Flow<CacheEntity?>

    @Upsert
    suspend fun put(entry: CacheEntity)

    @Query("DELETE FROM cache WHERE `key` = :key")
    suspend fun delete(key: String)
}

@Dao
interface PendingChangeDao {
    @Insert
    suspend fun insert(change: PendingChangeEntity)

    @Query("SELECT * FROM pending_change ORDER BY id")
    suspend fun all(): List<PendingChangeEntity>

    @Query("SELECT COUNT(*) FROM pending_change WHERE listId = :listId")
    fun countFor(listId: String): Flow<Int>

    @Query("DELETE FROM pending_change WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(
    entities = [RecipeSummaryEntity::class, RecipeDetailEntity::class, CacheEntity::class, PendingChangeEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class KochiDatabase : RoomDatabase() {
    abstract fun recipes(): RecipeDao

    abstract fun cache(): CacheDao

    abstract fun pendingChanges(): PendingChangeDao
}
