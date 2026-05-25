package world.saloris.donoff.util.graph

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Shader
import com.github.mikephil.charting.animation.ChartAnimator
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet
import com.github.mikephil.charting.renderer.BarChartRenderer
import com.github.mikephil.charting.utils.ViewPortHandler


class LineBarChartRenderer(
    chart: CombinedChart?, animator: ChartAnimator?, viewPortHandler: ViewPortHandler?,
    private val barWidth: Float
) : BarChartRenderer(chart, animator, viewPortHandler) {
    override fun drawDataSet(c: Canvas?, dataSet: IBarDataSet?, index: Int) {
        val trans = mChart.getTransformer(dataSet!!.axisDependency)

        val phaseX = mAnimator.phaseX
        val phaseY = mAnimator.phaseY

        initBuffers()
        val buffer = mBarBuffers[index]
        buffer.setPhases(phaseX, phaseY)
        buffer.setDataSet(index)
        buffer.setInverted(mChart.isInverted(dataSet.axisDependency))
        buffer.setBarWidth(mChart.barData.barWidth)

        buffer.feed(dataSet)

        trans.pointValuesToPixel(buffer.buffer)

        val isSingleColor = dataSet.colors.size == 1
        if (isSingleColor) {
            mRenderPaint.color = dataSet.color
        }

        for (j in 0 until buffer.size() step (4)) {
            if (!mViewPortHandler.isInBoundsLeft(buffer.buffer[j + 2])) continue
            if (!mViewPortHandler.isInBoundsRight(buffer.buffer[j])) break

            if (!isSingleColor) {
                mRenderPaint.color = dataSet.getColor(j / 4)
            }
            if (dataSet.gradientColor != null) {
                mRenderPaint.shader = LinearGradient(
                    buffer.buffer[j], buffer.buffer[j + 3],
                    buffer.buffer[j], buffer.buffer[j + 1],
                    dataSet.gradientColor.startColor, dataSet.gradientColor.endColor,
                    Shader.TileMode.MIRROR
                )
            }
            if (dataSet.gradientColors != null) {
                mRenderPaint.shader = LinearGradient(
                    buffer.buffer[j], buffer.buffer[j + 3],
                    buffer.buffer[j], buffer.buffer[j + 1],
                    dataSet.getGradientColor(j / 4).startColor,
                    dataSet.getGradientColor(j / 4).endColor,
                    Shader.TileMode.MIRROR
                )
            }

            val center = (buffer.buffer[j] + buffer.buffer[j + 2]) / 2
            val half = barWidth / 2
            c!!.drawRect(
                center - half, buffer.buffer[j + 1],
                center + half, buffer.buffer[j + 3],
                mRenderPaint
            )
        }
    }
}