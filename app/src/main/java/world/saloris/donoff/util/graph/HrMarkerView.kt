package world.saloris.donoff.util.graph

import android.annotation.SuppressLint
import android.content.Context
import android.widget.TextView
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import world.saloris.donoff.R


@SuppressLint("ViewConstructor")
class HrMarkerView(context: Context?, layoutResource: Int) : MarkerView(context, layoutResource) {
    private val time: TextView = findViewById(R.id.mk_time)
    private val heartRate: TextView = findViewById(R.id.mk_heart_rate)

    private val valueFormatter = TimeAxisValueFormat()

    override fun refreshContent(e: Entry, highlight: Highlight?) {
        time.text = valueFormatter.getSpecificFormattedValue(e.x)
        heartRate.text = e.y.toInt().toString()
        super.refreshContent(e, highlight)
    }

    override fun setOffset(offset: MPPointF?) {
        super.setOffset(offset)
    }

    override fun getOffset(): MPPointF {
        return MPPointF((-(width / 2)).toFloat(), (-height).toFloat())
    }
}