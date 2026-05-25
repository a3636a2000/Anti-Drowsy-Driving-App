package world.saloris.donoff

import android.app.Activity
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.Navigation
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.listener.BarLineChartTouchListener
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartGestureListener
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import world.saloris.donoff.database.heartRate.HeartRateDao
import world.saloris.donoff.databinding.FragmentGraphHrBinding
import world.saloris.donoff.util.HEART_RATE_THRESHOLD
import world.saloris.donoff.util.INITIAL_ENTRY
import world.saloris.donoff.util.TutorialPrefs
import world.saloris.donoff.util.graph.HrMarkerView
import world.saloris.donoff.util.graph.LineWithBarCombinedChartRenderer
import world.saloris.donoff.util.graph.MultiLineXAxisRenderer
import world.saloris.donoff.util.graph.TimeAxisValueFormat
import java.lang.Float.max
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*


class GraphHrFragment : Fragment() {
    /* View */
    private lateinit var navController: NavController
    private lateinit var binding: FragmentGraphHrBinding

    /* User Authentication */
    private lateinit var auth: FirebaseAuth
    private lateinit var uid: String

    /* Toolbar */
    private lateinit var toolbar: Toolbar
    private var menuDate: MenuItem? = null
    private val onLayoutChangeListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        setMenuDate()
    }

    private fun setMenuDate() {
        menuDate = toolbar.menu.findItem(R.id.menu_date)
        menuDate?.apply {
            setOnMenuItemClickListener {
                val calendar = Calendar.getInstance()
                val dateSetListener =
                    DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
                        drawChart(LocalDate.of(year, month + 1, dayOfMonth))
                    }
                val datePickerDialog = DatePickerDialog(
                    requireContext(),
                    dateSetListener,
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                )
                datePickerDialog.datePicker.maxDate = calendar.timeInMillis
                datePickerDialog.show()
                true
            }
            isVisible = true
        }
    }

    /* Graph */
    private fun CombinedChart.highlightLastValue(lastValue: Entry) {
        setStateLayout(lastValue)

        val highlight = Highlight(lastValue.x, kotlin.Float.NaN, 0)
        highlight.dataIndex = 0
        highlightValue(highlight, true)
    }

    private fun setStateLayout(e: Entry) {
        binding.time.text = TimeAxisValueFormat().getSpecificFormattedValueInLine(e.x)
        if (e.y < HEART_RATE_THRESHOLD) {
            with(binding.state) {
                text = getString(R.string.drowsy)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.drowsy))
            }
            binding.imgState.setImageDrawable(
                ContextCompat.getDrawable(requireContext(), R.drawable.drowsy)
            )
        } else {
            with(binding.state) {
                text = getString(R.string.normal)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
            }
            binding.imgState.setImageDrawable(
                ContextCompat.getDrawable(requireContext(), R.drawable.fine)
            )
        }
    }

    private fun drawChart(date: LocalDate) {
        val zoneId = ZoneId.systemDefault()
        val start = Instant.from(ZonedDateTime.of(date, LocalTime.MIDNIGHT, zoneId))
        val stop = Instant.from(ZonedDateTime.of(date, LocalTime.of(23, 59, 59), zoneId))
        val baseTime = Instant.from(ZonedDateTime.of(date, LocalTime.MIDNIGHT, zoneId))

        val dao = HeartRateDao()

        val formatter = DateTimeFormatter.ofPattern("YYYY. MM. dd. EEEE").withZone(zoneId)
        binding.date.text = formatter.format(date)

        var selectedValue: Entry? = null

        val chart = binding.lineChart
        var initialScale = 1f
        with(chart) {
            clear()
            fitScreen()
            setNoDataText(getString(R.string.no_data))
            setNoDataTextColor(ContextCompat.getColor(requireContext(), R.color.line_secondary))
            description.isEnabled = false
            legend.isEnabled = false
            setPinchZoom(false)
            isDoubleTapToZoomEnabled = false
            renderer =
                LineWithBarCombinedChartRenderer(this, animator, viewPortHandler, 2f)
            setXAxisRenderer(
                MultiLineXAxisRenderer(
                    viewPortHandler, xAxis, getTransformer(YAxis.AxisDependency.RIGHT)
                )
            )
            setMaxVisibleValueCount(100)

            marker = HrMarkerView(context, layoutResource = R.layout.hr_marker_view)

            with(xAxis) {
                position = XAxis.XAxisPosition.BOTTOM
                valueFormatter = TimeAxisValueFormat()
                granularity = 1f
                isGranularityEnabled = true
                setDrawAxisLine(false)
                setDrawGridLines(false)
            }

            axisLeft.isEnabled = false
            with(axisRight) {
                axisMaximum = 180f
                axisMinimum = 0f
                granularity = 1f
                isGranularityEnabled = true
                setDrawAxisLine(false)
                setCenterAxisLabels(true)
                gridColor = ContextCompat.getColor(requireContext(), R.color.line_secondary)
//                labelCount = (axisMaximum - axisMinimum).toInt()
                xOffset = 10f
            }

            // Listeners
            onChartGestureListener = object : OnChartGestureListener {
                override fun onChartGestureStart(
                    me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?
                ) {
                }

                override fun onChartGestureEnd(
                    me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?
                ) {
                }

                override fun onChartLongPressed(me: MotionEvent?) {}

                override fun onChartSingleTapped(me: MotionEvent?) {}

                override fun onChartFling(
                    me1: MotionEvent?, me2: MotionEvent?, velocityX: Float, velocityY: Float
                ) {
                }

                override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) {}

                override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) {}

                override fun onChartDoubleTapped(me: MotionEvent?) {
                    val xTrans = me!!.x - viewPortHandler.offsetLeft()
                    val yTrans = me.y - viewPortHandler.offsetTop()
                    if (viewPortHandler.scaleX <= initialScale) {
                        zoom(initialScale / viewPortHandler.scaleX, 1f, xTrans, yTrans)
                    }
                    if (selectedValue != null) {
                        (onTouchListener as BarLineChartTouchListener).stopDeceleration()
                        centerViewToAnimated(
                            selectedValue!!.x, selectedValue!!.y, YAxis.AxisDependency.RIGHT, 300
                        )
                    }
                }
            }

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry, h: Highlight) {
                    selectedValue?.icon =
                        if (selectedValue!!.y < HEART_RATE_THRESHOLD)
                            ContextCompat.getDrawable(
                                requireContext(), R.drawable.marker_point_drowsy
                            )
                        else null
                    e.icon = ContextCompat.getDrawable(context, R.drawable.marker_point)
                    setStateLayout(e)
                    selectedValue = e
                }

                override fun onNothingSelected() {
                    selectedValue?.icon =
                        if (selectedValue!!.y < HEART_RATE_THRESHOLD)
                            ContextCompat.getDrawable(
                                requireContext(), R.drawable.marker_point_drowsy
                            )
                        else null
                    selectedValue = null
                }
            })
        }

        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                binding.lineChart.visibility = View.INVISIBLE
                binding.loading.visibility = View.VISIBLE

                binding.stateContainer.visibility = View.INVISIBLE
                binding.loadingState.visibility = View.VISIBLE
            }
            val heartRateList = dao.getByUserAndPeriod(uid, start, stop)
            withContext(Dispatchers.Main) {
                binding.loading.visibility = View.INVISIBLE
                binding.lineChart.visibility = View.VISIBLE

                binding.loadingState.visibility = View.INVISIBLE
                binding.stateContainer.visibility = View.VISIBLE
            }

            if (heartRateList.isNotEmpty()) {
                withContext(Dispatchers.Main) { binding.layoutState.visibility = View.VISIBLE }
                val allValues = ArrayList<Entry>()
                val drowsyValues = ArrayList<BarEntry>()
                for (heartRate in heartRateList) {
                    val entry = BarEntry(
                        (heartRate.time.epochSecond - baseTime.epochSecond).toFloat(),
                        heartRate.value.toFloat()
                    )
                    allValues.add(entry as Entry)
                    if (heartRate.value < HEART_RATE_THRESHOLD) {
                        drowsyValues.add(entry)
                    }
                }

                for (drowsyValue in drowsyValues) {
                    (drowsyValue as Entry).icon =
                        ContextCompat.getDrawable(requireContext(), R.drawable.marker_point_drowsy)
                }
                val allDataSet = LineDataSet(allValues as List<Entry>?, "Heart Rate")
                val drowsyDataSet = BarDataSet(drowsyValues, "Heart Rate(Drowsy)")
                with(allDataSet) {
                    axisDependency = YAxis.AxisDependency.RIGHT
                    setDrawValues(false)
                    setDrawCircles(false)
//                    lineWidth = 1.5f
                    color = ContextCompat.getColor(requireContext(), R.color.primary)
                    setDrawHorizontalHighlightIndicator(false)
                    highLightColor = ContextCompat.getColor(requireContext(), R.color.line_primary)
                    setDrawFilled(true)
                    fillDrawable =
                        ContextCompat.getDrawable(requireContext(), R.drawable.bg_graph)
                }
                with(drowsyDataSet) {
                    axisDependency = YAxis.AxisDependency.RIGHT
                    setDrawValues(false)
                    setDrawIcons(false)
                    color = ContextCompat.getColor(requireContext(), R.color.drowsy)
                }

                val allDataSets = ArrayList<ILineDataSet>()
                val drowsyDataSets = ArrayList<IBarDataSet>()
                allDataSets.add(allDataSet)
                drowsyDataSets.add(drowsyDataSet)

                val allData = LineData(allDataSets)
                val drowsyData = BarData(drowsyDataSets)

                val combinedData = CombinedData()
                combinedData.apply {
                    setData(allData)
                    setData(drowsyData)
                }
                chart.data = combinedData

                val lastValue = allDataSet.getEntriesForXValue(chart.xChartMax)[0]
                withContext(Dispatchers.Main) {
                    chart.highlightLastValue(lastValue)
                }

                val totalIndex = allDataSet.xMax - allDataSet.xMin
                initialScale = max(totalIndex / INITIAL_ENTRY, 1f)
                with(chart) {
                    zoom(initialScale, 1f, 0f, 0f)
                    setVisibleXRangeMinimum(if (initialScale > 1f) 30f else totalIndex)
                    setVisibleYRangeMinimum(30f, YAxis.AxisDependency.RIGHT)
                    moveViewTo(lastValue.x, lastValue.y, YAxis.AxisDependency.RIGHT)
                    lifecycleScope.launch(Dispatchers.Main) { animateX(500) }
                }

                binding.btnZoomIn.setOnClickListener {
                    with(chart) {
                        (onTouchListener as BarLineChartTouchListener).stopDeceleration()
                        zoomIn()
                        if (selectedValue != null) {
                            centerViewTo(
                                selectedValue!!.x, selectedValue!!.y, YAxis.AxisDependency.RIGHT
                            )
                        }
                    }
                }

                binding.btnViewAll.setOnClickListener {
                    with(chart) {
                        (onTouchListener as BarLineChartTouchListener).stopDeceleration()
                        if (selectedValue == null) {
                            zoomAndCenterAnimated(
                                1 / viewPortHandler.scaleX, 1 / viewPortHandler.scaleY,
                                lastValue.x, lastValue.y, YAxis.AxisDependency.RIGHT,
                                500
                            )
                        } else {
                            zoomAndCenterAnimated(
                                1 / viewPortHandler.scaleX, 1 / viewPortHandler.scaleY,
                                selectedValue!!.x, selectedValue!!.y, YAxis.AxisDependency.RIGHT,
                                500
                            )
                        }
                    }
                }

                binding.btnZoomOut.setOnClickListener {
                    with(chart) {
                        (onTouchListener as BarLineChartTouchListener).stopDeceleration()
                        zoomOut()
                        if (selectedValue != null) {
                            centerViewTo(
                                selectedValue!!.x, selectedValue!!.y, YAxis.AxisDependency.RIGHT
                            )
                        }
                    }
                }

                binding.btnLastValue.setOnClickListener {
                    with(chart) {
                        highlightLastValue(lastValue)
                        (onTouchListener as BarLineChartTouchListener).stopDeceleration()
                        moveViewToAnimated(
                            lastValue.x, lastValue.y, YAxis.AxisDependency.RIGHT, 500
                        )
                    }
                }
            } else {
                withContext(Dispatchers.Main) { binding.layoutState.visibility = View.INVISIBLE }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /* User Authentication */
        auth = Firebase.auth
        uid = auth.currentUser!!.uid

        /* Toolbar */
        toolbar = (requireActivity() as MainActivity).binding.layoutToolbar.toolbar
        toolbar.addOnLayoutChangeListener(onLayoutChangeListener)
        setMenuDate()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentGraphHrBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        /* View */
        navController = Navigation.findNavController(view)

        TutorialPrefs.runTutorialOnceIfNeeded(requireContext(), "graph_hr")

        drawChart(LocalDate.now())
    }

    override fun onDestroyView() {
        super.onDestroyView()

        /* Toolbar */
        val toolbar = (requireActivity() as MainActivity).binding.layoutToolbar.toolbar
        toolbar.removeOnLayoutChangeListener(onLayoutChangeListener)
        menuDate?.isVisible = false
    }
}