package com.alberto.upgrade.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.layout.size
import com.alberto.upgrade.R
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.TextDecoration
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.alberto.upgrade.MainActivity
import com.alberto.upgrade.data.AppData
import com.alberto.upgrade.data.Repo
import com.alberto.upgrade.data.classesOn
import com.alberto.upgrade.data.key
import com.alberto.upgrade.data.minStr
import com.alberto.upgrade.data.nowMin
import com.alberto.upgrade.data.parseDate
import com.alberto.upgrade.data.pendingExams
import com.alberto.upgrade.data.streakOf
import com.alberto.upgrade.data.xpOn
import java.time.LocalDate

private val TaskIdKey = ActionParameters.Key<String>("taskId")

private data class WColors(
    val bg: Color, val ink: Color, val muted: Color, val primary: Color, val onPrimary: Color,
    val flame: Color, val xp: Color, val danger: Color, val track: Color, val ring: Color, val done: Int
)

private val WLight = WColors(
    Color(0xFFFFFFFF), Color(0xFF141413), Color(0xFF6C6A64), Color(0xFFB2E62A), Color(0xFF12160A),
    Color(0xFFE8630A), Color(0xFF4C7A00), Color(0xFFD92D2D), Color(0xFFE6DFD8), Color(0xFFD6D0C4), R.drawable.w_done_light
)
private val WDark = WColors(
    Color(0xFF1C1B19), Color(0xFFFAF9F5), Color(0xFFA09D96), Color(0xFFC4F23A), Color(0xFF12160A),
    Color(0xFFFF7A1A), Color(0xFFC4F23B), Color(0xFFFF5C5C), Color(0xFF2E2C28), Color(0xFF3A3833), R.drawable.w_done_dark
)

class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Repo.init(context)
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val c = if (night) WDark else WLight
        provideContent {
            val data by Repo.state.collectAsState()
            WidgetBody(context, data, c)
        }
    }
}

@Composable
private fun WidgetBody(context: Context, data: AppData, c: WColors) {
    val today = LocalDate.now()
    val minute = nowMin()
    val streak = streakOf(data, today)
    val xp = xpOn(data, today)
    val classes = classesOn(data, today)
    val current = classes.firstOrNull { minute in it.period.start until it.period.end }
    val next = classes.firstOrNull { it.period.start > minute }
    val exam = pendingExams(data, today, 3).firstOrNull()

    val pending = data.tasks.filter { t ->
        val d = parseDate(t.due)
        !t.done && (d == null || !d.isAfter(today.plusDays(1)))
    }.sortedWith(compareBy({ it.due == null }, { it.due }))
    val doneToday = data.tasks.filter { it.done && it.xpDate == today.key() }
    val tasks = (pending + doneToday).take(4)

    val openApp = actionStartActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    val openAdd = actionStartActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("open", "add"))

    Column(GlanceModifier.fillMaxSize().background(c.bg).cornerRadius(26.dp).padding(16.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(GlanceModifier.defaultWeight().clickable(openApp), verticalAlignment = Alignment.CenterVertically) {
                WIcon(R.drawable.ic_flame_fill, if (streak > 0) c.flame else c.muted, 20)
                Text(" $streak", style = TextStyle(color = ColorProvider(if (streak > 0) c.flame else c.muted), fontWeight = FontWeight.Bold, fontSize = 17.sp))
                Spacer(GlanceModifier.width(14.dp))
                WIcon(R.drawable.ic_zap_fill, c.xp, 18)
                Spacer(GlanceModifier.width(6.dp))
                LinearProgressIndicator(
                    progress = (xp / data.dailyGoal.toFloat()).coerceIn(0f, 1f),
                    modifier = GlanceModifier.defaultWeight().height(10.dp).cornerRadius(10.dp),
                    color = ColorProvider(c.primary),
                    backgroundColor = ColorProvider(c.track)
                )
                Text(
                    "  ${minOf(xp, data.dailyGoal)}/${data.dailyGoal}",
                    style = TextStyle(color = ColorProvider(c.ink), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                )
            }
            Spacer(GlanceModifier.width(12.dp))
            Box(
                GlanceModifier.size(40.dp).background(c.primary).cornerRadius(12.dp).clickable(openAdd),
                contentAlignment = Alignment.Center
            ) { WIcon(R.drawable.ic_plus, c.onPrimary, 22) }
        }
        val examSoon = exam != null
        val line = when {
            exam != null -> {
                val name = data.subject(exam.first.subjectId)?.name ?: exam.first.title
                when (exam.second) {
                    0L -> "Examen de $name HOY"
                    1L -> "Examen de $name mañana"
                    else -> "Examen de $name en ${exam.second} días"
                }
            }
            current != null -> "Ahora: ${current.subject.name} · hasta ${minStr(current.period.end)}"
            next != null -> "Luego: ${next.subject.name} a las ${minStr(next.period.start)}"
            else -> null
        }
        if (line != null) {
            Text(
                line,
                style = TextStyle(color = ColorProvider(if (examSoon) c.danger else c.muted), fontWeight = FontWeight.Bold, fontSize = 13.sp),
                maxLines = 1,
                modifier = GlanceModifier.padding(top = 10.dp)
            )
        }
        Spacer(GlanceModifier.height(8.dp))
        if (tasks.isEmpty()) {
            Text("Nada pendiente para hoy", style = TextStyle(color = ColorProvider(c.muted), fontSize = 14.sp))
        } else {
            tasks.chunked(2).forEach { pair ->
                Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    pair.forEachIndexed { i, t ->
                        if (i == 1) Spacer(GlanceModifier.width(12.dp))
                        val overdue = !t.done && t.due != null && t.due < today.key()
                        Row(
                            GlanceModifier.defaultWeight()
                                .clickable(actionRunCallback<ToggleTaskAction>(actionParametersOf(TaskIdKey to t.id))),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (t.done) {
                                Image(ImageProvider(c.done), contentDescription = null, modifier = GlanceModifier.size(20.dp))
                            } else {
                                WIcon(R.drawable.w_todo, c.ring, 20)
                            }
                            Text(
                                t.title,
                                style = TextStyle(
                                    color = ColorProvider(if (t.done) c.muted else if (overdue) c.danger else c.ink),
                                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                    textDecoration = if (t.done) TextDecoration.LineThrough else null
                                ),
                                maxLines = 1,
                                modifier = GlanceModifier.padding(start = 8.dp)
                            )
                        }
                    }
                    if (pair.size == 1) Spacer(GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

@Composable
private fun WIcon(res: Int, color: Color, sizeDp: Int) {
    Image(
        provider = ImageProvider(res),
        contentDescription = null,
        modifier = GlanceModifier.size(sizeDp.dp),
        colorFilter = ColorFilter.tint(ColorProvider(color))
    )
}

class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TaskIdKey] ?: return
        Repo.init(context)
        Repo.toggleTask(id)
        TodayWidget().update(context, glanceId)
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
