package world.saloris.donoff.util.graph

import com.github.mikephil.charting.animation.ChartAnimator
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.charts.CombinedChart.DrawOrder
import com.github.mikephil.charting.renderer.CombinedChartRenderer
import com.github.mikephil.charting.renderer.LineChartRenderer
import com.github.mikephil.charting.utils.ViewPortHandler


class LineWithBarCombinedChartRenderer(
    chart: CombinedChart?, animator: ChartAnimator?, viewPortHandler: ViewPortHandler?,
    private val barWidth: Float
) : CombinedChartRenderer(chart, animator, viewPortHandler) {
    override fun createRenderers() {
        mRenderers.clear()

        val chart = mChart.get() as CombinedChart? ?: return
        val orders = chart.drawOrder
        for (order in orders) {
            when (order) {
                DrawOrder.BAR -> {
                    if (chart.barData != null) {
                        chart.barData.isHighlightEnabled = false
                        mRenderers.add(
                            LineBarChartRenderer(
                                chart, mAnimator, mViewPortHandler,
                                barWidth
                            )
                        )
                    }
                }
                DrawOrder.LINE -> {
                    if (chart.lineData != null) {
                        mRenderers.add(LineChartRenderer(chart, mAnimator, mViewPortHandler))
                    }
                }
                else -> {}
            }
        }
    }
}