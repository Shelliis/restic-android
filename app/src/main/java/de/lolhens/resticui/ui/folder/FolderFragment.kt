package de.lolhens.resticui.ui.folder

import android.app.AlertDialog
import android.os.Bundle
import android.view.*
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import de.lolhens.resticui.BackupManager
import de.lolhens.resticui.R
import de.lolhens.resticui.config.FolderConfigId
import de.lolhens.resticui.databinding.FragmentFolderBinding
import de.lolhens.resticui.restic.ResticSnapshotId
import de.lolhens.resticui.ui.Formatters
import de.lolhens.resticui.ui.snapshot.SnapshotActivity
import java.util.concurrent.CompletionException
import kotlin.math.roundToInt

class FolderFragment : Fragment() {
    private var _binding: FragmentFolderBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private var _backupManager: BackupManager? = null
    private val backupManager get() = _backupManager!!

    private lateinit var _folderId: FolderConfigId
    private val folderId: FolderConfigId get() = _folderId

    private var snapshotIds: List<ResticSnapshotId>? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFolderBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setHasOptionsMenu(true)

        _backupManager = BackupManager.instance(requireContext())

        _folderId = (requireActivity() as FolderActivity).folderId
        val config = backupManager.config
        val folder = config.folders.find { it.id == folderId }
        val repo = folder?.repo(config)

        // fix nested scrolling for ListView
        binding.listFolderSnapshots.setOnTouchListener { view, event ->
            val action = event.action
            when (action) {
                MotionEvent.ACTION_DOWN ->
                    view.parent.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP ->
                    view.parent.requestDisallowInterceptTouchEvent(false)
            }

            view.onTouchEvent(event)
            true
        }

        if (folder != null && repo != null) {
            binding.textRepo.text = repo.base.name
            binding.textFolder.text = folder.path.path
            binding.textSchedule.text = folder.schedule
            binding.textSchedNotBefore.text = folder.schedTimeNotBefore
            binding.textRetain.text = listOf(
                "Everything",
                listOf(
                    if (folder.keepLast == null) "" else "in last ${folder.keepLast}",
                    if (folder.keepWithin == null) "" else "within ${
                        Formatters.durationDaysHours(
                            folder.keepWithin
                        )
                    }"
                ).filter { it.isNotEmpty() }.joinToString(" and ")
            ).filter { it.isNotEmpty() }.joinToString(" ")

            val resticRepo = repo.repo(backupManager.restic)

            backupManager.observeConfig(viewLifecycleOwner) { config ->
                val folder = config.folders.find { it.id == folderId }

                val lastSuccessfulBackup = folder?.lastBackup(filterSuccessful = true)

                binding.textLastBackup.text =
                    if (lastSuccessfulBackup == null) ""
                    else "Last Backup on ${Formatters.dateTime(lastSuccessfulBackup.timestamp)}"

                resticRepo.snapshots(resticRepo.restic.hostname).handle { snapshots, throwable ->
                    requireActivity().runOnUiThread {
                        binding.progressFolderSnapshots.visibility = GONE

                        val snapshots =
                            if (folder != null)
                                snapshots?.filter { it.paths.contains(folder.path) }?.reversed()
                                    ?: emptyList()
                            else
                                emptyList()

                        snapshotIds = snapshots.map { it.id }
                        binding.listFolderSnapshots.adapter = ArrayAdapter(
                            requireContext(),
                            android.R.layout.simple_list_item_1,
                            snapshots.map { "${Formatters.dateTime(it.time)} ${it.id.short}" }
                        )

                        if (throwable != null) {
                            val throwable =
                                if (throwable is CompletionException && throwable.cause != null) throwable.cause!!
                                else throwable

                            binding.textError.text = throwable.message
                            binding.textError.visibility = VISIBLE
                        }
                    }
                }
            }

            val activeBackup = backupManager.activeBackup(folderId)
            activeBackup.observe(viewLifecycleOwner) { backup ->
                binding.progressBackupDetails.visibility =
                    if (backup.isStarting()) VISIBLE else GONE

                binding.textBackupDetails.visibility =
                    if (!backup.isStarting() && backup.error == null) VISIBLE else GONE

                binding.textBackupError.visibility =
                    if (backup.error != null) VISIBLE else GONE

                binding.buttonBackup.visibility =
                    if (!backup.inProgress) VISIBLE else GONE

                binding.buttonBackupCancel.visibility =
                    if (backup.inProgress) VISIBLE else GONE

                if (backup.inProgress) {
                    if (backup.progress != null) {
                        binding.progressBackup.setProgress(
                            (backup.progress.percentDone100()).roundToInt(),
                            true
                        )

                        val details =
                            """
                            ${backup.progress.percentDoneString()} done / ${backup.progress.timeElapsedString()} elapsed
                            ${backup.progress.files_done}${if (backup.progress.total_files != null) " / ${backup.progress.total_files}" else ""} Files
                            ${backup.progress.bytesDoneString()}${if (backup.progress.total_bytes != null) " / ${backup.progress.totalBytesString()}" else ""}
                            """.trimIndent()

                        binding.textBackupDetails.text = details
                        binding.textBackupDetails.setOnClickListener {
                            AlertDialog.Builder(context)
                                .setTitle("Backup Progress")
                                .setMessage(details)
                                .setPositiveButton("OK") { dialog, _ ->
                                    dialog.cancel()
                                }
                                .show()
                        }
                    }
                } else {
                    binding.progressBackup.setProgress(0, true)

                    when {
                        backup.error != null -> {
                            System.err.println(backup.error)
                            binding.textBackupError.text = backup.error
                            binding.textBackupError.setOnClickListener {
                                AlertDialog.Builder(context)
                                    .setTitle("Backup Error")
                                    .setMessage(backup.error)
                                    .setPositiveButton("OK") { dialog, _ ->
                                        dialog.cancel()
                                    }
                                    .show()
                            }
                        }
                        backup.summary != null -> {
                            val details =
                                if (backup.progress == null) null
                                else {
                                    """
                                    Backup finished after ${backup.progress.timeElapsedString()}!
                                    ${backup.progress.files_done}${if (backup.progress.total_files != null) " / ${backup.progress.total_files}" else ""} Files
                                    ${backup.progress.bytesDoneString()}${if (backup.progress.total_bytes != null) " / ${backup.progress.totalBytesString()}" else ""}
                                    """.trimIndent()
                                }

                            binding.textBackupDetails.text = details ?: "Backup finished!"
                        }
                        else -> {
                            // cancelled
                        }
                    }
                }
            }

            binding.listFolderSnapshots.setOnItemClickListener { _, _, position, _ ->
                val snapshotId = snapshotIds?.get(position)
                if (snapshotId != null) SnapshotActivity.start(this, folder.repoId, snapshotId)
            }

            binding.buttonBackup.setOnClickListener { _ ->
                backupManager.backup(
                    requireContext(),
                    folder,
                    removeOld = false,
                    scheduled = false
                )
            }

            binding.buttonBackupCancel.setOnClickListener { _ ->
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.alert_backup_cancel_title)
                    .setMessage(R.string.alert_backup_cancel_message)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        activeBackup.value?.cancel()
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> }
                    .show()
            }
        }

        return root
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.nav_menu_entry, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_delete -> {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.alert_delete_folder_title)
                    .setMessage(R.string.alert_delete_folder_message)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        backupManager.configure { config ->
                            config.copy(folders = config.folders.filterNot { it.id == folderId })
                        }

                        requireActivity().finish()
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> }
                    .show()
                true
            }
            R.id.action_edit -> {
                FolderActivity.start(this, true, folderId)

                requireActivity().finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    override fun onDestroyView() {
        super.onDestroyView()
        _backupManager = null
        _binding = null
    }
}