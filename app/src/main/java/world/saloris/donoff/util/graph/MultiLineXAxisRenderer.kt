package world.saloris.donoff.util.graph

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.renderer.XAxisRenderer
import com.github.mikephil.charting.utils.MPPointF
import com.github.mikephil.charting.utils.Transformer
import com.github.mikephil.charting.utils.Utils
import com.github.mikephil.charting.utils.ViewPortHandler

class MultiLineXAxisRenderer(
    viewPortHandler: ViewPortHandler?, xAxis: XAxis?, trans: Transformer?
) : XAxisRenderer(viewPortHandler, xAxis, trans) {
    override fun drawLabel(
        c: Canvas?, formattedLabel: String, x: Float, y: Float,
        anchor: MPPointF?, angleDegrees: Float
    ) {
        val line: List<String> = formattedLabel.split("\n")
        Utils.drawXAxisValue(c, line[0], x, y, mAxisLabelPaint, anchor, angleDegrees)
        for (i in 1 until line.size) {
            Utils.drawXAxisValue(
                c, line[i], x, y + mAxisLabelPaint.textSize * i,
                mAxisLabelPaint, anchor, angleDegrees
            )
        }
    }

    override fun drawLabels(c: Canvas?, pos: Float, anchor: MPPointF?) {
        val rectPaint = Paint()
        rectPaint.color = Color.WHITE
        rectPaint.style = Paint.Style.FILL
        c!!.drawRect(
            0f, mViewPortHandler.contentBottom().toInt().toFloat(),
            mViewPortHandler.chartWidth, mViewPortHandler.chartHeight,
            rectPaint
        )
        super.drawLabels(c, pos, anchor)
    }
}