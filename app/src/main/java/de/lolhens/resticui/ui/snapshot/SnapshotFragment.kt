package de.lolhens.resticui.ui.snapshot

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.view.*
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import de.lolhens.resticui.BackupManager
import de.lolhens.resticui.R
import de.lolhens.resticui.config.RepoConfigId
import de.lolhens.resticui.databinding.FragmentSnapshotBinding
import de.lolhens.resticui.restic.ResticFile
import de.lolhens.resticui.restic.ResticRepo
import de.lolhens.resticui.restic.ResticSnapshotId
import de.lolhens.resticui.ui.Formatters
import java.io.File

class SnapshotFragment : Fragment() {
    private var _binding: FragmentSnapshotBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private var _backupManager: BackupManager? = null
    private val backupManager get() = _backupManager!!

    private lateinit var _repoId: RepoConfigId
    private val repoId: RepoConfigId get() = _repoId

    private lateinit var _snapshotId: ResticSnapshotId
    private val snapshotId: ResticSnapshotId get() = _snapshotId

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSnapshotBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setHasOptionsMenu(true)

        _backupManager = BackupManager.instance(requireContext())

        val activity = requireActivity() as SnapshotActivity
        _repoId = activity.repoId
        _snapshotId = activity.snapshotId

        binding.textSnapshotId.text = snapshotId.short
        binding.textSnapshotIdLong.text = snapshotId.id

        val repo = backupManager.config.repos.find { it.base.id == repoId }

        if (repo != null) {
            val resticRepo = repo.repo(backupManager.restic)

            resticRepo.cat(snapshotId).handle { snapshot, throwable ->
                requireActivity().runOnUiThread {
                    if (snapshot != null) {
                        val timeString = "Created on ${Formatters.dateTime(snapshot.time)}"
                        val snapshotRootPath = snapshot.paths[0]

                        binding.textTime.visibility = VISIBLE
                        binding.textHostname.visibility = VISIBLE
                        binding.textPath.visibility = VISIBLE

                        binding.textTime.text = timeString
                        binding.textHostname.text = snapshot.hostname
                        binding.textPath.text = snapshotRootPath.path

                        resticRepo.ls(snapshotId).handle { lsResult, throwable ->
                            requireActivity().runOnUiThread {
                                if (lsResult != null) {
                                    val (_, files) = lsResult
                                    binding.progressSnapshot.visibility = GONE

                                    binding.listFilesSnapshot.adapter = SnapshotFilesListAdapter(
                                        requireContext(),
                                        ArrayList(
                                            files.filter {
                                                it.path.startsWith(snapshotRootPath) &&
                                                        it.path.relativeTo(snapshotRootPath).path.isNotEmpty()
                                            }
                                        ),
                                        resticRepo,
                                        snapshotId,
                                        snapshotRootPath,
                                        binding.progressDl
                                    )

                                } else {
                                    throwable?.printStackTrace()
                                }
                            }
                        }
                    } else {
                        throwable?.printStackTrace()
                    }
                }
            }
        }

        return root
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.nav_menu_entry_delete, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_delete -> {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.alert_delete_snapshot_title)
                    .setMessage(R.string.alert_delete_snapshot_message)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        val repo = backupManager.config.repos.find { it.base.id == repoId }
                        if (repo != null) {
                            val resticRepo = repo.repo(backupManager.restic)

                            item.isEnabled = false
                            binding.progressSnapshotDelete.visibility = VISIBLE

                            resticRepo.forget(listOf(snapshotId), prune = true)
                                .handle { _, throwable ->
                                    if (throwable == null) {
                                        backupManager.configure { config ->
                                            config
                                        }
                                        requireActivity().finish()
                                    } else {
                                        throwable.printStackTrace()
                                    }
                                }
                        }
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> }
                    .show()
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

class SnapshotFilesListAdapter(
    private val context: Context,
    private val files: ArrayList<ResticFile>,
    private val resticRepo: ResticRepo,
    private val snapshotId: ResticSnapshotId,
    private val rootPath: File,
    private val progressDl: ProgressBar
) : BaseAdapter() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val pathNameText: TextView = view.findViewById(R.id.pathname)
        val fileDateText: TextView = view.findViewById(R.id.filedate)
    }

    private var sortOrderDesc: Boolean? = null
    private var sortedFiles: ArrayList<ResticFile> = ArrayList(files)

    fun triggerSort(listFilesSnapshot: ListView) {
        sortOrderDesc = when (sortOrderDesc) {
            null -> {
                sortedFiles.sortByDescending { it.mtime }
                true
            }

            true -> {
                sortedFiles.sortBy { it.mtime }
                false
            }

            false -> {
                sortedFiles = ArrayList(files)
                null
            }
        }

        listFilesSnapshot.invalidateViews()
    }

    override fun getCount(): Int = sortedFiles.size

    override fun getItem(position: Int): Any = position

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View? {
        lateinit var holder: RecyclerView.ViewHolder
        val view =
            if (convertView == null) {
                val inflater = LayoutInflater.from(context)
                val view = inflater.inflate(R.layout.listitem_file, parent, false)
                holder = ViewHolder(view)
                view.tag = holder
                view
            } else {
                holder = convertView.tag as ViewHolder
                convertView
            }

        val file = sortedFiles[position]
        val pathString = file.path.relativeTo(rootPath).toString() +
                (if (file.type == "dir") "/" else "")
        val dateString = Formatters.dateTimeShort(file.mtime)

        holder.pathNameText.text = pathString
        holder.fileDateText.text = dateString

        // Add a click listener to initiate download
        holder.pathNameText.setOnClickListener {
            if (file.type == "file") {
                // Path where the downloaded file will be saved on the device
                val sharedPref = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
                val downloadPathString = sharedPref?.getString("dl_path", "") ?: ""
                val downloadPath = File(downloadPathString)

                if (!(downloadPath.exists() && downloadPath.isDirectory)) {
                    AlertDialog.Builder(context)
                        .setTitle(R.string.alert_download_file_title)
                        .setMessage(R.string.alert_download_file_no_dest_dir)
                        .setNegativeButton(android.R.string.cancel) { _, _ -> }
                        .show()
                } else {
                    AlertDialog.Builder(context)
                        .setTitle(R.string.alert_download_file_title)
                        .setMessage(
                            context.getString(
                                R.string.alert_download_file_message,
                                file.path.name,
                                downloadPathString
                            )
                        )
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            downloadFile(file, downloadPath)
                        }
                        .setNegativeButton(android.R.string.cancel) { _, _ -> }
                        .show()
                }
            }
        }
        holder.fileDateText.setOnClickListener{
            val v = it.parent.parent as ListView
            triggerSort(v)
        }

        return view
    }

    // Function to handle file download
    private fun downloadFile(file: ResticFile, downloadPath: File) {
        progressDl.visibility = VISIBLE
        resticRepo.restore(snapshotId, downloadPath, file)
            .handle { content, throwable ->
                val handler = Handler(context.mainLooper)
                handler.post {
                    if (content != null) {
                        // Notify the user that the download is complete
                        // You can use a Toast or other UI element to display this message
                        showToast("File downloaded")
                    } else {
                        throwable?.printStackTrace()

                        // Notify the user that an error occurred during download
                        showToast("Failed to download")
                    }
                    progressDl.visibility = GONE
                }
            }
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

}