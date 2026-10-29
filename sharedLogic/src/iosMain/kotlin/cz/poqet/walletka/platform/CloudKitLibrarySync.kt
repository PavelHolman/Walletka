package cz.poqet.walletka.platform

import cz.poqet.walletka.data.CardCodec
import cz.poqet.walletka.data.CardLibrary
import cz.poqet.walletka.data.RemoteChange
import cz.poqet.walletka.data.StoredCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import platform.CloudKit.CKAccountStatusAvailable
import platform.CloudKit.CKAsset
import platform.CloudKit.CKContainer
import platform.CloudKit.CKCurrentUserDefaultName
import platform.CloudKit.CKErrorServerRecordChanged
import platform.CloudKit.CKErrorUnknownItem
import platform.CloudKit.CKErrorZoneNotFound
import platform.CloudKit.CKRecord
import platform.CloudKit.CKRecordChangedErrorServerRecordKey
import platform.CloudKit.CKRecordID
import platform.CloudKit.CKRecordValueProtocol
import platform.CloudKit.CKRecordZone
import platform.CloudKit.CKRecordZoneID
import platform.CloudKit.CKSyncEngine
import platform.CloudKit.CKSyncEngineAccountChangeType.CKSyncEngineAccountChangeTypeSignOut
import platform.CloudKit.CKSyncEngineConfiguration
import platform.CloudKit.CKSyncEngineDelegateProtocol
import platform.CloudKit.CKSyncEngineEvent
import platform.CloudKit.CKSyncEngineEventType.CKSyncEngineEventTypeAccountChange
import platform.CloudKit.CKSyncEngineEventType.CKSyncEngineEventTypeDidFetchChanges
import platform.CloudKit.CKSyncEngineEventType.CKSyncEngineEventTypeDidSendChanges
import platform.CloudKit.CKSyncEngineEventType.CKSyncEngineEventTypeFetchedDatabaseChanges
import platform.CloudKit.CKSyncEngineEventType.CKSyncEngineEventTypeFetchedRecordZoneChanges
import platform.CloudKit.CKSyncEngineEventType.CKSyncEngineEventTypeSentDatabaseChanges
import platform.CloudKit.CKSyncEngineEventType.CKSyncEngineEventTypeSentRecordZoneChanges
import platform.CloudKit.CKSyncEngineEventType.CKSyncEngineEventTypeStateUpdate
import platform.CloudKit.CKSyncEngineEventType.CKSyncEngineEventTypeWillFetchChanges
import platform.CloudKit.CKSyncEngineEventType.CKSyncEngineEventTypeWillSendChanges
import platform.CloudKit.CKSyncEngineFailedRecordSave
import platform.CloudKit.CKSyncEngineFailedZoneSave
import platform.CloudKit.CKSyncEngineFetchedRecordDeletion
import platform.CloudKit.CKSyncEngineFetchedZoneDeletion
import platform.CloudKit.CKSyncEnginePendingRecordZoneChange
import platform.CloudKit.CKSyncEnginePendingRecordZoneChangeType.CKSyncEnginePendingRecordZoneChangeTypeDeleteRecord
import platform.CloudKit.CKSyncEnginePendingRecordZoneChangeType.CKSyncEnginePendingRecordZoneChangeTypeSaveRecord
import platform.CloudKit.CKSyncEnginePendingZoneSave
import platform.CloudKit.CKSyncEngineRecordZoneChangeBatch
import platform.CloudKit.CKSyncEngineSendChangesContext
import platform.CloudKit.CKSyncEngineStateSerialization
import platform.CloudKit.CKSyncEngineZoneDeletionReason.CKSyncEngineZoneDeletionReasonEncryptedDataReset
import platform.CloudKit.accountStatusWithCompletionHandler
import platform.CloudKit.privateCloudDatabase
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSLog
import platform.Foundation.NSFileManager
import platform.Foundation.NSKeyedArchiver
import platform.Foundation.NSKeyedUnarchiver
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.darwin.NSObject

/**
 * Syncs the card library through the user's private iCloud database with `CKSyncEngine`.
 *
 * One `Card` record per card (its JSON from [CardCodec], `updatedAt`, the photo, banner and logo image as assets, each absent when the card has none; a deleted card
 * stays as a tombstone record with `deletedAt`), plus one `Order` record with the manual order. What arrives goes
 * through [CardLibrary.applyRemote], so the shared merge rules decide; what the library reports as changed (or newer
 * than what arrived) is queued for upload. Without an iCloud account everything stays local.
 */
internal class CloudKitLibrarySync(
    private val containerIdentifier: String,
    /** Directory for the engine's state and the records' server fields. */
    private val stateDirectory: String,
) : LibrarySync {

    private val defaults = NSUserDefaults.standardUserDefaults
    private val fileManager = NSFileManager.defaultManager
    private val zoneID = CKRecordZoneID(zoneName = ZONE, ownerName = CKCurrentUserDefaultName)
    private val stateFile = "$stateDirectory/engine-state"
    private val recordsFile = "$stateDirectory/records"

    private val _status = MutableStateFlow(if (isEnabled) CloudSyncStatus.SYNCING else CloudSyncStatus.OFF)
    override val status: StateFlow<CloudSyncStatus> = _status.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var library: CardLibrary? = null
    private var engine: CKSyncEngine? = null

    /** The account check before the engine starts is under way; a second engine must not be created meanwhile. */
    private var isConnecting = false

    /** CKSyncEngine holds its delegate weakly. */
    private val delegate = Delegate()

    /** Server fields (change tags) of the records last seen, by record name, so saves don't conflict needlessly. */
    private val serverFields = mutableMapOf<String, NSData>()

    private val isEnabled: Boolean
        get() = defaults.objectForKey(ENABLED_KEY) == null || defaults.boolForKey(ENABLED_KEY)

    override fun start(library: CardLibrary) {
        this.library = library
        library.changeListener = { ids, orderChanged -> queue(ids, orderChanged) }
        if (isEnabled) connect()
        // Pushes don't always arrive (e.g. while the app was not running): catch up whenever the app comes back.
        NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = null,
        ) { _ -> syncNow() }
    }

    override fun syncNow() {
        val engine = engine ?: return if (isEnabled) connect() else Unit
        engine.fetchChangesWithCompletionHandler { error ->
            if (error != null) fail("fetch", error) else engine.sendChangesWithCompletionHandler { sendError ->
                if (sendError != null) fail("send", sendError)
            }
        }
    }

    private fun fail(operation: String, error: NSError) {
        log("$operation failed: ${error.code} ${error.localizedDescription}")
        _lastError.value = error.localizedDescription
        _status.value = CloudSyncStatus.FAILED
    }

    override fun setEnabled(enabled: Boolean) {
        if (enabled == isEnabled && (engine != null) == enabled) return
        defaults.setBool(enabled, forKey = ENABLED_KEY)
        if (enabled) {
            connect(uploadAll = true)
        } else {
            engine?.cancelOperationsWithCompletionHandler(null)
            engine = null
            _status.value = CloudSyncStatus.OFF
        }
    }

    /** Starts the engine once iCloud is available; [uploadAll] queues every card (first start, re-enabled). */
    private fun connect(uploadAll: Boolean = false) {
        if (isConnecting) return
        isConnecting = true
        _status.value = CloudSyncStatus.SYNCING
        val container = CKContainer.containerWithIdentifier(containerIdentifier)
        container.accountStatusWithCompletionHandler { accountStatus, error ->
            log("account status $accountStatus ${error?.localizedDescription.orEmpty()}")
            isConnecting = false
            if (!isEnabled || engine != null) return@accountStatusWithCompletionHandler
            if (accountStatus != CKAccountStatusAvailable) {
                _lastError.value = error?.localizedDescription
                _status.value = CloudSyncStatus.UNAVAILABLE
                return@accountStatusWithCompletionHandler
            }
            fileManager.createDirectoryAtPath(stateDirectory, withIntermediateDirectories = true, attributes = null, error = null)
            loadServerFields()
            val state = loadState()
            val configuration = CKSyncEngineConfiguration(
                database = container.privateCloudDatabase,
                stateSerialization = state,
                delegate = delegate,
            )
            val engine = CKSyncEngine(configuration)
            this.engine = engine
            if (state == null || uploadAll) queueEverything(engine)
            engine.fetchChangesWithCompletionHandler { fetchError -> fetchError?.let { fail("fetch", it) } }
        }
    }

    private fun queue(ids: Set<String>, orderChanged: Boolean) {
        val engine = engine ?: return
        val changes = ids.map { save(recordID(it)) } + listOfNotNull(save(recordID(ORDER_RECORD)).takeIf { orderChanged })
        if (changes.isNotEmpty()) engine.state.addPendingRecordZoneChanges(changes)
    }

    private fun queueEverything(engine: CKSyncEngine) {
        val library = library ?: return
        runBlocking { library.load() }
        engine.state.addPendingDatabaseChanges(listOf(CKSyncEnginePendingZoneSave(zone = CKRecordZone(zoneID = zoneID))))
        val ids = library.cards.value.map { it.id } + library.syncMetadata.value.tombstones.keys + ORDER_RECORD
        engine.state.addPendingRecordZoneChanges(ids.map { save(recordID(it)) })
    }

    private fun save(id: CKRecordID) = CKSyncEnginePendingRecordZoneChange(recordID = id, type = CKSyncEnginePendingRecordZoneChangeTypeSaveRecord)

    private fun recordID(name: String) = CKRecordID(recordName = name, zoneID = zoneID)

    // MARK: Engine events (called serially on the engine's queue)

    private fun handle(engine: CKSyncEngine, event: CKSyncEngineEvent) {
        log("event ${event.type}: ${event.description}")
        when (event.type) {
            CKSyncEngineEventTypeStateUpdate -> saveState(event.stateUpdateEvent.stateSerialization)

            CKSyncEngineEventTypeAccountChange -> if (event.accountChangeEvent.changeType == CKSyncEngineAccountChangeTypeSignOut) {
                // The cards stay on this device; a later account starts from scratch.
                fileManager.removeItemAtPath(stateFile, error = null)
                fileManager.removeItemAtPath(recordsFile, error = null)
                serverFields.clear()
                this.engine = null
                _status.value = CloudSyncStatus.UNAVAILABLE
            } else {
                serverFields.clear()
                queueEverything(engine)
            }

            CKSyncEngineEventTypeFetchedDatabaseChanges -> {
                val deletion = event.fetchedDatabaseChangesEvent.deletions
                    .filterIsInstance<CKSyncEngineFetchedZoneDeletion>()
                    .firstOrNull { it.zoneID.zoneName == ZONE }
                if (deletion != null) {
                    serverFields.clear()
                    if (deletion.reason == CKSyncEngineZoneDeletionReasonEncryptedDataReset) {
                        queueEverything(engine)
                    } else {
                        // The user deleted Walletka's iCloud data: keep the cards here, but stop syncing them.
                        setEnabled(false)
                    }
                }
            }

            CKSyncEngineEventTypeFetchedRecordZoneChanges -> {
                val fetched = event.fetchedRecordZoneChangesEvent
                val records = fetched.modifications.filterIsInstance<CKRecord>()
                records.forEach(::rememberServerFields)
                // Walletka never hard-deletes records; treat one as the oldest possible deletion.
                val deletions = fetched.deletions.filterIsInstance<CKSyncEngineFetchedRecordDeletion>()
                    .filter { it.recordType == CARD_TYPE }
                    .map { RemoteChange.Deleted(it.recordID.recordName, deletedAt = 0) }
                apply(records.mapNotNull(::toChange) + deletions)
                persistServerFields()
            }

            CKSyncEngineEventTypeSentDatabaseChanges -> {
                val failure = event.sentDatabaseChangesEvent.failedZoneSaves.filterIsInstance<CKSyncEngineFailedZoneSave>().firstOrNull()
                if (failure != null) fail("zone save", failure.error)
            }

            CKSyncEngineEventTypeSentRecordZoneChanges -> {
                val sent = event.sentRecordZoneChangesEvent
                sent.savedRecords.filterIsInstance<CKRecord>().forEach(::rememberServerFields)
                val conflicts = mutableListOf<CKRecord>()
                val retry = mutableListOf<CKSyncEnginePendingRecordZoneChange>()
                var failed = false
                for (failure in sent.failedRecordSaves.filterIsInstance<CKSyncEngineFailedRecordSave>()) {
                    val id = failure.record.recordID
                    log("save failed ${id.recordName}: ${failure.error.code} ${failure.error.localizedDescription}")
                    when (failure.error.code) {
                        CKErrorServerRecordChanged -> {
                            (failure.error.userInfo[CKRecordChangedErrorServerRecordKey] as? CKRecord)?.let(conflicts::add)
                        }

                        CKErrorZoneNotFound -> {
                            engine.state.addPendingDatabaseChanges(listOf(CKSyncEnginePendingZoneSave(zone = CKRecordZone(zoneID = zoneID))))
                            serverFields.remove(id.recordName)
                            retry += save(id)
                        }

                        CKErrorUnknownItem -> {
                            serverFields.remove(id.recordName)
                            retry += save(id)
                        }

                        else -> {
                            failed = true
                            _lastError.value = failure.error.localizedDescription
                        }
                    }
                }
                conflicts.forEach(::rememberServerFields)
                // Merging the server's version re-queues the cards that are newer here (CardLibrary.changeListener).
                apply(conflicts.mapNotNull(::toChange))
                if (retry.isNotEmpty()) engine.state.addPendingRecordZoneChanges(retry)
                persistServerFields()
                if (failed) _status.value = CloudSyncStatus.FAILED
            }

            CKSyncEngineEventTypeWillFetchChanges, CKSyncEngineEventTypeWillSendChanges -> {
                // A new attempt: the previous failure no longer stands.
                _lastError.value = null
                _status.value = CloudSyncStatus.SYNCING
            }

            CKSyncEngineEventTypeDidFetchChanges, CKSyncEngineEventTypeDidSendChanges ->
                if (_status.value != CloudSyncStatus.FAILED || _lastError.value == null) {
                    _lastError.value = null
                    _status.value = CloudSyncStatus.UP_TO_DATE
                }

            else -> Unit
        }
    }

    private fun nextBatch(engine: CKSyncEngine, context: CKSyncEngineSendChangesContext): CKSyncEngineRecordZoneChangeBatch? {
        val scope = context.options.scope
        val pending = engine.state.pendingRecordZoneChanges
            .filterIsInstance<CKSyncEnginePendingRecordZoneChange>()
            .filter { scope.containsPendingRecordZoneChange(it) }
            .take(BATCH_SIZE)
        if (pending.isEmpty()) return null
        return CKSyncEngineRecordZoneChangeBatch(pendingChanges = pending) { id ->
            val record = id?.let(::record)
            // Nothing to send for it (any more): drop it from the queue.
            if (record == null && id != null) {
                engine.state.removePendingRecordZoneChanges(
                    listOf(
                        save(id),
                        CKSyncEnginePendingRecordZoneChange(recordID = id, type = CKSyncEnginePendingRecordZoneChangeTypeDeleteRecord),
                    ),
                )
            }
            record
        }
    }

    private fun apply(changes: List<RemoteChange>) {
        val library = library ?: return
        if (changes.isEmpty()) return
        runBlocking { library.applyRemote(changes) }
    }

    // MARK: Records

    /** The record for [id] as this device has it, or `null` when there is nothing to send. */
    private fun record(id: CKRecordID): CKRecord? {
        val library = library ?: return null
        val name = id.recordName
        if (name == ORDER_RECORD) {
            return newRecord(ORDER_TYPE, id).apply {
                put("ids", library.cards.value.joinToString("\n") { it.id })
                put("updatedAt", NSNumber(longLong = library.syncMetadata.value.orderUpdatedAt))
            }
        }
        val card = library.cards.value.firstOrNull { it.id == name }
        if (card != null) return cardRecord(id, card)
        val deletedAt = library.syncMetadata.value.tombstones[name] ?: return null
        return newRecord(CARD_TYPE, id).apply {
            put("deletedAt", NSNumber(longLong = deletedAt))
            put("card", null)
            put("photo", null)
            put("banner", null)
            put("logo", null)
        }
    }

    private fun cardRecord(id: CKRecordID, card: StoredCard) = newRecord(CARD_TYPE, id).apply {
        put("card", CardCodec.encode(card))
        put("updatedAt", NSNumber(longLong = card.updatedAt))
        put("deletedAt", null)
        put("photo", card.form.photo?.let(::asset))
        put("banner", card.form.banner?.let(::asset))
        put("logo", card.form.logo?.image?.let(::asset))
    }

    private fun asset(path: String): CKAsset? =
        if (fileManager.fileExistsAtPath(path)) CKAsset(fileURL = NSURL.fileURLWithPath(path)) else null

    /** A record carrying the server fields seen last, so CloudKit takes it as an update of that version. */
    private fun newRecord(type: String, id: CKRecordID): CKRecord {
        serverFields[id.recordName]?.let { data ->
            val coder = NSKeyedUnarchiver(forReadingFromData = data, error = null)
            coder.requiresSecureCoding = true
            val record = runCatching { CKRecord(coder = coder) }.getOrNull()
            coder.finishDecoding()
            if (record != null) return record
        }
        return CKRecord(recordType = type, recordID = id)
    }

    private fun toChange(record: CKRecord): RemoteChange? {
        val library = library ?: return null
        return when (record.recordType) {
            ORDER_TYPE -> {
                val ids = (record.objectForKey("ids") as? String)?.split("\n")?.filter { it.isNotEmpty() } ?: return null
                RemoteChange.Order(ids, record.long("updatedAt") ?: 0)
            }

            CARD_TYPE -> {
                val id = record.recordID.recordName
                record.long("deletedAt")?.let { return RemoteChange.Deleted(id, it) }
                val text = record.objectForKey("card") as? String ?: return null
                val card = CardCodec.decode(text, WalletPlatform.APPLE_WALLET) { library.imageFile(it).toString() } ?: return null
                // The assets are temporary files; keep copies under the names the card refers to.
                card.form.photo?.let { copyAsset(record.objectForKey("photo") as? CKAsset, it) }
                card.form.logo?.image?.let { copyAsset(record.objectForKey("logo") as? CKAsset, it) }
                card.form.banner?.let { copyAsset(record.objectForKey("banner") as? CKAsset, it) }
                RemoteChange.Card(card)
            }

            else -> null
        }
    }

    private fun copyAsset(asset: CKAsset?, target: String) {
        val source = asset?.fileURL?.path ?: return
        fileManager.removeItemAtPath(target, error = null)
        fileManager.copyItemAtPath(source, toPath = target, error = null)
    }

    /** Sets a record field; strings become `NSString`s, which CloudKit takes as record values. */
    private fun CKRecord.put(key: String, value: Any?) {
        val objcValue: Any? = if (value is String) NSString.create(string = value) else value
        setObject(objcValue as CKRecordValueProtocol?, forKey = key)
    }

    private fun CKRecord.long(key: String): Long? = (objectForKey(key) as? NSNumber)?.longLongValue

    private fun rememberServerFields(record: CKRecord) {
        val archiver = NSKeyedArchiver(requiringSecureCoding = true)
        record.encodeSystemFieldsWithCoder(archiver)
        archiver.finishEncoding()
        serverFields[record.recordID.recordName] = archiver.encodedData
    }

    // MARK: Persistence

    private fun loadState(): CKSyncEngineStateSerialization? {
        val data = NSData.dataWithContentsOfFile(stateFile) ?: return null
        return NSKeyedUnarchiver.unarchivedObjectOfClass(CKSyncEngineStateSerialization, fromData = data, error = null)
            as? CKSyncEngineStateSerialization
    }

    private fun saveState(state: CKSyncEngineStateSerialization) {
        NSKeyedArchiver.archivedDataWithRootObject(state, requiringSecureCoding = true, error = null)
            ?.writeToFile(stateFile, atomically = true)
    }

    private fun loadServerFields() {
        val data = NSData.dataWithContentsOfFile(recordsFile) ?: return
        val stored = NSKeyedUnarchiver.unarchiveObjectWithData(data) as? Map<*, *> ?: return
        stored.forEach { (key, value) -> if (key is String && value is NSData) serverFields[key] = value }
    }

    private fun persistServerFields() {
        NSKeyedArchiver.archivedDataWithRootObject(serverFields.toMap(), requiringSecureCoding = false, error = null)
            ?.writeToFile(recordsFile, atomically = true)
    }

    private inner class Delegate : NSObject(), CKSyncEngineDelegateProtocol {
        override fun syncEngine(syncEngine: CKSyncEngine, handleEvent: CKSyncEngineEvent) {
            handle(syncEngine, handleEvent)
        }

        override fun syncEngine(
            syncEngine: CKSyncEngine,
            nextRecordZoneChangeBatchForContext: CKSyncEngineSendChangesContext,
        ): CKSyncEngineRecordZoneChangeBatch? = nextBatch(syncEngine, nextRecordZoneChangeBatchForContext)
    }

    // Kotlin strings can't be NSLog's variadic arguments, so the message is the (escaped) format itself.
    private fun log(message: String) {
        NSLog("[CloudSync] " + message.replace("%", "%%"))
        println("[CloudSync] $message") // also on the console of a launch from Xcode / devicectl
    }

    private companion object {
        const val ZONE = "Library"
        const val CARD_TYPE = "Card"
        const val ORDER_TYPE = "Order"
        const val ORDER_RECORD = "order"
        const val ENABLED_KEY = "WalletkaCloudSyncEnabled"
        const val BATCH_SIZE = 50
    }
}
