package de.baumann.browser.database

import android.content.Context
import androidx.room.*
import de.baumann.browser.preference.ConfigManager

@Database(entities = [Bookmark::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY title COLLATE NOCASE ASC")
    suspend fun getAllBookmarks(): List<Bookmark>

    @Query("SELECT * FROM bookmarks WHERE isDirectory = 1 ORDER BY title COLLATE NOCASE ASC")
    suspend fun getBookmarkFolders(): List<Bookmark>

    @Query("SELECT * FROM bookmarks WHERE parent = :parentId ORDER BY title COLLATE NOCASE ASC")
    suspend fun getBookmarksByParent(parentId: Int): List<Bookmark>

    @Query("SELECT COUNT(id) FROM bookmarks WHERE url = :url")
    suspend fun existsUrl(url: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: Bookmark)

    @Delete
    suspend fun delete(bookmark: Bookmark)

    @Update
    suspend fun update(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks")
    suspend fun deleteAll()
}

class BookmarkManager(private val context: Context) {
    val database = Room.databaseBuilder(
        context,
        AppDatabase::class.java, "einkbro_db"
    ).build()

    val bookmarkDao = database.bookmarkDao()

    suspend fun migrateOldData() {
        val config = ConfigManager(context)
        if (config.dbVersion != 0) return

        //add bookmarks
        val db = BookmarkList(context).apply { open() }
        val cursor = db.fetchAllData(context)
        cursor.moveToFirst()
        while (!cursor.isAfterLast) {
            insert(
                title = cursor.getString(1),
                url = cursor.getString(2),
            )
            cursor.moveToNext()
        }
        cursor.close()
        db.close()

        config.dbVersion = 1
    }

    suspend fun getAllBookmarks(): List<Bookmark> = bookmarkDao.getAllBookmarks()

    suspend fun getBookmarks(parentId: Int = 0): List<Bookmark> = bookmarkDao.getBookmarksByParent(parentId)

    suspend fun getBookmarksByParent(parent: Int) = bookmarkDao.getBookmarksByParent(parent)

    suspend fun getBookmarkFolders(): List<Bookmark> = bookmarkDao.getBookmarkFolders()

    suspend fun insert(bookmark: Bookmark) = bookmarkDao.insert(bookmark)

    suspend fun insert(title: String, url: String) {
        if (existsUrl(url)) return

        bookmarkDao.insert(Bookmark(title, url))
    }

    suspend fun deleteAll() = bookmarkDao.deleteAll()

    suspend fun existsUrl(url: String): Boolean = bookmarkDao.existsUrl(url) > 0

    suspend fun insertDirectory(title: String, parentId: Int = 0) {
        bookmarkDao.insert(
            Bookmark(
                title = title,
                url = "",
                isDirectory = true,
                parent = parentId,
            )
        )
    }

    suspend fun delete(bookmark: Bookmark) = bookmarkDao.delete(bookmark)

    suspend fun update(bookmark: Bookmark) = bookmarkDao.update(bookmark)

    fun release() = database.close()
}