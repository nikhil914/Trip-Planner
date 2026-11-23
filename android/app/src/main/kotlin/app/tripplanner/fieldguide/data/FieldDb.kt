package app.tripplanner.fieldguide.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TripEntity::class,
        TripDayEntity::class,
        TimelineBlockEntity::class,
        ContextualItemEntity::class,
        AttachmentEntity::class,
        OutboxEntity::class,
        ExpenseEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class FieldDb : RoomDatabase() {
    abstract fun itineraryDao(): ItineraryDao

    companion object {
        fun open(context: Context): FieldDb =
            Room.databaseBuilder(context.applicationContext, FieldDb::class.java, "field-guide.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** Adds the trip budget, the shared-ledger switch and the expenses table. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `trips` ADD COLUMN `budget_minor` INTEGER")
                db.execSQL("ALTER TABLE `trips` ADD COLUMN `shared_ledger` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `expenses` (`id` TEXT NOT NULL, `trip_id` TEXT NOT NULL, `mine` INTEGER NOT NULL, " +
                        "`kind` TEXT NOT NULL, `day_id` TEXT, `block_id` TEXT, `occurred_at` INTEGER NOT NULL, " +
                        "`amount_minor` INTEGER NOT NULL, `currency` TEXT NOT NULL, `rate_to_home` REAL NOT NULL, " +
                        "`home_minor` INTEGER NOT NULL, `billed_home_minor` INTEGER, `method` TEXT NOT NULL, " +
                        "`category` TEXT NOT NULL, `merchant` TEXT, `note` TEXT, `prepaid` INTEGER NOT NULL, " +
                        "`paid_by` TEXT, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_trip_id` ON `expenses` (`trip_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_day_id` ON `expenses` (`day_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_block_id` ON `expenses` (`block_id`)")
            }
        }
    }
}
