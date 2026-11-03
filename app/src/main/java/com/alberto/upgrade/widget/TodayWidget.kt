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
import androidx.glance.GlanceId
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
    val bg: Color, val ink: Color, val muted: Color, val primary: Color,
    val flame: Color, val xp: Color, val danger: Color, val chip: Color
)

private val WLight = WColors(Color(0xFFFFFFFF), Color(0xFF1A1A1A), Color(0xFF6F6D66), Color(0xFF5645D4), Color(0xFFDD5B00), Color(0xFFB98500), Color(0xFFE03131), Color(0xFFF6F5F4))
private val WDark = WColors(Color(0xFF202020), Color(0xFFFFFFFF), Color(0xFFA4A097), Color(0xFF9D91FF), Color(0xFFFF8A3D), Color(0xFFF5C542), Color(0xFFFF6B6B), Color(0xFF2C2C2C))

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

    val tasks = data.tasks.filter { t ->
        val d = parseDate(t.due)
        !t.done && (d == null || !d.isAfter(today.plusDays(1)))
    }.sortedWith(compareBy({ it.due == null }, { it.due })).take(4)

    val openApp = actionStartActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    val openAdd = actionStartActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("open", "add"))

    Column(
        GlanceModifier.fillMaxSize().background(c.bg).cornerRadius(20.dp).padding(14.dp)
    ) {
        Row(GlanceModifier.fillMaxWidth().clickable(openApp), verticalAlignment = Alignment.CenterVertically) {
            Text("🔥 $streak", style = TextStyle(color = ColorProvider(if (streak > 0) c.flame else c.muted), fontWeight = FontWeight.Bold, fontSize = 15.sp))
            Spacer(GlanceModifier.width(12.dp))
            Text("⚡ $xp/${data.dailyGoal}", style = TextStyle(color = ColorProvider(c.xp), fontWeight = FontWeight.Bold, fontSize = 15.sp))
            Spacer(GlanceModifier.defaultWeight())
            Text(
                "＋",
                style = TextStyle(color = ColorProvider(c.primary), fontWeight = FontWeight.Bold, fontSize = 20.sp),
                modifier = GlanceModifier.padding(horizontal = 8.dp).clickable(openAdd)
            )
        }
        val line = when {
            exam != null && exam.second <= 1L -> "📚 ${exam.first.title} " + if (exam.second == 0L) "HOY" else "MAÑANA"
            current != null -> "Ahora: ${current.subject.name} · hasta ${minStr(current.period.end)}"
            next != null -> "Luego: ${next.subject.name} a las ${minStr(next.period.start)}"
            else -> "Lo que te queda"
        }
        Text(
            line,
            style = TextStyle(color = ColorProvider(if (exam != null && exam.second <= 1L) c.danger else c.primary), fontWeight = FontWeight.Bold, fontSize = 13.sp),
            maxLines = 1,
            modifier = GlanceModifier.padding(top = 4.dp, bottom = 6.dp)
        )
        if (tasks.isEmpty()) {
            Text("Nada pendiente para hoy ✨", style = TextStyle(color = ColorProvider(c.muted), fontSize = 14.sp))
        } else {
            tasks.forEach { t ->
                val overdue = t.due != null && t.due < today.key()
                Row(
                    GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)
                        .clickable(actionRunCallback<ToggleTaskAction>(actionParametersOf(TaskIdKey to t.id))),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        GlanceModifier.width(22.dp).height(22.dp).cornerRadius(11.dp).background(c.chip),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("○", style = TextStyle(color = ColorProvider(c.muted), fontSize = 12.sp))
                    }
                    Text(
                        t.title,
                        style = TextStyle(color = ColorProvider(if (overdue) c.danger else c.ink), fontSize = 14.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        modifier = GlanceModifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
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
