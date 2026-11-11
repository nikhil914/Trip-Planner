package app.tripplanner.fieldguide.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TripEntity::class,
        TripDayEntity::class,
        TimelineBlockEntity::class,
        ContextualItemEntity::class,
        AttachmentEntity::class,
        OutboxEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class FieldDb : RoomDatabase() {
    abstract fun itineraryDao(): ItineraryDao

    companion object {
        fun open(context: Context): FieldDb =
            Room.databaseBuilder(context.applicationContext, FieldDb::class.java, "field-guide.db").build()
    }
}
