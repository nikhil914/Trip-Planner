package app.tripplanner.fieldguide

import android.app.Application
import app.tripplanner.fieldguide.data.FieldDb
import app.tripplanner.fieldguide.data.ItineraryRepository
import app.tripplanner.fieldguide.data.Session
import app.tripplanner.fieldguide.data.Vault
import app.tripplanner.fieldguide.sync.SyncApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

/** Hand-wired dependencies. A DI library is not worth its size here. */
class AppGraph(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db: FieldDb = FieldDb.open(app)
    val session = Session(app)
    val api = SyncApi(session)
    val vault = Vault(app, api)
    val repository = ItineraryRepository(app, db, api, session, vault)

    /** One-line messages for the user, shown as a snackbar when the app is open. */
    val notices = MutableSharedFlow<String>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
}

class FieldGuideApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}
