# Source review and fixes

Reviewed the supplied application Kotlin source, Android resources/manifest, Gradle configuration, bundled engine version, persistence and existing tests. The GitHub repository also contained a standalone marketing website; this deliverable is the complete Android application project.

| Finding | Change |
| --- | --- |
| History kept only an expiring remote thumbnail URL | Durable JPEG in filesDir plus persisted local path, remote/file/frame fallback and legacy backfill |
| Artwork could render as an empty cell after an image error | Permanent themed placeholder, local-file-first image request |
| Editing/shared URLs could race a previous extraction | ViewModel generation token, cancellable coroutine and interruptible native extraction |
| Engine initialization failure was ignored on fetch | Check readiness and surface a readable failure |
| Share received on Library/Settings was not opened | Switch to Home and consume the incoming URL |
| Queue restore could overwrite a newly enqueued item | Explicit readiness barrier before enqueue/worker startup |
| Queued cancellations were later cleared by executeOne | Check current queue membership; retain cancellation until the job finishes |
| Coroutine shutdown left native work running | Interruptible operations, tracked per-item jobs and native-process cleanup |
| Uncoordinated flow read/modify/write could lose changes | Atomic StateFlow updates and serialized persistence |
| File output search could select another job's file or a part | Isolated job directories, strict completed-output selection |
| Interrupted merge output could be delivered as success | Reject hidden/temp/part files; salvage merge writes a hidden temporary file and renames after success |
| Failed MP3 conversion could be treated as an M4A success | Require the requested audio output extension |
| Saving could fail but UI still report Done | Fail and retain the recoverable private media for Retry |
| Cancelling during post-save thumbnail fetch could duplicate media | Save, record and persist history as a cancellation-safe handoff; recover artwork afterward |
| Cleanup could run before history reached disk | Await durable persistence before deleting job media |
| Private completed media accumulated after public copy | Remove the completed job directory after durable publication |
| MediaStore null stream/publish failure left pending rows | Check stream/copy/update and delete incomplete rows on error |
| MediaStore collision suffix was not recorded | Query actual saved display name and retain its content URI |
| Open/share assumed raw filesystem access | Prefer scoped content URIs and show actionable errors |
| Delete could hide history even when storage deletion failed | Remove history only after successful/already-absent deletion |
| Worker idle stop could stop newly started work | Capture the completed start token and recheck pending work on the main dispatcher |
| Best selected AVC before higher resolution formats | Best video+audio selector prioritizes highest available quality |
| Per-height fallback could exceed the selected resolution | Exact-height selectors with bounded fallbacks |
| Audio-only metadata still offered a video download | Offer only formats appropriate to available streams |
| Direct-file metadata with no formats needed fallback | Recognize direct video/audio metadata |
| 4320p was labelled 4K | Correct vertical-resolution 8K mapping |
| Platform detection trusted matching text in path/query | Parse and compare the actual hostname |
| Invalid schemes/credential URLs reached the extractor | Validate HTTP(S), host and credentials before extraction |
| Multi-fragment shared links caused parsing errors | Remove the fragment before URI validation |
| Old bundled binary overwrote newer engine on restart | Compare installed marker version before applying the bundle |
| Library version preferences stayed stale after custom install | Read the installed version marker first |
| Update/download could overlap binary replacement | Concurrent engine read leases; update installation waits for exclusive access |
| Updater allowed unverified/truncated artifacts and duplicate workers | Official asset origin, exact length/SHA-256, deduplicated workers and failure reporting |
| Update connections and foreground notifications leaked | Finally blocks close connections, clean files and remove notifications |
| Idle animation loops repeatedly recomposed UI | Static surfaces; short interaction transitions and active progress only |
| Floating nav had narrow tap targets and could crowd screens | Full-width labelled Material navigation, safe insets, bounded wide-screen content |
| Startup demanded broad file access | Permission-free MediaStore default; retain previously granted legacy path behavior |
| Signature/local paths were bundled with the source | Exclude signing credentials and local build paths from deliverable |

## Limits and remaining validation

This is a source review and a verified build, not a guarantee that every possible defect is eliminated. No Android phone/emulator was attached in this environment. Actual device animation performance, notification behavior across OEM battery managers, MediaStore/legacy migrations, artwork after a real cache clear/reboot, and live downloads from each third-party platform require device validation. No claim of measured FPS, faster download speeds, iOS superiority or universal platform availability is made.

The bundled engine was verified against the official release's checksum, which detects incomplete/corrupt downloads; it is not a separate cryptographic signature trust system. Existing history whose remote image has already expired can recover from a readable local video; audio with no accessible old artwork receives the visible fallback. Clearing app data/uninstalling also removes persistent artwork and history.

Legacy partial files from the old shared working directory are not automatically associated with new isolated job directories, because ambiguous files could belong to another download. Such older in-flight downloads may restart. Newly created jobs resume within their own directory.

The original source distributed a signing key. The supplied APK retains that identity for upgrade compatibility; a public release should separately address signing-key custody/migration. Source archives here exclude that key and the Telegram credential.
