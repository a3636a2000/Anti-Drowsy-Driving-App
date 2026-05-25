package world.saloris.donoff

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.Navigation
import world.saloris.donoff.databinding.FragmentGraphBinding

class GraphFragment : Fragment() {
    /* View */
    private lateinit var binding: FragmentGraphBinding
    private lateinit var navController: NavController

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentGraphBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navController = Navigation.findNavController(view)

        // HR 버튼 -> HR 그래프
        binding.btnHrImage.setOnClickListener { navController.navigate(R.id.action_graphFragment_to_graphHrFragment) }
//        // SPO2 버튼 -> SPO2 그래프
//        binding.btnSpo2Image.setOnClickListener { navController.navigate(R.id.action_graphFragment_to_graphHrFragment) }
//        // HRV 버튼 -> HRV 그래프
//        binding.btnHrvImage.setOnClickListener { navController.navigate(R.id.action_graphFragment_to_graphHrFragment) }
//        // PI 버튼 -> PI 그래프
//        binding.btnPiImage.setOnClickListener { navController.navigate(R.id.action_graphFragment_to_graphHrFragment) }
    }
}