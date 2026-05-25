package world.saloris.donoff

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.Navigation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import world.saloris.donoff.databinding.FragmentMainBinding
import world.saloris.donoff.util.user.MakeToast


class MainFragment : Fragment() {
    /* View */
    private lateinit var binding: FragmentMainBinding
    private lateinit var navController: NavController

    private lateinit var onBackPressedCallback: OnBackPressedCallback
    var btnBackPressedTime: Long = 0

    /* Toast */
    private val toast = MakeToast()

    /* User Authentication */
    private lateinit var auth: FirebaseAuth

    private fun isAutoLogined(): Boolean {
        val autoLoginPref =
            requireContext().getSharedPreferences("autoLogin", Activity.MODE_PRIVATE)
        return autoLoginPref.contains("username")
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currTime = System.currentTimeMillis()
                val timeDifference = currTime - btnBackPressedTime

                if (timeDifference in 0..2000) {
                    activity?.finish()
                } else {
                    btnBackPressedTime = currTime
                    toast.makeToast(context, "한 번 더 누르면 종료됩니다.")
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /* User Authentication */
        auth = Firebase.auth
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentMainBinding.inflate(layoutInflater, container, false)

        /* Bottom Menu */
        val bottomMenu = (requireActivity() as MainActivity).binding.bottomMenu
        bottomMenu.visibility = View.GONE

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navController = Navigation.findNavController(view)

        /* User Authentication */
        if (auth.currentUser == null) {
            if (isAutoLogined()) {
                context?.let { toast.makeToast(it, "로그인에 실패했습니다.") }
            }
            navController.navigate(R.id.action_mainFragment_to_loginStartFragment)
        } else {
            if (!auth.currentUser?.isEmailVerified!!) {
                context?.let { toast.makeToast(it, "메일함에서 인증해주세요") }
                navController.navigate(R.id.action_mainFragment_to_loginStartFragment)
            }
            binding.userName.text = auth.currentUser!!.displayName
        }

        binding.btnNodOff.setOnClickListener {
            navController.navigate(R.id.action_mainFragment_to_scanFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        /* Bottom Menu */
        val bottomMenu = (requireActivity() as MainActivity).binding.bottomMenu
        bottomMenu.visibility = View.VISIBLE
    }

    override fun onDetach() {
        super.onDetach()
        onBackPressedCallback.remove()
    }
}