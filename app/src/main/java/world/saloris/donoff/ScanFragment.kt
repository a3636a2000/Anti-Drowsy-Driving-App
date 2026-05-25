package world.saloris.donoff

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.Navigation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import world.saloris.donoff.databinding.FragmentScanBinding
import world.saloris.donoff.util.HEART_RATE_SERVICE_STRING
import world.saloris.donoff.util.SCAN_TIME
import world.saloris.donoff.util.TutorialPrefs
import world.saloris.donoff.util.ble.BleListAdapter
import world.saloris.donoff.util.user.MakeToast
import java.time.LocalDate
import java.util.*
import kotlin.concurrent.schedule


class ScanFragment : Fragment() {
    /* View */
    private lateinit var navController: NavController
    private lateinit var binding: FragmentScanBinding

    /* Toast */
    private val toast = MakeToast()

    /* User Authentication */
    private lateinit var auth: FirebaseAuth

    /* Toolbar */
    private lateinit var toolbar: Toolbar
    private var menuCamera: MenuItem? = null
    private val onLayoutChangeListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        setMenuCamera()
    }

    private fun setMenuCamera() {
        menuCamera = toolbar.menu.findItem(R.id.menu_camera)
        menuCamera?.apply {
            setOnMenuItemClickListener {
                navController.navigate(R.id.action_scanFragment_to_stateActivity)
                true
            }
            isVisible = true
        }
    }

    /* Permission */
    private val locationPermissionList = arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    @RequiresApi(Build.VERSION_CODES.S)
    private val bluetoothPermissionList = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val deniedList = result.filter { !it.value }.map { it.key }
            Log.d("State", "$deniedList")
            if (deniedList.isNotEmpty()) {
                AlertDialog.Builder(requireContext())
                    .setTitle("알림")
                    .setMessage("권한이 거부되었습니다. 사용을 원하시면 설정에서 해당 권한을 직접 허용하셔야 합니다.")
                    .setPositiveButton("설정") { _, _ -> openAndroidSetting() }
                    .setNegativeButton("취소", null)
                    .create()
                    .show()
            } else {
                binding.btnScan.isChecked = true
            }
        }

    private fun openAndroidSetting() {
        val intent = Intent().apply {
            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            addCategory(Intent.CATEGORY_DEFAULT)
            data = Uri.parse("package:${activity?.packageName}")
        }
        startActivity(intent)
    }

    /* BLE */
    private val bleListAdapter = BleListAdapter()

    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager =
            activity?.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private var scanResults: ArrayList<BluetoothDevice> = ArrayList()
    private val scanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            addScanResult(result)
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            for (result in results) {
                addScanResult(result)
            }
        }

        override fun onScanFailed(_error: Int) {
            Log.e("Scan Fail Code", "$_error")
        }

        private fun addScanResult(result: ScanResult) {
            val device: BluetoothDevice = result.device

            for (scanResult in scanResults) {
                if (device.address == scanResult.address) return
            }
            scanResults.add(result.device)
            bleListAdapter.notifyItemInserted(scanResults.size - 1)
        }
    }

    private fun startScan() {
        val filters: MutableList<ScanFilter> = ArrayList()
        val scanFilter: ScanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(UUID.fromString(HEART_RATE_SERVICE_STRING)))
            .build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build()
        scanResults.clear()

        /* Permission */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                binding.btnScan.post { binding.btnScan.isChecked = false }
                requestPermissionLauncher.launch(bluetoothPermissionList)
                return
            }
        } else {
            if (ActivityCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                binding.btnScan.post { binding.btnScan.isChecked = false }
                requestPermissionLauncher.launch(locationPermissionList)
                return
            }
        }

        /* BLE */
        if (bluetoothAdapter?.isEnabled == true) {
            filters.add(scanFilter)
            Log.d("State", "Start Scan!")
            bluetoothAdapter?.bluetoothLeScanner?.startScan(filters, settings, scanCallback)
        }
    }

    private fun stopScan() {
        /* Permission */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        } else {
            if (ActivityCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        if (bluetoothAdapter?.isEnabled == true) {
            Log.d("State", "Stop Scan!")
            bluetoothAdapter!!.bluetoothLeScanner?.stopScan(scanCallback)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /* User Authentication */
        auth = Firebase.auth

        /* Toolbar */
        toolbar = (requireActivity() as MainActivity).binding.layoutToolbar.toolbar
        toolbar.addOnLayoutChangeListener(onLayoutChangeListener)
        setMenuCamera()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentScanBinding.inflate(inflater, container, false)
        bleListAdapter.bluetoothDevices = scanResults
        binding.recyclerView.adapter = bleListAdapter
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navController = Navigation.findNavController(view)

        TutorialPrefs.runTutorialOnceIfNeeded(requireContext(), "scan")

        binding.btnScan.setOnCheckedChangeListener { button, isChecked ->
            if (isChecked) {
                button.isEnabled = false
                binding.loading.visibility = View.VISIBLE

                if (bluetoothAdapter == null) {
                    context?.let { toast.makeToast(it, "블루투스를 지원하지 않습니다.") }
                    button.post { button.isChecked = false }
                    return@setOnCheckedChangeListener
                }
                if (!bluetoothAdapter!!.isEnabled) {
                    context?.let { toast.makeToast(it, "블루투스가 꺼져 있습니다.") }
                    button.post { button.isChecked = false }
                    return@setOnCheckedChangeListener
                }

                startScan()
                Timer(false).schedule(SCAN_TIME) {
                    button.post { button.isChecked = false }
                }
            } else {
                button.isEnabled = true
                binding.loading.visibility = View.GONE

                stopScan()
            }
        }
    }

    override fun onStart() {
        super.onStart()

        binding.btnScan.isChecked = true
    }

    override fun onDestroyView() {
        super.onDestroyView()

        /* Toolbar */
        val toolbar = (requireActivity() as MainActivity).binding.layoutToolbar.toolbar
        toolbar.removeOnLayoutChangeListener(onLayoutChangeListener)
        menuCamera?.isVisible = false
    }
}