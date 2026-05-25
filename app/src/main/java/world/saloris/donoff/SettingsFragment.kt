package world.saloris.donoff

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.Navigation
import world.saloris.donoff.databinding.FragmentSettingsBinding
import world.saloris.donoff.util.user.OpenDialog

class SettingsFragment : Fragment() {

    /* View */
    private lateinit var binding: FragmentSettingsBinding
    private lateinit var navController: NavController

    /* Dialog */
    private val dialog = OpenDialog()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSettingsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navController = Navigation.findNavController(view)
        // faceMesh 표출선 변경
        binding.btnFaceMeshSettings.setOnClickListener {
            activity?.let { activity ->
                dialog.openDialog(activity, FaceMeshSettingsDialog(), "FaceMeshSettingsDialog")
            }
        }
        binding.btnFatigueAlarmSettings.setOnClickListener {
            activity?.let { activity ->
                dialog.openDialog(
                    activity,
                    FatigueAlarmSettingsDialog(),
                    "FatigueAlarmSettingsDialog"
                )
            }
        }
        // 계정 들어가기
        binding.btnAccountSettings.setOnClickListener { navController.navigate(R.id.action_settingsFragment_to_accountFragment) }
    }
}