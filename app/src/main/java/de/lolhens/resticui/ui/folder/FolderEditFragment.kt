package de.lolhens.resticui.ui.folder

import android.os.Bundle
import android.view.*
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import de.lolhens.resticui.BackupManager
import de.lolhens.resticui.R
import de.lolhens.resticui.config.FolderConfig
import de.lolhens.resticui.config.FolderConfigId
import de.lolhens.resticui.databinding.FragmentFolderEditBinding
import de.lolhens.resticui.ui.Formatters
import de.lolhens.resticui.util.DirectoryChooser
import java.io.File
import java.time.Duration

class FolderEditFragment : Fragment() {
    companion object {
        val schedules = arrayOf(
            Pair("Manual", -1),
            Pair("Hourly", 60),
            Pair("Daily", 24 * 60),
            Pair("Weekly", 7 * 24 * 60),
            Pair("Monthly", 30 * 24 * 60)
        )

        val schedTimeNotBefore = arrayOf(
            Pair(">00:00", 0 ),
            Pair(">01:00", 1 ),
            Pair(">02:00", 2 ),
            Pair(">03:00", 3 ),
            Pair(">04:00", 4 ),
            Pair(">05:00", 5 ),
            Pair(">06:00", 6 ),
            Pair(">07:00", 7 ),
            Pair(">08:00", 8 ),
            Pair(">09:00", 9 ),
            Pair(">10:00", 10 ),
            Pair(">11:00", 11 ),
            Pair(">12:00", 12 ),
            Pair(">13:00", 13 ),
            Pair(">14:00", 14 ),
            Pair(">15:00", 15 ),
            Pair(">16:00", 16 ),
            Pair(">17:00", 17 ),
            Pair(">18:00", 18 ),
            Pair(">19:00", 19 ),
            Pair(">20:00", 20 ),
            Pair(">21:00", 21 ),
            Pair(">22:00", 22 ),
            Pair(">23:00", 23 ),
        )

        val retainProfiles = arrayOf(
            -1,
            1,
            2,
            6,
            1 * 24,
            3 * 24,
            5 * 24,
            10 * 24,
            30 * 24,
            60 * 24,
            90 * 24,
            120 * 24,
            365 * 24
        )
    }

    private var _binding: FragmentFolderEditBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private var _backupManager: BackupManager? = null
    private val backupManager get() = _backupManager!!

    private lateinit var _folderId: FolderConfigId
    private val folderId: FolderConfigId get() = _folderId

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFolderEditBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setHasOptionsMenu(true)

        _backupManager = BackupManager.instance(requireContext())

        _folderId = (requireActivity() as FolderActivity).folderId
        val config = backupManager.config
        val folder = config.folders.find { it.id == folderId }
        val folderRepo = folder?.repo(config)

        binding.spinnerRepo.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            backupManager.config.repos.map { it.base.name }
        )

        binding.spinnerSchedule.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            schedules.map { it.first }
        )
        binding.spinnerSchedule.setSelection(1)

        binding.spinnerSchedNotBefore.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            schedTimeNotBefore.map { it.first }
        )
        binding.spinnerSchedNotBefore.setSelection(0)
        binding.spinnerSchedNotBefore.setSelection(0)

        binding.spinnerRetainWithin.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            retainProfiles.map { hours ->
                if (hours == -1) "Always" else Formatters.durationDaysHours(
                    Duration.ofHours(hours.toLong())
                )
            }
        )
        binding.spinnerRetainWithin.setSelection(1)

        val directoryChooser = DirectoryChooser.newInstance()

        directoryChooser.register(this, requireContext()) { path ->
            binding.editFolder.setText(path)
        }

        binding.buttonFolderSelect.setOnClickListener {
            directoryChooser.openDialog()
        }

        if (folder != null && folderRepo != null) {
            binding.spinnerRepo.setSelection(backupManager.config.repos.indexOfFirst { it.base.id == folderRepo.base.id })
            binding.editFolder.setText(folder.path.path)
            binding.spinnerSchedule.setSelection(schedules.indexOfFirst { it.first == folder.schedule })
            binding.spinnerSchedNotBefore.setSelection(schedTimeNotBefore.indexOfFirst { it.first == folder.schedTimeNotBefore })
            val scheduleIndex = retainProfiles.indexOfFirst {
                it.toLong() == folder.keepWithin?.toHours()
            }
            binding.spinnerRetainWithin.setSelection(if (scheduleIndex == -1) 0 else scheduleIndex)
        }

        return root
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.nav_menu_entry_edit, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_done -> {
                val selectedRepoName = binding.spinnerRepo.selectedItem?.toString()
                val repo =
                    if (selectedRepoName == null) null
                    else backupManager.config.repos.find { it.base.name == selectedRepoName }
                val path = binding.editFolder.text.toString()
                val schedule = binding.spinnerSchedule.selectedItem?.toString()
                val schedTimeNotBefore = binding.spinnerSchedNotBefore.selectedItem?.toString()
                val keepWithin =
                    if (retainProfiles[binding.spinnerRetainWithin.selectedItemPosition] < 0) null
                    else Duration.ofHours(retainProfiles[binding.spinnerRetainWithin.selectedItemPosition].toLong())

                if (
                    repo != null &&
                    path.isNotEmpty() &&
                    schedule != null &&
                    schedTimeNotBefore != null
                ) {
                    val prevFolder = backupManager.config.folders.find { it.id == folderId }

                    val folder = FolderConfig(
                        folderId,
                        repo.base.id,
                        File(path),
                        schedule,
                        schedTimeNotBefore,
                        prevFolder?.keepLast,
                        keepWithin,
                        prevFolder?.history ?: emptyList()
                    )

                    backupManager.configure { config ->
                        config.copy(folders = config.folders.filterNot { it.id == folderId }
                            .plus(folder))
                    }

                    FolderActivity.start(this, false, folderId)

                    requireActivity().finish()

                    true
                } else {
                    false
                }
            }
            else -> super.onOptionsItemSelected(item)
        }

    override fun onDestroyView() {
        super.onDestroyView()
        _backupManager = null
        _binding = null
    }
}