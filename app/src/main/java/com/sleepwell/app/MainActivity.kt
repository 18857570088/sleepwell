package com.sleepwell.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.ScrollView
import android.widget.TextView
import android.widget.VideoView
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private enum class Page {
        Home,
        Data,
        Heal,
        Qa,
        Community,
        Profile,
    }

    private enum class ContentFilter(val label: String) {
        All("全部"),
        Video("视频"),
        WatchedVideo("已看视频"),
        Comic("漫画"),
        WatchedComic("已看漫画"),
    }

    private data class SleepRecord(
        val date: String,
        val bedTime: String,
        val wakeTime: String,
        val totalSleepMinutes: Int,
        val sleepLatencyMinutes: Int,
        val sleepEfficiency: Int,
        val awakenings: Int,
        val awakeAfterSleepMinutes: Int,
        val deepSleepMinutes: Int,
        val lightSleepMinutes: Int,
        val remSleepMinutes: Int,
        val sleepDebtMinutes: Int,
        val score: Int,
        val midpoint: String,
        val recommendedMidpoint: String,
        val stability: String,
        val sleepTiming: String,
    )

    private data class HealthContentItem(
        val id: String,
        val type: String,
        val categoryKey: String,
        val categoryName: String,
        val title: String,
        val subtitle: String,
        val duration: String,
        val cover: String,
        val url: String,
        val tags: List<String>,
        val description: String,
        val triggerQuestionKey: String,
        val triggerAnswerKey: String,
        val sortOrder: Int,
    )

    private data class HealthContentBundle(
        val item: HealthContentItem,
        val pages: List<HealthContentItem>,
    )

    private data class ApiConfig(
        val enabled: Boolean,
        val apiBaseUrl: String,
        val sleepRecordsEndpoint: String,
        val healthContentsEndpoint: String,
        val contentProgressEndpoint: String,
        val deviceStatusEndpoint: String,
        val uploadEndpoint: String,
        val mode: String,
    ) {
        fun url(endpoint: String): String =
            apiBaseUrl.trimEnd('/') + "/" + endpoint.trimStart('/')
    }

    private lateinit var contentHost: FrameLayout
    private lateinit var bottomNav: LinearLayout
    private lateinit var tablerTypeface: Typeface
    private var currentPage: Page = Page.Home
    private var sleepRecords: List<SleepRecord> = emptyList()
    private var healthContents: List<HealthContentItem> = emptyList()
    private var cloudWatchedContentIds: Set<String> = emptySet()
    private val apiConfig: ApiConfig by lazy { loadApiConfig() }
    private val watchedContentPrefs by lazy { getSharedPreferences("sleepwell_watched_content", Context.MODE_PRIVATE) }
    private var sleepDataSource: String = "云端模拟数据"
    private var contentDataSource: String = "云端内容库"
    private var isRemoteLoading: Boolean = true
    private var remoteLoadError: String? = null
    private var acceptedSleepRestrictionBedTime: String? = null
    private var acceptedSleepRestrictionWakeTime: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        tablerTypeface = Typeface.createFromAsset(assets, "vendor/tabler/fonts/tabler-icons.ttf")
        window.statusBarColor = C.bgPrimary
        window.navigationBarColor = C.bgPrimary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }

        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(C.bgPrimary)
            }
        contentHost =
            FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(match, 0, 1f)
            }
        bottomNav =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, dp(10))
                background = solid(C.bgPrimary, strokeColor = C.borderTertiary, radiusDp = 0)
                layoutParams = LinearLayout.LayoutParams(match, wrap)
            }
        root.addView(contentHost)
        root.addView(bottomNav)
        setContentView(root)
        showPage(Page.Home)
        refreshRemoteData()
    }

    private fun showPage(page: Page) {
        setVideoFullscreen(false)
        currentPage = page
        bottomNav.visibility = View.VISIBLE
        renderContent(scrollPage(contentFor(page)))
        renderBottomNav()
    }

    private fun showDetail(content: View, selectedPage: Page = currentPage, hideBottom: Boolean = false) {
        setVideoFullscreen(false)
        currentPage = selectedPage
        bottomNav.visibility = if (hideBottom) View.GONE else View.VISIBLE
        renderContent(scrollPage(content))
        if (!hideBottom) renderBottomNav()
    }

    private fun showRawDetail(content: View, selectedPage: Page = currentPage, hideBottom: Boolean = false, immersive: Boolean = false) {
        setVideoFullscreen(immersive)
        currentPage = selectedPage
        bottomNav.visibility = if (hideBottom) View.GONE else View.VISIBLE
        renderContent(content)
        if (!hideBottom) renderBottomNav()
    }

    private fun setVideoFullscreen(enabled: Boolean) {
        if (enabled) {
            window.statusBarColor = Color.BLACK
            window.navigationBarColor = Color.BLACK
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        } else {
            window.statusBarColor = C.bgPrimary
            window.navigationBarColor = C.bgPrimary
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            } else {
                window.decorView.systemUiVisibility = 0
            }
        }
    }

    private fun renderContent(view: View) {
        contentHost.removeAllViews()
        contentHost.addView(view)
    }

    private fun contentFor(page: Page): LinearLayout =
        when (page) {
            Page.Home -> if (sleepRecords.isEmpty()) cloudDataStatePage("今日睡眠报告") else homePage()
            Page.Data -> if (sleepRecords.isEmpty()) cloudDataStatePage("睡眠数据中心") else dataCenterPage()
            Page.Heal -> healingCenterPage()
            Page.Qa -> qaPage()
            Page.Community -> communityPage()
            Page.Profile -> profilePage()
        }

    private fun loadApiConfig(): ApiConfig {
        val json = assets.open("server_config.json").bufferedReader().use { it.readText() }
        val item = JSONObject(json)
        return ApiConfig(
            enabled = item.optBoolean("enabled", true),
            apiBaseUrl = item.optString("apiBaseUrl"),
            sleepRecordsEndpoint = item.optString("sleepRecordsEndpoint", "/v1/sleep-records?days=7&user_id=demo"),
            healthContentsEndpoint = item.optString("healthContentsEndpoint", "/v1/health-contents"),
            contentProgressEndpoint = item.optString("contentProgressEndpoint", "/v1/content-progress?user_id=demo"),
            deviceStatusEndpoint = item.optString("deviceStatusEndpoint", "/v1/device/status?user_id=demo"),
            uploadEndpoint = item.optString("uploadEndpoint", "/v1/uploads"),
            mode = item.optString("mode", "cloud-only"),
        )
    }

    private fun parseSleepRecords(array: JSONArray): List<SleepRecord> =
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            SleepRecord(
                date = item.getString("date"),
                bedTime = item.getString("bedTime"),
                wakeTime = item.getString("wakeTime"),
                totalSleepMinutes = item.getInt("totalSleepMinutes"),
                sleepLatencyMinutes = item.getInt("sleepLatencyMinutes"),
                sleepEfficiency = item.getInt("sleepEfficiency"),
                awakenings = item.getInt("awakenings"),
                awakeAfterSleepMinutes = item.getInt("awakeAfterSleepMinutes"),
                deepSleepMinutes = item.getInt("deepSleepMinutes"),
                lightSleepMinutes = item.getInt("lightSleepMinutes"),
                remSleepMinutes = item.getInt("remSleepMinutes"),
                sleepDebtMinutes = item.getInt("sleepDebtMinutes"),
                score = item.getInt("score"),
                midpoint = item.getString("midpoint"),
                recommendedMidpoint = item.getString("recommendedMidpoint"),
                stability = item.getString("stability"),
                sleepTiming = item.getString("sleepTiming"),
            )
        }

    private fun parseHealthContents(array: JSONArray): List<HealthContentItem> =
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val tagsArray = item.getJSONArray("tags")
            HealthContentItem(
                id = item.getString("id"),
                type = item.getString("type"),
                categoryKey = item.optString("categoryKey"),
                categoryName = item.optString("categoryName"),
                title = item.getString("title"),
                subtitle = item.getString("subtitle"),
                duration = item.getString("duration"),
                cover = item.getString("cover"),
                url = item.getString("url"),
                tags = (0 until tagsArray.length()).map { tagsArray.getString(it) },
                description = item.getString("description"),
                triggerQuestionKey = item.optString("triggerQuestionKey"),
                triggerAnswerKey = item.optString("triggerAnswerKey"),
                sortOrder = item.optInt("sortOrder", index),
            )
        }

    private fun parseWatchedContentIds(root: JSONObject): Set<String> {
        val data = root.optJSONObject("data") ?: return emptySet()
        val array = data.optJSONArray("watchedContentIds") ?: JSONArray()
        return (0 until array.length()).mapNotNull { index ->
            array.optString(index).takeIf { it.isNotBlank() }
        }.toSet()
    }

    private fun refreshRemoteData() {
        val config = apiConfig
        if (!config.enabled || config.apiBaseUrl.isBlank()) {
            isRemoteLoading = false
            remoteLoadError = "云端接口未启用"
            showPage(currentPage)
            return
        }
        isRemoteLoading = true
        remoteLoadError = null
        Thread {
            var errorMessage: String? = null
            val remoteSleepRecords = runCatching {
                val root = JSONObject(httpGet(config.url(config.sleepRecordsEndpoint)))
                parseSleepRecords(root.getJSONArray("data"))
            }.onFailure { errorMessage = "睡眠数据接口：${it.message ?: it.javaClass.simpleName}" }.getOrNull()
            val remoteHealthContents = runCatching {
                val root = JSONObject(httpGet(config.url(config.healthContentsEndpoint)))
                parseHealthContents(root.getJSONArray("data"))
            }.onFailure {
                if (errorMessage == null) errorMessage = "内容接口：${it.message ?: it.javaClass.simpleName}"
            }.getOrNull()
            val remoteWatchedIds = runCatching {
                val root = JSONObject(httpGet(config.url(config.contentProgressEndpoint)))
                parseWatchedContentIds(root)
            }.getOrNull()
            runOnUiThread {
                isRemoteLoading = false
                if (!remoteSleepRecords.isNullOrEmpty()) {
                    sleepRecords = remoteSleepRecords
                    sleepDataSource = "云端模拟数据"
                }
                if (remoteHealthContents != null) {
                    healthContents = remoteHealthContents
                    contentDataSource = "云端已上传内容"
                }
                if (remoteWatchedIds != null) {
                    cloudWatchedContentIds = remoteWatchedIds
                }
                migrateLegacyWatchedFlags(contentBundles())
                syncPendingWatchedContentIds()
                remoteLoadError =
                    if (sleepRecords.isEmpty()) {
                        errorMessage ?: "云端未返回 7 天睡眠数据"
                    } else {
                        null
                    }
                showPage(currentPage)
            }
        }.start()
    }

    private fun httpGet(rawUrl: String): String {
        val connection = URL(rawUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 3500
        connection.readTimeout = 3500
        connection.requestMethod = "GET"
        return try {
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun httpPostJson(rawUrl: String, payload: JSONObject): String {
        val connection = URL(rawUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        connection.doOutput = true
        return try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("HTTP $code $body")
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun latestSleepRecord(): SleepRecord = sleepRecords.last()

    private fun previousSleepRecord(): SleepRecord = sleepRecords.getOrElse(sleepRecords.lastIndex - 1) { latestSleepRecord() }

    private fun averageInt(selector: (SleepRecord) -> Int): Int =
        sleepRecords.map(selector).average().roundToInt()

    private fun averageOneDecimal(selector: (SleepRecord) -> Int): String =
        String.format("%.1f", sleepRecords.map(selector).average())

    private fun percent(part: Int, total: Int): Int =
        if (total <= 0) 0 else (part * 100f / total).roundToInt()

    private fun formatDuration(minutes: Int): String {
        val hours = minutes / 60
        val rest = minutes % 60
        return if (hours > 0) "${hours}h ${rest}m" else "${rest}m"
    }

    private fun formatDurationStacked(minutes: Int): String {
        val hours = minutes / 60
        val rest = minutes % 60
        return if (hours > 0) "${hours}h\n${rest}m" else "${rest}\nmin"
    }

    private fun formatHourLabel(minutes: Int): String =
        String.format("%.1fH", minutes / 60f)

    private fun formatSignedMinutes(delta: Int): String {
        val sign = if (delta >= 0) "↑" else "↓"
        return "$sign${kotlin.math.abs(delta)}min"
    }

    private fun formatSleepDebt(minutes: Int): String =
        String.format("%.1fh", minutes / 60f)

    private fun formatSleepDebtDelta(first: Int, latest: Int): String {
        val improvedMinutes = latest - first
        return if (improvedMinutes >= 0) {
            "已减少${String.format("%.1f", improvedMinutes / 60f)}h"
        } else {
            "增加${String.format("%.1f", -improvedMinutes / 60f)}h"
        }
    }

    private fun trendPointsHigherIsBetter(values: List<Int>): List<Float> {
        val maxValue = (values.maxOrNull() ?: 1).coerceAtLeast(1)
        return values.map { (maxValue - it + 8).toFloat() }
    }

    private fun trendPointsLowerIsBetter(values: List<Int>): List<Float> =
        values.map { it.toFloat() }

    private fun rhythmShiftHours(record: SleepRecord): String {
        val shiftMinutes = kotlin.math.abs(minutesSinceMidnight(record.midpoint) - minutesSinceMidnight(record.recommendedMidpoint))
        return String.format("%.1f", shiftMinutes / 60f)
    }

    private fun minutesSinceMidnight(value: String): Int {
        val parts = value.split(":")
        return parts.getOrNull(0).orEmpty().toIntOrNull().orZero() * 60 + parts.getOrNull(1).orEmpty().toIntOrNull().orZero()
    }

    private fun Int?.orZero(): Int = this ?: 0

    private fun scrollPage(content: View): ScrollView =
        ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(C.bgPrimary)
            addView(
                content,
                FrameLayout.LayoutParams(match, wrap),
            )
        }

    private fun screen(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(18))
        }

    private fun cloudDataStatePage(pageTitle: String): LinearLayout =
        screen().apply {
            val config = apiConfig
            addView(title(pageTitle, 22f))
            addView(
                card("#E6F1FB", null).apply {
                    addView(row(Gravity.CENTER_VERTICAL).apply {
                        addView(iconCircle("wifi", "#FFFFFF", "#185FA5", 42))
                        addView(column().apply {
                            layoutParams = LinearLayout.LayoutParams(0, wrap, 1f)
                            setPadding(dp(10), 0, 0, 0)
                            addView(body(if (isRemoteLoading) "正在读取云端模拟数据" else "云端数据未就绪", 16f, "#0C447C", true))
                            addView(small(config.apiBaseUrl, "#0C447C"))
                        })
                    })
                    addView(progress(if (isRemoteLoading) 0.42f else 0.18f, if (isRemoteLoading) "#185FA5" else "#EF9F27", "#B5D4F4"))
                    addView(small(if (isRemoteLoading) "已取消本地 7 天模拟数据，正在从云端获取睡眠记录。" else (remoteLoadError ?: "请检查云端服务状态。"), "#0C447C"))
                },
            )
            addView(notification("info-circle", "数据源说明", "当前版本不再打包本地睡眠 JSON、本地视频清单或漫画清单；APP 数据统一来自云端接口。", "#185FA5", "#E6F1FB", "#0C447C", "#0C447C"))
            addView(textButton("重新加载云端数据", null) { refreshRemoteData() })
        }

    private fun homePage(): LinearLayout =
        screen().apply {
            val latest = latestSleepRecord()
            val previous = previousSleepRecord()
            val scoreDelta = latest.score - previous.score
            val deepPercent = percent(latest.deepSleepMinutes, latest.totalSleepMinutes)
            val remPercent = percent(latest.remSleepMinutes, latest.totalSleepMinutes)
            val todayCourse = healthContents.firstOrNull { it.type == "video" && isUploadedContent(it) }

            addView(
                row(Gravity.CENTER_VERTICAL).apply {
                    addView(
                        column().apply {
                            layoutParams = LinearLayout.LayoutParams(0, wrap, 1f)
                            addView(label("早安，小林"))
                            addView(title("今日睡眠报告", 22f))
                        },
                    )
                    addView(
                        FrameLayout(this@MainActivity).apply {
                            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                            addView(icon("bell", C.textSecondary, 24), FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER))
                            addView(
                                View(this@MainActivity).apply { background = oval("#E24B4A") },
                                FrameLayout.LayoutParams(dp(8), dp(8), Gravity.TOP or Gravity.END),
                            )
                        },
                    )
                },
            )

            addView(
                card("#F2F0EA", null).apply {
                    background = gradient("#E6F1FB", "#EAF3DE", 14)
                    addView(
                        row(Gravity.CENTER_VERTICAL).apply {
                            addView(
                                column().apply {
                                    layoutParams = LinearLayout.LayoutParams(0, wrap, 1f)
                                    addView(cardTitle("睡眠得分"))
                                    addView(valueText(latest.score.toString(), 42f, "#185FA5"))
                                    addView(small("${if (scoreDelta >= 0) "↑" else "↓"} 比昨晚${if (scoreDelta >= 0) "提升" else "下降"} ${kotlin.math.abs(scoreDelta)} 分", "#27500A"))
                                },
                            )
                            addView(ScoreRingView(this@MainActivity, latest.score).apply { layoutParams = LinearLayout.LayoutParams(dp(78), dp(78)) })
                        },
                    )
                },
            )
            addView(metricRow(
                metricBox("总睡眠", formatDurationStacked(latest.totalSleepMinutes), minHeightDp = 82),
                metricBox("入睡潜伏", "${latest.sleepLatencyMinutes}\nmin", minHeightDp = 82),
                metricBox("睡眠效率", "${latest.sleepEfficiency}%", minHeightDp = 82),
            ))
            addView(metricRow(
                metricBox("深睡比例", "$deepPercent%", "#1D9E75", minHeightDp = 82),
                metricBox("REM比例", "$remPercent%", "#1D9E75", minHeightDp = 82),
                metricBox("夜醒次数", latest.awakenings.toString(), minHeightDp = 82),
            ))
            addView(nativeMenu("video", "#FAEEDA", "#854F0B", "今日课程", todayCourse?.let { "${it.title} · ${it.duration}" } ?: "正在加载云端课程", "新", onClick = {
                showDetail(contentCenterPage(Page.Home), Page.Home)
            }))
            val recommendedBedTime = acceptedSleepRestrictionBedTime ?: latest.bedTime
            val recommendedWakeTime = acceptedSleepRestrictionWakeTime ?: latest.wakeTime
            val suggestionStatus = if (acceptedSleepRestrictionBedTime == null) "睡眠时间限制建议" else "已接受睡眠时间限制建议"
            addView(notification("bulb", "今日建议", "今晚 $recommendedBedTime 上床（$suggestionStatus）· $recommendedWakeTime 起床", "#185FA5").apply {
                addView(surveyButton("接受建议", true) {
                    showDetail(sleepRestrictionAcceptPage(latest.bedTime, latest.wakeTime), Page.Home)
                })
            })
            addView(
                actionStrip(
                    listOf(
                        "查看睡眠报告" to { showDetail(sleepReportPage(), Page.Data, hideBottom = true) },
                        "生物钟相位" to { showDetail(bioClockPage(), Page.Data, hideBottom = true) },
                    ),
                ),
            )
        }

    private fun sleepRestrictionAcceptPage(prescribedBedTime: String, prescribedWakeTime: String): LinearLayout =
        screen().apply {
            var selectedBedTime = acceptedSleepRestrictionBedTime ?: prescribedBedTime
            var selectedWakeTime = acceptedSleepRestrictionWakeTime ?: prescribedWakeTime
            addView(backHeader("接受建议") { showPage(Page.Home) })
            addView(notification(
                "clock-hour-3",
                "睡眠时间限制确认",
                "您可以在建议时间前后 30 分钟内调整。确认后，系统将与睡眠监测垫回传的实际上床、起床时间进行比对。",
                "#185FA5",
                "#E6F1FB",
                "#0C447C",
                "#0C447C",
            ))
            addView(timeAdjuster("上床时间", prescribedBedTime, selectedBedTime, "#185FA5") { selectedBedTime = it })
            addView(timeAdjuster("起床时间", prescribedWakeTime, selectedWakeTime, "#1D9E75") { selectedWakeTime = it })
            addView(surveyButton("确认", true) {
                acceptedSleepRestrictionBedTime = selectedBedTime
                acceptedSleepRestrictionWakeTime = selectedWakeTime
                showDetail(sleepRestrictionAcceptedPage(selectedBedTime, selectedWakeTime), Page.Home)
            })
        }

    private fun sleepRestrictionAcceptedPage(bedTime: String, wakeTime: String): LinearLayout =
        screen().apply {
            addView(backHeader("时间限制已确认") { showPage(Page.Home) })
            addView(
                card("#EAF3DE", null).apply {
                    addView(row(Gravity.CENTER_VERTICAL).apply {
                        addView(iconCircle("shield-check", "#FFFFFF", "#1D9E75", 42))
                        addView(column().apply {
                            layoutParams = LinearLayout.LayoutParams(0, wrap, 1f)
                            setPadding(dp(10), 0, 0, 0)
                            addView(body("已接受今日睡眠建议", 17f, "#27500A", true))
                            addView(small("今晚 $bedTime 上床 · 明早 $wakeTime 起床", "#27500A"))
                        })
                    })
                },
            )
            addView(notification(
                "device-watch",
                "后续比对逻辑",
                "睡眠监测垫返回数据后，将用实际入床、离床时间与本次确认时间比对，判断是否完成睡眠时间限制。",
                "#1D9E75",
                "#EAF3DE",
                "#085041",
                "#085041",
            ))
            addView(surveyFooterButtons(
                previousText = "继续调整",
                nextText = "返回首页",
                onPrevious = { showDetail(sleepRestrictionAcceptPage(latestSleepRecord().bedTime, latestSleepRecord().wakeTime), Page.Home) },
                onNext = { showPage(Page.Home) },
            ))
        }

    private fun dataCenterPage(): LinearLayout =
        screen().apply {
            val first = sleepRecords.first()
            val latest = latestSleepRecord()
            val totalMinutes = sleepRecords.sumOf { it.totalSleepMinutes }
            val deepPercent = percent(sleepRecords.sumOf { it.deepSleepMinutes }, totalMinutes)
            val remPercent = percent(sleepRecords.sumOf { it.remSleepMinutes }, totalMinutes)

            addView(title("睡眠数据中心", 22f))
            addView(tabRow(listOf("7天" to true, "30天" to false, "全程" to false)))
            addView(
                sevenDaySummaryCard(
                    startDate = first.date,
                    endDate = latest.date,
                    avgSleepMinutes = averageInt { it.totalSleepMinutes },
                    avgEfficiency = averageInt { it.sleepEfficiency },
                    avgLatency = averageInt { it.sleepLatencyMinutes },
                    avgAwakenings = averageOneDecimal { it.awakenings },
                ),
            )
            addView(sectionLabel("睡眠时长趋势"))
            addView(BarChartView(this@MainActivity, sleepRecords.map { it.totalSleepMinutes }).apply { layoutParams = LinearLayout.LayoutParams(match, dp(116)) })
            addView(sectionLabel("关键指标（7天均值）"))
            addView(metricRow(
                metricBox("睡眠效率", "${averageInt { it.sleepEfficiency }}%", "#185FA5"),
                metricBox("入睡潜伏", "${averageInt { it.sleepLatencyMinutes }} min"),
                metricBox("夜醒次数", averageOneDecimal { it.awakenings }),
            ))
            addView(metricRow(
                metricBox("深睡比例", "$deepPercent%", "#1D9E75"),
                metricBox("REM 比例", "$remPercent%", "#1D9E75"),
                metricBox("睡眠负债", formatSleepDebt(averageInt { it.sleepDebtMinutes }), "#D85A30"),
            ))
            addView(sectionLabel("生物钟相位"))
            addView(
                card().apply {
                    addView(
                        row(Gravity.CENTER_VERTICAL).apply {
                            addView(
                                column().apply {
                                    layoutParams = LinearLayout.LayoutParams(0, wrap, 1f)
                                    addView(body("您的睡眠中心时间", 14f, "#25231E", true))
                                    addView(valueText("凌晨 ${latest.midpoint}", 24f, "#185FA5"))
                                },
                            )
                            addView(
                                column(Gravity.END).apply {
                                    addView(small("建议时间"))
                                    addView(body("凌晨 ${latest.recommendedMidpoint}", 16f, "#1D9E75", true))
                                },
                            )
                        },
                    )
                    addView(progress(0.70f, "#185FA5"))
                    addView(small("节律偏移约 ${rhythmShiftHours(latest)} 小时 · 继续提前上床时间"))
                },
            )
            addView(sectionLabel("睡眠趋势"))
            addView(
                nativeMenu("chart-line", "#E6F1FB", "#185FA5", "数据中心·折线趋势", "睡眠效率、睡眠得分、睡眠时长、入睡潜伏", null, onClick = {
                    showDetail(dataTrendPage(), Page.Data)
                }),
            )
            addView(
                nativeMenu("clipboard-list", "#EAF3DE", "#3B6D11", "数据中心·指标详情", "六项核心指标纵向对比", null, onClick = {
                    showDetail(dataDetailsPage(), Page.Data)
                }),
            )
        }

    private fun dataTrendPage(): LinearLayout =
        screen().apply {
            val first = sleepRecords.first()
            val latest = latestSleepRecord()
            val efficiencyPoints = trendPointsHigherIsBetter(sleepRecords.map { it.sleepEfficiency })
            val scorePoints = trendPointsHigherIsBetter(sleepRecords.map { it.score })
            val durationPoints = trendPointsHigherIsBetter(sleepRecords.map { it.totalSleepMinutes })
            val latencyPoints = trendPointsLowerIsBetter(sleepRecords.map { it.sleepLatencyMinutes })

            addView(backHeader("睡眠数据中心") { showPage(Page.Data) })
            addView(tabRow(listOf("7天" to true, "30天" to false, "3个月" to false, "全程" to false)))
            addView(sectionLabel("指标选择"))
            addView(wrapChips(listOf("睡眠效率" to true, "睡眠得分" to true, "睡眠时长" to true, "入睡潜伏" to true)))
            addView(sectionLabel("睡眠效率趋势（近7天）  单位：%"))
            addView(TrendChartView(this@MainActivity, efficiencyPoints, "#185FA5", latest.sleepEfficiency.toString(), sleepRecords.map { it.sleepEfficiency.toString() }, sleepRecords.map { shortDate(it.date) }).apply {
                layoutParams = LinearLayout.LayoutParams(match, dp(124))
            })
            addView(metricRow(
                metricBox("起点", "${first.sleepEfficiency}%", "#D85A30"),
                metricBox("当前", "${latest.sleepEfficiency}%", "#185FA5"),
                metricBox("提升", "+${latest.sleepEfficiency - first.sleepEfficiency}%", "#1D9E75"),
            ))
            addView(sectionLabel("睡眠得分趋势（近7天）  单位：分"))
            addView(TrendChartView(this@MainActivity, scorePoints, "#1D9E75", latest.score.toString(), sleepRecords.map { it.score.toString() }, sleepRecords.map { shortDate(it.date) }).apply {
                layoutParams = LinearLayout.LayoutParams(match, dp(124))
            })
            addView(metricRow(
                metricBox("起点", first.score.toString(), "#D85A30"),
                metricBox("当前", latest.score.toString(), "#1D9E75"),
                metricBox("提升", "+${latest.score - first.score}分", "#1D9E75"),
            ))
            addView(sectionLabel("睡眠时长趋势（近7天）  单位：H"))
            addView(TrendChartView(this@MainActivity, durationPoints, "#378ADD", String.format("%.1f", latest.totalSleepMinutes / 60f), sleepRecords.map { String.format("%.1f", it.totalSleepMinutes / 60f) }, sleepRecords.map { shortDate(it.date) }).apply {
                layoutParams = LinearLayout.LayoutParams(match, dp(124))
            })
            addView(metricRow(
                metricBox("起点", formatDuration(first.totalSleepMinutes), "#D85A30"),
                metricBox("当前", formatDuration(latest.totalSleepMinutes), "#185FA5"),
                metricBox("提升", "↑${formatDuration(latest.totalSleepMinutes - first.totalSleepMinutes)}", "#1D9E75"),
            ))
            addView(sectionLabel("入睡潜伏趋势（近7天）  单位：min"))
            addView(TrendChartView(this@MainActivity, latencyPoints, "#EF9F27", latest.sleepLatencyMinutes.toString(), sleepRecords.map { it.sleepLatencyMinutes.toString() }, sleepRecords.map { shortDate(it.date) }).apply {
                layoutParams = LinearLayout.LayoutParams(match, dp(124))
            })
            addView(metricRow(
                metricBox("起点", "${first.sleepLatencyMinutes}min", "#D85A30"),
                metricBox("当前", "${latest.sleepLatencyMinutes}min", "#BA7517"),
                metricBox("改善", formatSignedMinutes(latest.sleepLatencyMinutes - first.sleepLatencyMinutes), "#1D9E75"),
            ))
        }

    private fun dataDetailsPage(): LinearLayout =
        screen().apply {
            val first = sleepRecords.first()
            val latest = latestSleepRecord()
            addView(backHeader("数据中心·指标详情") { showPage(Page.Data) })
            addView(tabRow(listOf("7天" to true, "30天" to false, "全程" to false)))
            addView(sectionLabel("六项核心指标·纵向对比"))
            addView(indicatorCard("睡眠效率", "${latest.sleepEfficiency}%", "↑${latest.sleepEfficiency - first.sleepEfficiency}%", "#185FA5", "#EAF3DE", "目标 ≥ 85%  · 已达标", latest.sleepEfficiency / 100f))
            addView(indicatorCard("睡眠评分", latest.score.toString(), "↑${latest.score - first.score}分", "#1D9E75", "#EAF3DE", "入组时：${first.score}分", latest.score / 100f))
            addView(indicatorCard("睡眠时长", formatDuration(latest.totalSleepMinutes), "↑${formatDuration(latest.totalSleepMinutes - first.totalSleepMinutes)}", "#185FA5", "#EAF3DE", "睡眠时间限制处方 6.5h · ${if (latest.totalSleepMinutes >= 390) "已超标" else "未达标"}", latest.totalSleepMinutes / 480f))
            addView(indicatorCard("入睡潜伏期", "${latest.sleepLatencyMinutes} min", formatSignedMinutes(latest.sleepLatencyMinutes - first.sleepLatencyMinutes), "#BA7517", "#FAEEDA", "入组时：${first.sleepLatencyMinutes}分钟 · 目标 <20分", latest.sleepLatencyMinutes / 60f))
            addView(indicatorCard("入睡后清醒（WASO）", "${latest.awakenings}次 · ${latest.awakeAfterSleepMinutes}m", "↓${first.awakenings - latest.awakenings}次", "#BA7517", "#FAEEDA", "入组时：${first.awakenings}次 · 共${first.awakeAfterSleepMinutes}分钟", latest.awakeAfterSleepMinutes / 40f))
            addView(indicatorCard("睡眠负债", formatSleepDebt(latest.sleepDebtMinutes), formatSleepDebtDelta(first.sleepDebtMinutes, latest.sleepDebtMinutes), "#D85A30", "#EAF3DE", "入组时：${formatSleepDebt(first.sleepDebtMinutes)} · 目标清零", kotlin.math.abs(latest.sleepDebtMinutes) / 480f))
        }

    private fun healingCenterPage(): LinearLayout =
        screen().apply {
            val bundles = contentBundles()
            val todayCourse = bundles.firstOrNull { it.item.type == "video" }?.item
            val videoCount = bundles.count { it.item.type == "video" }
            val comicCount = bundles.count { it.item.type == "comic" }
            addView(title("疗愈中心", 22f))
            addView(
                card("#E6F1FB", null).apply {
                    addView(body("第 3 周 · 睡眠疗愈 干预计划", 13f, "#185FA5", true))
                    addView(progress(0.42f, "#185FA5", "#B5D4F4"))
                    addView(small("本周完成度 42% · 继续保持", "#0C447C"))
                },
            )
            addView(sectionLabel("睡眠疗愈 核心模块"))
            addView(nativeMenu("clock-hour-3", "#E6F1FB", "#185FA5", "睡眠时间限制", "今晚处方：23:15 上床 · 07:00 起床", "进行中"))
            addView(nativeMenu("bed", "#EAF3DE", "#3B6D11", "睡眠习惯", "5 条行为规则 · 今日完成 3/5", "3/5"))
            addView(nativeMenu("brain", "#FAEEDA", "#854F0B", "认知重构", "识别并纠正睡眠认知扭曲"))
            addView(nativeMenu("activity", "#E1F5EE", "#0F6E56", "放松训练", "PMR · 4-7-8 呼吸 · 正念冥想"))
            addView(sectionLabel("睡眠卫生教育"))
            addView(nativeMenu("video", "#FAEEDA", "#854F0B", "今日课程", todayCourse?.let { "${it.title} · ${it.duration}" } ?: "暂无已上传视频", "新", onClick = {
                showDetail(contentCenterPage(Page.Heal), Page.Heal)
            }))
            addView(nativeMenu("book", "#FCEBEB", "#793434", "健康教育内容中心", "${videoCount}个视频 · ${comicCount}个漫画 · 已上传素材", null, onClick = {
                showDetail(contentCenterPage(Page.Heal), Page.Heal)
            }))
            addView(sectionLabel("工具"))
            addView(nativeMenu("message-circle", "#E6F1FB", "#185FA5", "AI 睡眠助手", "基于您的个人数据回复", null, onClick = { showPage(Page.Qa) }))
            addView(nativeMenu("trending-up", "#EAF3DE", "#3B6D11", "疗愈效果报告", "ISI 趋势、依从性与待改善项", null, onClick = { showDetail(therapyReportPage(), Page.Heal) }))
        }

    private fun contentCenterPage(backPage: Page = Page.Heal, activeFilter: ContentFilter = ContentFilter.All): LinearLayout =
        screen().apply {
            val bundles = contentBundles()
            migrateLegacyWatchedFlags(bundles)
            val visibleBundles = filteredContentBundles(bundles, activeFilter)
            val videoCount = bundles.count { it.item.type == "video" }
            val comicCount = bundles.count { it.item.type == "comic" }
            val watchedVideoCount = bundles.count { it.item.type == "video" && isContentWatched(it) }
            val watchedComicCount = bundles.count { it.item.type == "comic" && isContentWatched(it) }
            addView(backHeader("健康教育内容中心") { showPage(backPage) })
            addView(label("展示云端已上传视频 / 漫画"))
            addView(
                card("#E6F1FB", null).apply {
                    addView(row(Gravity.CENTER_VERTICAL).apply {
                        addView(iconCircle("book", "#FFFFFF", "#185FA5", 38))
                        addView(column().apply {
                            layoutParams = LinearLayout.LayoutParams(0, wrap, 1f)
                            setPadding(dp(10), 0, dp(6), 0)
                            addView(body("云端已上传内容库", 16f, "#0C447C", true))
                            addView(small(apiConfig.url(apiConfig.healthContentsEndpoint), "#0C447C"))
                        })
                        addView(badge("${bundles.size}项", "#185FA5", "#FFFFFF"))
                    })
                    addView(metricRow(
                        metricBox("未看视频", "${videoCount - watchedVideoCount}个", "#185FA5"),
                        metricBox("未看漫画", "${comicCount - watchedComicCount}个", "#793434"),
                        metricBox("已看", "${watchedVideoCount + watchedComicCount}个", "#1D9E75"),
                    ))
                },
            )
            addView(contentFilterTabs(activeFilter, backPage))
            if (visibleBundles.isEmpty()) {
                addView(notification("info-circle", emptyContentTitle(activeFilter), emptyContentMessage(activeFilter), "#185FA5", "#E6F1FB", "#0C447C", "#0C447C"))
            }
            visibleBundles.forEach { bundle ->
                addView(contentItemCard(bundle, backPage, activeFilter))
            }
        }

    private fun contentItemCard(bundle: HealthContentBundle, backPage: Page, activeFilter: ContentFilter): LinearLayout =
        card().apply {
            val item = bundle.item
            val watched = isContentWatched(bundle)
            isClickable = true
            setOnClickListener {
                markContentWatched(bundle)
                if (item.type == "video") {
                    showRawDetail(videoFullscreenPage(item, backPage, activeFilter), backPage, hideBottom = true, immersive = true)
                } else {
                    showDetail(contentDetailPage(bundle, backPage, activeFilter), backPage)
                }
            }
            addView(row(Gravity.CENTER_VERTICAL).apply {
                addView(cloudImage(item.cover.ifBlank { item.url }, widthDp = 74, heightDp = 74))
                addView(column().apply {
                    layoutParams = LinearLayout.LayoutParams(0, wrap, 1f)
                    addView(body(bundleTitle(bundle), 15f, "#25231E", true))
                    addView(small("${item.categoryName.ifBlank { item.categoryKey }} · ${bundleDuration(bundle)}"))
                })
                addView(badge(contentWatchLabel(item, watched), if (watched) "#EAF3DE" else contentTypeFill(item), if (watched) "#27500A" else contentTypeColor(item)))
            })
            addView(body(item.description, 13f, "#65635C").apply { setPadding(0, dp(8), 0, dp(8)) })
            addView(row(Gravity.CENTER_VERTICAL).apply {
                addView(badge(item.triggerQuestionKey.ifBlank { "云端内容" }, "#EAF3DE", "#27500A"))
                addView(small(item.triggerAnswerKey.ifBlank { item.url }).apply {
                    layoutParams = LinearLayout.LayoutParams(0, wrap, 1f)
                    setPadding(dp(8), 0, 0, 0)
                })
                addView(icon("chevron-right", C.textTertiary, 18))
            })
            addView(row().apply {
                setPadding(0, dp(8), 0, 0)
                item.tags.take(3).forEach { addView(tag(it, "#E6F1FB", "#0C447C")) }
            })
        }

    private fun contentDetailPage(bundle: HealthContentBundle, backPage: Page, activeFilter: ContentFilter): LinearLayout =
        screen().apply {
            val item = bundle.item
            addView(backHeader(bundleTitle(bundle)) { showDetail(contentCenterPage(backPage, activeFilter), backPage) })
            addView(comicViewerCard(bundle))
            addView(
                card(if (item.type == "video") "#FAEEDA" else "#FCEBEB", null).apply {
                    addView(row(Gravity.CENTER_VERTICAL).apply {
                        addView(iconCircle(contentTypeIcon(item), "#FFFFFF", contentTypeColor(item), 48))
                        addView(column().apply {
                            layoutParams = LinearLayout.LayoutParams(0, wrap, 1f)
                            setPadding(dp(10), 0, dp(8), 0)
                            addView(body(contentTypeLabel(item), 13f, contentTypeColor(item), true))
                            addView(body(item.subtitle, 16f, "#25231E", true))
                            addView(small(if (item.type == "comic") "页数：${bundle.pages.size}页" else "时长：${item.duration}"))
                        })
                        addView(badge("云端下发", "#EAF3DE", "#27500A"))
                    })
                },
            )
            addView(sectionLabel("内容说明"))
            addView(card().apply {
                addView(body(item.description, 14f, "#65635C"))
                addView(row().apply {
                    setPadding(0, dp(10), 0, 0)
                    item.tags.forEach { addView(tag(it, "#E6F1FB", "#0C447C")) }
                })
            })
        }

    private fun isUploadedContent(item: HealthContentItem): Boolean =
        item.url.contains("/media/") || item.cover.contains("/media/")

    private fun filteredContentBundles(bundles: List<HealthContentBundle>, activeFilter: ContentFilter): List<HealthContentBundle> =
        when (activeFilter) {
            ContentFilter.All -> bundles
            ContentFilter.Video -> bundles.filter { it.item.type == "video" && !isContentWatched(it) }
            ContentFilter.WatchedVideo -> bundles.filter { it.item.type == "video" && isContentWatched(it) }
            ContentFilter.Comic -> bundles.filter { it.item.type == "comic" && !isContentWatched(it) }
            ContentFilter.WatchedComic -> bundles.filter { it.item.type == "comic" && isContentWatched(it) }
        }

    private fun contentFilterTabs(activeFilter: ContentFilter, backPage: Page): HorizontalScrollView =
        HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row().apply {
                ContentFilter.values().forEach { filter ->
                    addView(chip(filter.label, filter == activeFilter).apply {
                        isClickable = true
                        setOnClickListener { showDetail(contentCenterPage(backPage, filter), backPage) }
                    })
                }
            })
        }

    private fun contentWatchKey(bundle: HealthContentBundle): String =
        if (bundle.item.type == "comic") {
            "comic:${bundle.pages.joinToString("|") { it.id }}"
        } else {
            "video:${bundle.item.id}"
        }

    private fun contentIds(bundle: HealthContentBundle): List<String> =
        if (bundle.item.type == "comic") bundle.pages.map { it.id } else listOf(bundle.item.id)

    private fun isContentWatched(bundle: HealthContentBundle): Boolean =
        contentIds(bundle).all { it in cloudWatchedContentIds || it in localWatchedContentIds() } ||
            watchedContentPrefs.getBoolean(contentWatchKey(bundle), false)

    private fun markContentWatched(bundle: HealthContentBundle) {
        val ids = contentIds(bundle)
        saveLocalWatchedContentIds(ids)
        watchedContentPrefs.edit().putBoolean(contentWatchKey(bundle), true).apply()
        syncWatchedContentIds(ids)
    }

    private fun migrateLegacyWatchedFlags(bundles: List<HealthContentBundle>) {
        val legacyIds =
            bundles
                .filter { watchedContentPrefs.getBoolean(contentWatchKey(it), false) }
                .flatMap { contentIds(it) }
                .filter { it !in localWatchedContentIds() }
        if (legacyIds.isNotEmpty()) {
            saveLocalWatchedContentIds(legacyIds)
            syncWatchedContentIds(legacyIds)
        }
    }

    private fun localWatchedContentIds(): Set<String> =
        watchedContentPrefs.getStringSet("watched_content_ids", emptySet()).orEmpty().toSet()

    private fun saveLocalWatchedContentIds(ids: Collection<String>) {
        val merged = localWatchedContentIds().toMutableSet()
        merged.addAll(ids.filter { it.isNotBlank() })
        watchedContentPrefs.edit().putStringSet("watched_content_ids", merged).apply()
    }

    private fun syncPendingWatchedContentIds() {
        val pendingIds = localWatchedContentIds().filter { it !in cloudWatchedContentIds }
        if (pendingIds.isNotEmpty()) syncWatchedContentIds(pendingIds)
    }

    private fun syncWatchedContentIds(ids: Collection<String>) {
        val contentIds = ids.filter { it.isNotBlank() }.distinct()
        if (contentIds.isEmpty() || !apiConfig.enabled || apiConfig.apiBaseUrl.isBlank()) return
        Thread {
            val success = runCatching {
                val payload =
                    JSONObject()
                        .put("userId", contentProgressUserId())
                        .put("contentIds", JSONArray(contentIds))
                        .put("progressPercent", 100)
                        .put("completed", true)
                httpPostJson(apiConfig.url(contentProgressPostEndpoint()), payload)
            }.isSuccess
            if (success) {
                runOnUiThread {
                    cloudWatchedContentIds = cloudWatchedContentIds + contentIds
                }
            }
        }.start()
    }

    private fun contentProgressUserId(): String =
        Regex("[?&]user_id=([^&]+)")
            .find(apiConfig.contentProgressEndpoint)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { Uri.decode(it) }
            ?.takeIf { it.isNotBlank() }
            ?: "demo"

    private fun contentProgressPostEndpoint(): String =
        apiConfig.contentProgressEndpoint.substringBefore("?").ifBlank { "/v1/content-progress" }

    private fun contentWatchLabel(item: HealthContentItem, watched: Boolean): String =
        when {
            watched && item.type == "video" -> "已看视频"
            watched -> "已看漫画"
            item.type == "video" -> "未看视频"
            else -> "未看漫画"
        }

    private fun emptyContentTitle(activeFilter: ContentFilter): String =
        when (activeFilter) {
            ContentFilter.All -> "暂无已上传内容"
            ContentFilter.Video -> "暂无未看视频"
            ContentFilter.WatchedVideo -> "暂无已看视频"
            ContentFilter.Comic -> "暂无未看漫画"
            ContentFilter.WatchedComic -> "暂无已看漫画"
        }

    private fun emptyContentMessage(activeFilter: ContentFilter): String =
        when (activeFilter) {
            ContentFilter.All -> remoteLoadError ?: "请先在 Sleepwell 内容上传工具中上传视频或漫画素材。"
            ContentFilter.Video -> "所有视频都已观看，或当前云端暂无视频素材。"
            ContentFilter.WatchedVideo -> "打开并播放视频后，会自动归入这里。"
            ContentFilter.Comic -> "所有漫画都已观看，或当前云端暂无漫画素材。"
            ContentFilter.WatchedComic -> "打开漫画详情后，会自动归入这里。"
        }

    private fun contentBundles(): List<HealthContentBundle> {
        val uploadedContents = healthContents.filter { isUploadedContent(it) }
        val videos =
            uploadedContents
                .filter { it.type == "video" }
                .sortedBy { it.sortOrder }
                .map { HealthContentBundle(it, listOf(it)) }
        val comics =
            uploadedContents
                .filter { it.type == "comic" }
                .groupBy { comicGroupKey(it) }
                .values
                .mapNotNull { pages ->
                    val sorted =
                        pages.sortedWith(
                            compareBy<HealthContentItem> { it.sortOrder }
                                .thenBy { comicPageIndex(it) },
                        )
                    sorted.firstOrNull()?.let { HealthContentBundle(it, sorted) }
                }
                .sortedBy { it.item.sortOrder }
        return (videos + comics).sortedBy { it.item.sortOrder }
    }

    private fun comicGroupKey(item: HealthContentItem): String =
        listOf(
            item.categoryKey,
            item.triggerQuestionKey,
            item.triggerAnswerKey,
            comicBaseTitle(item.title),
        ).joinToString("|")

    private fun comicBaseTitle(title: String): String =
        title
            .replace(Regex("（第\\d+页）$"), "")
            .replace(Regex("\\(第\\d+页\\)$"), "")
            .trim()

    private fun comicPageIndex(item: HealthContentItem): Int =
        Regex("第(\\d+)页").find(item.title)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: item.sortOrder

    private fun bundleTitle(bundle: HealthContentBundle): String =
        if (bundle.item.type == "comic" && bundle.pages.size > 1) comicBaseTitle(bundle.item.title) else bundle.item.title

    private fun bundleDuration(bundle: HealthContentBundle): String =
        if (bundle.item.type == "comic") "${bundle.pages.size}页" else bundle.item.duration

    private fun videoFullscreenPage(item: HealthContentItem, backPage: Page, activeFilter: ContentFilter): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(match, match)
            setBackgroundColor(Color.BLACK)
            setPadding(dp(12), dp(10), dp(12), dp(14))
            addView(row(Gravity.CENTER_VERTICAL).apply {
                addView(backButton("#FFFFFF") { showDetail(contentCenterPage(backPage, activeFilter), backPage) })
                addView(body(bundleTitle(HealthContentBundle(item, listOf(item))), 16f, "#FFFFFF", true).apply {
                    layoutParams = LinearLayout.LayoutParams(0, wrap, 1f)
                    maxLines = 2
                })
                addView(iconCircle("video", "#1F2937", "#DCEBFF", 38))
            })
            val videoUrl = mediaUrl(item.url)
            val posterUrl = item.cover.takeIf { it.isNotBlank() }?.let { mediaUrl(it) }.orEmpty()
            val webView =
                WebView(this@MainActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(match, 0, 1f).apply {
                        topMargin = dp(10)
                        bottomMargin = dp(8)
                    }
                    setBackgroundColor(Color.BLACK)
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    settings.javaScriptEnabled = false
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.loadsImagesAutomatically = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    loadDataWithBaseURL(
                        videoUrl.substringBeforeLast('/') + "/",
                        videoHtml(videoUrl, posterUrl, item.title),
                        "text/html",
                        "UTF-8",
                        null,
                    )
                }
            addView(webView)
            addView(small("${item.categoryName.ifBlank { item.categoryKey }} · ${item.duration}", "#DCEBFF").apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            })
        }

    private fun videoHtml(videoUrl: String, posterUrl: String, title: String): String {
        val poster = if (posterUrl.isBlank()) "" else " poster=\"${htmlEscape(posterUrl)}\""
        return """
            <!doctype html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
              <style>
                html, body {
                  margin: 0;
                  width: 100%;
                  height: 100%;
                  background: #000;
                  overflow: hidden;
                }
                video {
                  width: 100%;
                  height: 100vh;
                  background: #000;
                  object-fit: contain;
                }
              </style>
            </head>
            <body>
              <video controls autoplay playsinline preload="auto"$poster title="${htmlEscape(title)}">
                <source src="${htmlEscape(videoUrl)}" type="video/mp4">
              </video>
            </body>
            </html>
        """.trimIndent()
    }

    private fun htmlEscape(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    private fun videoPlayerCard(item: HealthContentItem): LinearLayout =
        card("#111827", null).apply {
            addView(body("视频播放", 13f, "#DCEBFF", true).apply { setPadding(0, 0, 0, dp(8)) })
            val videoView = VideoView(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(match, dp(210))
                setVideoURI(Uri.parse(mediaUrl(item.url)))
                val controller = MediaController(this@MainActivity)
                controller.setAnchorView(this)
                setMediaController(controller)
                setOnPreparedListener { seekTo(1) }
            }
            addView(videoView)
            addView(surveyButton("播放视频", true) { videoView.start() }.apply {
                background = solid(Color.parseColor("#185FA5"), Color.TRANSPARENT, 10)
            })
        }

    private fun comicViewerCard(bundle: HealthContentBundle): LinearLayout =
        card("#FCEBEB", null).apply {
            addView(body("漫画查看 · 共 ${bundle.pages.size} 页", 13f, "#793434", true).apply { setPadding(0, 0, 0, dp(8)) })
            bundle.pages.forEachIndexed { index, page ->
                addView(body("第 ${index + 1} 页", 13f, "#793434", true).apply {
                    setPadding(0, if (index == 0) 0 else dp(10), 0, dp(6))
                })
                addView(cloudImage(page.url.ifBlank { page.cover }, heightDp = 430, fitCenter = true).apply {
                    background = solid(Color.WHITE, C.borderTertiary, 10)
                })
            }
        }

    private fun cloudImage(path: String, widthDp: Int? = null, heightDp: Int = 120, fitCenter: Boolean = false): ImageView =
        ImageView(this).apply {
            background = solid(C.bgSecondary, C.borderTertiary, 10)
            scaleType = if (fitCenter) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
            adjustViewBounds = fitCenter
            layoutParams = LinearLayout.LayoutParams(widthDp?.let { dp(it) } ?: match, dp(heightDp)).apply {
                if (widthDp != null) rightMargin = dp(10)
                bottomMargin = dp(8)
            }
            if (path.isNotBlank()) loadCloudImage(path, this)
        }

    private fun loadCloudImage(path: String, target: ImageView) {
        val source = mediaUrl(path)
        Thread {
            val bitmap = runCatching {
                val connection = URL(source).openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 12000
                connection.inputStream.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
            if (bitmap != null) {
                runOnUiThread { target.setImageBitmap(bitmap) }
            }
        }.start()
    }

    private fun mediaUrl(path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val base = apiConfig.apiBaseUrl.trimEnd('/')
        val origin = base.substringBefore("/sleepwell-api")
        return if (path.startsWith("/sleepwell-api/")) {
            origin + path
        } else {
            base + "/" + path.trimStart('/')
        }
    }

    private fun contentTypeLabel(item: HealthContentItem): String =
        if (item.type == "video") "视频" else "漫画"

    private fun contentTypeIcon(item: HealthContentItem): String =
        if (item.type == "video") "video" else "book"

    private fun contentTypeFill(item: HealthContentItem): String =
        if (item.type == "video") "#FAEEDA" else "#FCEBEB"

    private fun contentTypeColor(item: HealthContentItem): String =
        if (item.type == "video") "#854F0B" else "#793434"

    private fun therapyReportPage(): LinearLayout =
        screen().apply {
            addView(
                row(Gravity.CENTER_VERTICAL).apply {
                    addView(backButton { showPage(Page.Heal) })
                    addView(title("疗愈效果报告", 20f).apply { layoutParams = LinearLayout.LayoutParams(0, wrap, 1f) })
                    addView(icon("share", C.textSecondary, 22))
                },
            )
            addView(label("第 3 周 · 2025年5月"))
            addView(
                card("#EAF3DE", null).apply {
                    addView(body("本周最大进步", 14f, "#27500A", true))
                    addView(
                        metricRow(
                            metricBox("睡眠效率", "↑22%", "#3B6D11"),
                            metricBox("入睡时间", "-14min", "#3B6D11"),
                            metricBox("ISI 评分", "-9", "#3B6D11"),
                        ),
                    )
                },
            )
            addView(sectionLabel("ISI 失眠评分趋势"))
            addView(TrendChartView(this@MainActivity, listOf(44f, 38f, 28f, 18f, 8f), "#185FA5", "12→8").apply {
                layoutParams = LinearLayout.LayoutParams(match, dp(96))
            })
            addView(sectionLabel("依从性评估"))
            addView(
                card().apply {
                    addView(progressRow("睡眠时间限制", "86%", "#1D9E75", 0.86f))
                    addView(progressRow("睡眠习惯", "63%", "#BA7517", 0.63f, "#EF9F27"))
                },
            )
            addView(notification("alert-triangle", "待改善项", "本周有 3 晚睡前使用手机 >1 小时，建议下周重点改善。", "#EF9F27", "#FAEEDA", "#633806", "#854F0B"))
            addView(textButton("生成分享卡片", "share") {})
        }

    private fun qaPage(): LinearLayout =
        screen().apply {
            addView(
                row(Gravity.CENTER_VERTICAL).apply {
                    addView(iconCircle("robot", "#E6F1FB", "#185FA5", 36))
                    addView(
                        column().apply {
                            setPadding(dp(10), 0, 0, 0)
                            addView(body("AI 睡眠助手", 18f, "#25231E", true))
                            addView(small("● 基于您的个人数据回复", "#1D9E75"))
                        },
                    )
                },
            )
            addView(chatBubble("您好！我已读取您近 7 天的睡眠数据。您的睡眠效率从 74% 提升到 84%，进步明显！", mine = false))
            addView(chatBubble("为什么我最近更累了，但睡眠数据说好了？", mine = true))
            addView(chatBubble("这是睡眠限制疗法初期的正常反应。您的在床时间减少了，睡眠驱动力在积累，通常 1-2 周后白天精力会显著好转。您现在是第 3 周，即将迎来转折点。", mine = false))
            addView(chatBubble("还需要坚持多久？", mine = true))
            addView(wrapChips(listOf("我的数据解读" to false, "调整处方建议" to false, "放松练习推荐" to false)))
            addView(
                row(Gravity.CENTER_VERTICAL).apply {
                    addView(
                        body("输入问题…", 15f, "#65635C").apply {
                            background = solid(C.bgSecondary, C.borderSecondary, 10)
                            setPadding(dp(12), dp(10), dp(12), dp(10))
                            layoutParams = LinearLayout.LayoutParams(0, wrap, 1f)
                        },
                    )
                    addView(iconCircle("send", "#185FA5", "#FFFFFF", 38).apply { (layoutParams as LinearLayout.LayoutParams).leftMargin = dp(8) })
                },
            )
        }

    private fun communityPage(): LinearLayout =
        screen().apply {
            addView(title("睡眠社区", 22f))
            addView(tabRow(listOf("同路人" to true, "康复故事" to false, "打卡圈" to false)))
            addView(
                card().apply {
                    addView(
                        row(Gravity.CENTER_VERTICAL).apply {
                            addView(avatar("林", "#E1F5EE", "#085041"))
                            addView(
                                column().apply {
                                    layoutParams = LinearLayout.LayoutParams(0, wrap, 1f)
                                    setPadding(dp(8), 0, dp(6), 0)
                                    addView(body("林同路人 · 第 21 天", 14f, "#25231E", true))
                                    addView(small("入睡困难型 · 和您情况相似"))
                                },
                            )
                            addView(badge("配对中", "#EAF3DE", "#27500A"))
                        },
                    )
                    addView(body("「坚持 睡眠时间限制第三周了，昨晚 18 分钟就睡着了，真的有效！」", 14f, "#65635C").apply { setPadding(0, dp(8), 0, dp(8)) })
                    addView(row().apply {
                        addView(smallButton("thumb-up", "12"))
                        addView(smallButton(null, "私信").apply { (layoutParams as LinearLayout.LayoutParams).leftMargin = dp(8) })
                    })
                },
            )
            addView(
                card().apply {
                    addView(row(Gravity.CENTER_VERTICAL).apply {
                        addView(avatar("王", "#FAEEDA", "#633806"))
                        addView(column().apply {
                            setPadding(dp(8), 0, 0, 0)
                            addView(body("王康复大使  ✓", 14f, "#25231E", true))
                            addView(small("疗愈成功 · 180天"))
                        })
                    })
                    addView(body("「分享我的恢复经验：起床时间固定是最关键的一步，比什么都重要…」", 14f, "#65635C").apply { setPadding(0, dp(8), 0, dp(8)) })
                    addView(row().apply {
                        addView(tag("入睡困难", "#EAF3DE", "#27500A"))
                        addView(tag("睡眠时间限制成功", "#E6F1FB", "#0C447C"))
                    })
                },
            )
            addView(sectionLabel("今日打卡"))
            addView(row().apply {
                addView(avatar("我", "#E6F1FB", "#185FA5", outlined = true))
                addView(avatar("林", "#EAF3DE", "#3B6D11", outlined = true))
                addView(avatar("陈", "#FAEEDA", "#854F0B", outlined = true))
                addView(avatar("+", "#F2F0EA", "#9A968D"))
            })
            addView(textButton("发布今日打卡", "plus") {})
        }

    private fun profilePage(): LinearLayout =
        screen().apply {
            addView(
                row(Gravity.CENTER_VERTICAL).apply {
                    addView(avatar("小", "#E6F1FB", "#0C447C", 54))
                    addView(
                        column().apply {
                            layoutParams = LinearLayout.LayoutParams(0, wrap, 1f)
                            setPadding(dp(10), 0, 0, 0)
                            addView(body("小林", 18f, "#25231E", true))
                            addView(small("疗愈第 21 天 · 入睡困难型"))
                        },
                    )
                    addView(icon("settings", C.textSecondary, 24))
                },
            )
            addView(metricRow(
                metricBox("疗愈天数", "21 天", "#185FA5"),
                metricBox("打卡连续", "7 天", "#1D9E75"),
            ))
            addView(sectionLabel("设置与工具"))
            addView(nativeMenu("device-watch", "#FAEEDA", "#854F0B", "监测垫连接状态", "已连接 · 正常同步", "在线"))
            addView(nativeMenu("clipboard-list", "#E6F1FB", "#185FA5", "首次问卷", "8-10分钟 · 生活习性 · 量表评估", "首次", onClick = {
                showDetail(surveyEntryPage(), Page.Profile)
            }))
            addView(nativeMenu("trending-up", "#EAF3DE", "#3B6D11", "月度复查问卷", "3-5分钟 · 生活习惯 · 睡眠变化", "月度", onClick = {
                showDetail(monthlyReviewSurveyPage(), Page.Profile)
            }))
            addView(nativeMenu("clipboard-list", "#E6F1FB", "#185FA5", "调试工具", "云端数据 · 内容清单 · 接口状态", "测试", onClick = {
                showDetail(debugToolsPage(), Page.Profile)
            }))
            addView(nativeMenu("bell", "#FCEBEB", "#793434", "提醒偏好设置", "勿扰时段 · 提醒强度"))
            addView(nativeMenu("file-export", "#EEEDFE", "#533AB7", "导出睡眠报告", "PDF 格式，可发给医生"))
            addView(nativeMenu("shield-lock", "#F2F0EA", "#65635C", "隐私与数据管理", "查看、删除个人数据"))
        }

    private fun surveyEntryPage(): LinearLayout =
        screen().apply {
            addView(backHeader("调查问卷") { showPage(Page.Profile) })
            addView(title("欢迎使用睡眠疗愈", 22f))
            addView(label("完成首次评估，建立您的专属睡眠画像"))
            addView(surveyInfoCard(
                iconName = "clipboard-list",
                accent = "#185FA5",
                fill = "#E6F1FB",
                title = "首次入组评估",
                subtitle = "约需 8-10 分钟 · 仅需完成一次",
                badges = listOf("基础信息", "生活习性", "影响与归因"),
                onClick = { showDetail(initialBasicSurveyPage(), Page.Profile) },
            ))
            addView(surveyInfoCard(
                iconName = "trending-up",
                accent = "#1D9E75",
                fill = "#EAF3DE",
                title = "月度睡眠改善复查",
                subtitle = "约需 3-5 分钟 · 每月 1 次",
                badges = listOf("生活习惯复查", "睡眠变化", "音乐偏好"),
                onClick = { showDetail(monthlyReviewSurveyPage(), Page.Profile) },
            ))
            addView(sectionLabel("首次评估分为四步"))
            addView(stepTimeline(listOf("基础信息" to true, "生活习性" to false, "睡眠影响" to false, "相关因素" to false)))
            addView(surveyButton("开始首次评估", true) { showDetail(initialBasicSurveyPage(), Page.Profile) })
            addView(small("数据仅用于个性化睡眠干预，严格保护隐私").apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            })
        }

    private fun initialBasicSurveyPage(): LinearLayout =
        screen().apply {
            addView(surveyHeader("基础信息", "第 1 步 / 4") { showDetail(surveyEntryPage(), Page.Profile) })
            addView(progress(0.25f, "#185FA5"))
            addView(notification("clipboard-list", "睡眠调查表（首次）", "请填写基本资料。姓名可缩写，信息仅用于建立个人睡眠画像。", "#185FA5", "#E6F1FB", "#0C447C", "#0C447C"))
            addView(surveyInputMock("姓名（可缩写）", "请输入姓名或缩写"))
            addView(surveyInputMock("出生年月", "年 / 月"))
            addView(questionText("性别", "单选"))
            addView(surveyChoiceGroup(listOf("男", "女")))
            addView(questionText("民族", "单选，可补充说明").apply { setPadding(0, dp(8), 0, dp(8)) })
            addView(surveyChoiceGroup(listOf("汉族", "其他")))
            addView(surveyInputMock("其他民族说明", "如有，请填写"))
            addView(questionText("宗教信仰", "单选，可补充说明").apply { setPadding(0, dp(8), 0, dp(8)) })
            addView(surveyChoiceGroup(listOf("无", "佛教", "其他")))
            addView(surveyInputMock("其他宗教说明", "如有，请填写"))
            addView(questionText("婚姻状况", "单选").apply { setPadding(0, dp(8), 0, dp(8)) })
            addView(surveySingleTagCloud(listOf("未婚", "已婚", "丧偶", "离婚", "其他"), itemsPerRow = 3))
            addView(questionText("床伴", "是否有同床或同房睡眠伴侣").apply { setPadding(0, dp(8), 0, dp(8)) })
            addView(surveyChoiceGroup(listOf("有", "无")))
            addView(questionText("床伴的睡眠情况", "可多选").apply { setPadding(0, dp(8), 0, dp(8)) })
            addView(surveyTagCloud(listOf("正常", "打鼾", "睡觉时拳打脚踢", "失眠"), itemsPerRow = 2))
            addView(questionText("职业情况", "单选").apply { setPadding(0, dp(8), 0, dp(8)) })
            addView(surveySingleTagCloud(listOf("无业", "待业", "在职", "退休"), itemsPerRow = 2))
            addView(questionText("居住方式", "单选").apply { setPadding(0, dp(8), 0, dp(8)) })
            addView(surveySingleTagCloud(listOf("独居", "集体宿舍", "与家人同住", "其他"), itemsPerRow = 2))
            addView(questionText("文化程度", "单选").apply { setPadding(0, dp(8), 0, dp(8)) })
            addView(surveyChoiceGroup(listOf("初中及以下", "高中", "本科及以上")))
            addView(surveyFooterButtons(
                previousText = "返回入口",
                nextText = "下一步",
                onPrevious = { showDetail(surveyEntryPage(), Page.Profile) },
                onNext = { showDetail(initialLifestyleSurveyPage(), Page.Profile) },
            ))
        }

    private fun initialLifestyleSurveyPage(): LinearLayout =
        screen().apply {
            addView(surveyHeader("生活习性评估", "第 2 步 / 4") { showDetail(initialBasicSurveyPage(), Page.Profile) })
            addView(progress(0.50f, "#185FA5"))
            addView(questionText("饮酒习惯", "单选，并选择习惯饮酒时间"))
            val alcoholTimes = surveyTagCloud(listOf("早", "中", "晚", "睡前"), itemsPerRow = 4)
            addView(surveyChoiceGroup(listOf("是", "否"), onSelected = { setSurveyEnabled(alcoholTimes, it == "是") }))
            addView(alcoholTimes)
            setSurveyEnabled(alcoholTimes, false)
            addView(questionText("吸烟习惯", "单选，并选择习惯吸烟时间").apply { setPadding(0, dp(8), 0, dp(8)) })
            val smokingTimes = surveyTagCloud(listOf("白天", "晚上", "睡前"), itemsPerRow = 3)
            addView(surveyChoiceGroup(listOf("是", "否"), onSelected = { setSurveyEnabled(smokingTimes, it == "是") }))
            addView(smokingTimes)
            setSurveyEnabled(smokingTimes, false)
            addView(questionText("喝茶习惯", "单选，并选择习惯喝茶时间").apply { setPadding(0, dp(8), 0, dp(8)) })
            val teaTimes = surveyTagCloud(listOf("上午", "下午", "晚上", "睡前"), itemsPerRow = 4)
            addView(surveyChoiceGroup(listOf("是", "否"), onSelected = { setSurveyEnabled(teaTimes, it == "是") }))
            addView(teaTimes)
            setSurveyEnabled(teaTimes, false)
            addView(questionText("含咖啡因类饮料习惯", "可乐 / 咖啡；单选，并选择习惯饮用时间").apply { setPadding(0, dp(8), 0, dp(8)) })
            val caffeineTimes = surveyTagCloud(listOf("上午", "下午", "晚上", "睡前"), itemsPerRow = 4)
            addView(surveyChoiceGroup(listOf("是", "否"), onSelected = { setSurveyEnabled(caffeineTimes, it == "是") }))
            addView(caffeineTimes)
            setSurveyEnabled(caffeineTimes, false)
            addView(questionText("运动习惯", "单选，并选择运动类型").apply { setPadding(0, dp(8), 0, dp(8)) })
            val exerciseType = surveySingleTagCloud(listOf("有氧", "无氧"), itemsPerRow = 2)
            val exerciseProjectLabel = questionText("习惯运动项目", "可多选").apply { setPadding(0, dp(8), 0, dp(8)) }
            val exerciseProjects = surveyTagCloud(
                listOf("散步", "快走", "慢跑", "竞走", "游泳", "骑自行车", "打太极拳", "跳舞", "做韵律操", "跳绳", "打篮球", "踢足球"),
                itemsPerRow = 3,
            )
            addView(surveyChoiceGroup(listOf("有", "无"), onSelected = {
                val enabled = it == "有"
                setSurveyEnabled(exerciseType, enabled)
                setSurveyEnabled(exerciseProjectLabel, enabled)
                setSurveyEnabled(exerciseProjects, enabled)
            }))
            addView(exerciseType)
            addView(exerciseProjectLabel)
            addView(exerciseProjects)
            setSurveyEnabled(exerciseType, false)
            setSurveyEnabled(exerciseProjectLabel, false)
            setSurveyEnabled(exerciseProjects, false)
            addView(questionText("睡前行为", "选择符合情况的项目").apply { setPadding(0, dp(8), 0, dp(8)) })
            addView(surveyCheckOption("睡前3小时内运动", false))
            addView(surveyCheckOption("睡前看电子产品", false))
            addView(surveyCheckOption("夜间睡不着时看时间", false))
            addView(surveyFooterButtons(
                previousText = "上一步",
                nextText = "下一步",
                onPrevious = { showDetail(initialBasicSurveyPage(), Page.Profile) },
                onNext = { showDetail(initialSleepImpactSurveyPage(), Page.Profile) },
            ))
        }

    private fun initialSleepImpactSurveyPage(): LinearLayout =
        screen().apply {
            addView(surveyHeader("睡眠影响评估", "第 3 步 / 4") { showDetail(initialLifestyleSurveyPage(), Page.Profile) })
            addView(progress(0.75f, "#185FA5"))
            addView(questionText("陌生睡眠环境", "假如当晚更换为陌生的睡眠环境，您的睡眠情况是"))
            addView(surveyChoiceGroup(listOf("睡得比平常更好", "睡得跟平常一样", "睡得比平常要差")))
            addView(questionText("睡眠环境变化程度", "1-10分；分数越高表示变化程度越高").apply { setPadding(0, dp(8), 0, dp(8)) })
            addView(scaleTenRow())
            addView(questionText("睡眠不好对生活的影响", "可多选").apply { setPadding(0, dp(8), 0, dp(8)) })
            addView(surveyCheckList(
                listOf("担心/紧张", "容易犯错", "注意力不集中", "记忆力下降", "烦躁", "疲劳", "犯困", "易激惹", "做事主动性下降", "对睡眠状况不满意", "行为紊乱"),
            ))
            addView(surveyFooterButtons(
                previousText = "上一步",
                nextText = "下一步",
                onPrevious = { showDetail(initialLifestyleSurveyPage(), Page.Profile) },
                onNext = { showDetail(initialCauseSurveyPage(), Page.Profile) },
            ))
        }

    private fun initialCauseSurveyPage(): LinearLayout =
        screen().apply {
            addView(surveyHeader("相关因素与心理特征", "第 4 步 / 4") { showDetail(initialSleepImpactSurveyPage(), Page.Profile) })
            addView(progress(1.0f, "#185FA5"))
            addView(notification("shield-check", "最后一步", "这些因素会用于后续健康教育内容推荐，例如睡前手机、生活压力、作息不规律、饮酒、喝茶、抽烟、午睡等。", "#185FA5", "#E6F1FB", "#0C447C", "#0C447C"))
            addView(questionText("您的性格特点", "可多选"))
            addView(surveyTagCloud(listOf("外向", "内向", "敏感", "压抑", "追求完美", "思虑过多", "较真", "要强"), itemsPerRow = 3))
            addView(questionText("睡眠不好相关因素", "可多选，选出所有符合情况的项目").apply { setPadding(0, dp(8), 0, dp(8)) })
            addView(surveyCheckList(
                listOf(
                    "从小就睡眠不好",
                    "父亲或母亲睡眠不好",
                    "白天睡太多",
                    "白天活动不足",
                    "白天躺着时间太久",
                    "过晚饮用含咖啡因饮料",
                    "过多饮用含咖啡因饮料",
                    "与床伴睡眠时间不同步",
                    "社会/工作压力导致不良睡眠时间表",
                    "睡眠环境变化",
                    "倒班",
                    "熬夜加班",
                    "假期作息不规律",
                    "更换睡眠环境",
                    "早上赖床",
                    "太早上床",
                    "睡眠时间不规律",
                    "缺乏规律光照",
                    "饮食不规律",
                    "生活压力大",
                    "床上做与睡眠无关的事",
                    "过度担心睡眠",
                    "睡前或床上过度担心和思虑",
                    "强光、噪声等环境干扰",
                    "半夜看时间",
                    "太用力入睡",
                    "晚上剧烈运动",
                ),
            ))
            addView(surveyFooterButtons(
                previousText = "上一步",
                nextText = "提交问卷",
                onPrevious = { showDetail(initialSleepImpactSurveyPage(), Page.Profile) },
                onNext = { showDetail(surveyEntryPage(), Page.Profile) },
            ))
        }

    private fun monthlyReviewSurveyPage(): LinearLayout =
        screen().apply {
            addView(surveyHeader("月度睡眠调查表", "第 1 步 / 3") { showPage(Page.Profile) })
            addView(progress(0.33f, "#3B6D11", "#C0DD97"))
            addView(notification(
                "trending-up",
                "近期睡眠状态复查",
                "用于了解本月生活习惯变化，并为下一步睡眠疗愈内容推荐提供依据。",
                "#1D9E75",
                "#EAF3DE",
                "#085041",
                "#085041",
            ))
            addView(questionText("饮酒习惯", "单选，并选择习惯饮酒的时间"))
            val monthlyAlcoholTimes = surveyTagCloud(listOf("早", "中", "晚", "睡前"), itemsPerRow = 4)
            addView(surveyChoiceGroup(listOf("是", "否"), onSelected = { setSurveyEnabled(monthlyAlcoholTimes, it == "是") }))
            addView(monthlyAlcoholTimes)
            setSurveyEnabled(monthlyAlcoholTimes, false)
            addView(questionText("吸烟习惯", "单选，并选择习惯吸烟的时间").apply { setPadding(0, dp(8), 0, dp(8)) })
            val monthlySmokingTimes = surveyTagCloud(listOf("白天", "晚上", "睡前"), itemsPerRow = 3)
            addView(surveyChoiceGroup(listOf("是", "否"), onSelected = { setSurveyEnabled(monthlySmokingTimes, it == "是") }))
            addView(monthlySmokingTimes)
            setSurveyEnabled(monthlySmokingTimes, false)
            addView(questionText("喝茶习惯", "单选，并选择习惯喝茶的时间").apply { setPadding(0, dp(8), 0, dp(8)) })
            val monthlyTeaTimes = surveyTagCloud(listOf("上午", "下午", "晚上", "睡前"), itemsPerRow = 4)
            addView(surveyChoiceGroup(listOf("是", "否"), onSelected = { setSurveyEnabled(monthlyTeaTimes, it == "是") }))
            addView(monthlyTeaTimes)
            setSurveyEnabled(monthlyTeaTimes, false)
            addView(questionText("含咖啡因类饮料习惯", "可乐 / 咖啡；单选，并选择习惯饮用时间").apply { setPadding(0, dp(8), 0, dp(8)) })
            val monthlyCaffeineTimes = surveyTagCloud(listOf("上午", "下午", "晚上", "睡前"), itemsPerRow = 4)
            addView(surveyChoiceGroup(listOf("是", "否"), onSelected = { setSurveyEnabled(monthlyCaffeineTimes, it == "是") }))
            addView(monthlyCaffeineTimes)
            setSurveyEnabled(monthlyCaffeineTimes, false)
            addView(surveyFooterButtons(
                previousText = "返回",
                nextText = "下一步",
                onPrevious = { showPage(Page.Profile) },
                onNext = { showDetail(monthlyExerciseSurveyPage(), Page.Profile) },
            ))
        }

    private fun monthlyExerciseSurveyPage(): LinearLayout =
        screen().apply {
            addView(surveyHeader("运动与睡前行为", "第 2 步 / 3") { showDetail(monthlyReviewSurveyPage(), Page.Profile) })
            addView(progress(0.66f, "#3B6D11", "#C0DD97"))
            addView(questionText("运动习惯", "单选，并选择习惯运动类型"))
            val monthlyExerciseType = surveySingleTagCloud(listOf("有氧", "无氧"), itemsPerRow = 2)
            val monthlyExerciseProjectLabel = questionText("习惯运动项目", "可多选").apply { setPadding(0, dp(8), 0, dp(8)) }
            val monthlyExerciseProjects = surveyCheckList(
                listOf("散步", "快走", "慢跑", "竞走", "游泳", "骑自行车", "打太极拳", "跳舞", "做韵律操", "跳绳", "打篮球", "踢足球"),
            )
            addView(surveyChoiceGroup(listOf("有", "无"), onSelected = {
                val enabled = it == "有"
                setSurveyEnabled(monthlyExerciseType, enabled)
                setSurveyEnabled(monthlyExerciseProjectLabel, enabled)
                setSurveyEnabled(monthlyExerciseProjects, enabled)
            }))
            addView(monthlyExerciseType)
            addView(monthlyExerciseProjectLabel)
            addView(monthlyExerciseProjects)
            setSurveyEnabled(monthlyExerciseType, false)
            setSurveyEnabled(monthlyExerciseProjectLabel, false)
            setSurveyEnabled(monthlyExerciseProjects, false)
            addView(questionText("睡前3小时内运动的习惯", "单选").apply { setPadding(0, dp(8), 0, dp(8)) })
            addView(surveyChoiceGroup(listOf("有", "无")))
            addView(questionText("睡前是否有看电子产品的习惯", "单选").apply { setPadding(0, dp(8), 0, dp(8)) })
            addView(surveyChoiceGroup(listOf("有", "无")))
            addView(questionText("夜间睡不着时有看时间的习惯", "单选").apply { setPadding(0, dp(8), 0, dp(8)) })
            addView(surveyChoiceGroup(listOf("有", "无")))
            addView(surveyFooterButtons(
                previousText = "上一步",
                nextText = "下一步",
                onPrevious = { showDetail(monthlyReviewSurveyPage(), Page.Profile) },
                onNext = { showDetail(monthlyFeedbackSurveyPage(), Page.Profile) },
            ))
        }

    private fun monthlyFeedbackSurveyPage(): LinearLayout =
        screen().apply {
            addView(surveyHeader("睡眠变化与偏好", "第 3 步 / 3") { showDetail(monthlyExerciseSurveyPage(), Page.Profile) })
            addView(progress(1.0f, "#3B6D11", "#C0DD97"))
            addView(notification(
                "heart-rate-monitor",
                "月度复查反馈",
                "您的选择将用于调整下月睡眠疗愈建议、健康教育视频与漫画推荐。",
                "#1D9E75",
                "#EAF3DE",
                "#085041",
                "#085041",
            ))
            addView(questionText("您自我感觉近期的睡眠情况", "单选"))
            addView(surveyChoiceGroup(listOf("明显好转", "没有感觉变化", "比原来更差")))
            addView(questionText("清醒状态时播放的音乐偏好", "可多选").apply { setPadding(0, dp(10), 0, dp(8)) })
            addView(surveyTagCloud(listOf("呼吸引导", "粉红噪声", "正念冥想", "肌肉放松"), itemsPerRow = 2))
            addView(surveyFooterButtons(
                previousText = "上一步",
                nextText = "提交复查",
                onPrevious = { showDetail(monthlyExerciseSurveyPage(), Page.Profile) },
                onNext = { showPage(Page.Profile) },
            ))
        }

    private fun surveyHeader(text: String, meta: String, onBack: () -> Unit): LinearLayout =
        row(Gravity.CENTER_VERTICAL).apply {
            addView(backButton { onBack() })
            addView(body(text, 18f, "#25231E", true).apply { layoutParams = LinearLayout.LayoutParams(0, wrap, 1f) })
            addView(badge(meta, "#F2F0EA", "#65635C"))
        }

    private fun surveyInfoCard(
        iconName: String,
        accent: String,
        fill: String,
        title: String,
        subtitle: String,
        badges: List<String>,
        onClick: () -> Unit,
    ): LinearLayout =
        card().apply {
            background = solid(C.bgSecondary, Color.parseColor(accent), 12, strokeWidthDp = 0)
            isClickable = true
            setOnClickListener { onClick() }
            addView(row(Gravity.CENTER_VERTICAL).apply {
                addView(icon(iconName, Color.parseColor(accent), 20))
                addView(column().apply {
                    layoutParams = LinearLayout.LayoutParams(0, wrap, 1f)
                    setPadding(dp(8), 0, dp(4), 0)
                    addView(body(title, 15f, "#25231E", true))
                    addView(small(subtitle))
                })
                addView(icon("chevron-right", C.textTertiary, 18))
            })
            addView(row().apply {
                setPadding(0, dp(10), 0, 0)
                badges.forEach { addView(badge(it, fill, if (accent == "#1D9E75") "#27500A" else "#0C447C")) }
            })
        }

    private fun stepTimeline(steps: List<Pair<String, Boolean>>): LinearLayout =
        row(Gravity.CENTER_VERTICAL).apply {
            steps.forEach { (text, active) ->
                addView(View(this@MainActivity).apply {
                    background = oval(if (active) "#185FA5" else "#C7C4BA")
                    layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply { rightMargin = dp(5) }
                })
                addView(small(text, if (active) "#25231E" else "#65635C").apply {
                    layoutParams = LinearLayout.LayoutParams(0, wrap, 1f)
                })
            }
            setPadding(0, 0, 0, dp(10))
        }

    private fun questionText(title: String, subtitle: String): LinearLayout =
        column().apply {
            addView(body(title, 15f, "#25231E", true))
            addView(small(subtitle))
            setPadding(0, dp(4), 0, dp(8))
        }

    private fun timeAdjuster(label: String, baseTime: String, initialTime: String, accent: String, onSelected: (String) -> Unit): LinearLayout =
        card().apply {
            val offsets = listOf(-30, -15, 0, 15, 30)
            var selectedOffset = offsets.firstOrNull { shiftClockTime(baseTime, it) == initialTime } ?: 0
            val accentColor = Color.parseColor(accent)
            val valueView = valueText(shiftClockTime(baseTime, selectedOffset), 26f, accent)
            val chips = mutableListOf<TextView>()

            fun optionLabel(offset: Int): String =
                when {
                    offset < 0 -> "提前${kotlin.math.abs(offset)}"
                    offset > 0 -> "延后$offset"
                    else -> "建议"
                }

            fun applyStates() {
                valueView.text = shiftClockTime(baseTime, selectedOffset)
                chips.forEachIndexed { index, chip ->
                    val active = offsets[index] == selectedOffset
                    chip.setTextColor(if (active) Color.WHITE else C.textSecondary)
                    chip.typeface = Typeface.create("sans", if (active) Typeface.BOLD else Typeface.NORMAL)
                    chip.background = solid(if (active) accentColor else C.bgSecondary, if (active) accentColor else C.borderTertiary, 8)
                }
            }

            addView(row(Gravity.CENTER_VERTICAL).apply {
                addView(column().apply {
                    layoutParams = LinearLayout.LayoutParams(0, wrap, 1f)
                    addView(body(label, 15f, "#25231E", true))
                    addView(small("建议 $baseTime · 可在前后 30 分钟内调整"))
                })
                addView(valueView)
            })
            addView(row().apply {
                setPadding(0, dp(10), 0, 0)
                offsets.forEach { offset ->
                    val chip = body(optionLabel(offset), 11f, "#65635C", offset == selectedOffset).apply {
                        gravity = Gravity.CENTER
                        setPadding(dp(4), dp(8), dp(4), dp(8))
                        layoutParams = LinearLayout.LayoutParams(0, wrap, 1f).apply { rightMargin = dp(5) }
                        isClickable = true
                        setOnClickListener {
                            selectedOffset = offset
                            onSelected(shiftClockTime(baseTime, selectedOffset))
                            applyStates()
                        }
                    }
                    chips.add(chip)
                    addView(chip)
                }
            })
            applyStates()
        }

    private fun shiftClockTime(time: String, offsetMinutes: Int): String {
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val total = (hour * 60 + minute + offsetMinutes).floorMod(24 * 60)
        return "%02d:%02d".format(total / 60, total % 60)
    }

    private fun Int.floorMod(modulus: Int): Int =
        ((this % modulus) + modulus) % modulus

    private fun surveyInputMock(label: String, placeholder: String): LinearLayout =
        column().apply {
            setPadding(0, dp(5), 0, dp(7))
            addView(body(label, 14f, "#25231E", true))
            addView(EditText(this@MainActivity).apply {
                hint = placeholder
                setHintTextColor(Color.parseColor("#9A968D"))
                setTextColor(Color.parseColor("#25231E"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.create("sans", Typeface.NORMAL)
                includeFontPadding = true
                inputType = InputType.TYPE_CLASS_TEXT
                background = solid(C.bgSecondary, C.borderSecondary, 8)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                layoutParams = LinearLayout.LayoutParams(match, wrap).apply { topMargin = dp(6) }
            })
        }

    private fun surveyChoiceGroup(options: List<String>, selectedIndex: Int = -1, onSelected: ((String) -> Unit)? = null): LinearLayout =
        column().apply {
            var selected = selectedIndex
            val rows = mutableListOf<LinearLayout>()
            val marks = mutableListOf<TextView>()
            val labels = mutableListOf<TextView>()

            fun applyStates() {
                rows.forEachIndexed { index, choiceRow ->
                    val active = selected == index
                    choiceRow.background = solid(if (active) Color.parseColor("#E6F1FB") else C.bgPrimary, if (active) Color.parseColor("#185FA5") else C.borderTertiary, 10)
                    marks[index].background = solid(if (active) Color.parseColor("#185FA5") else C.transparent, if (active) Color.parseColor("#185FA5") else C.borderSecondary, 8, strokeWidthDp = 2)
                    labels[index].setTextColor(Color.parseColor(if (active) "#0C447C" else "#25231E"))
                }
            }

            options.forEachIndexed { index, text ->
                val mark = TextView(this@MainActivity).apply {
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    layoutParams = LinearLayout.LayoutParams(dp(17), dp(17)).apply { rightMargin = dp(9) }
                }
                val labelView = body(text, 14f, "#25231E").apply {
                    layoutParams = LinearLayout.LayoutParams(0, wrap, 1f)
                }
                val choiceRow = row(Gravity.CENTER_VERTICAL).apply {
                    setPadding(dp(10), dp(9), dp(10), dp(9))
                    layoutParams = LinearLayout.LayoutParams(match, wrap).apply { bottomMargin = dp(6) }
                    addView(mark)
                    addView(labelView)
                    isClickable = true
                    setOnClickListener {
                        selected = index
                        applyStates()
                        onSelected?.invoke(text)
                    }
                }
                rows.add(choiceRow)
                marks.add(mark)
                labels.add(labelView)
                addView(choiceRow)
            }
            applyStates()
        }

    private fun surveyTagCloud(options: List<String>, selected: Set<String> = emptySet(), itemsPerRow: Int = 3): LinearLayout =
        tagRows(options.chunked(itemsPerRow).map { rowItems -> rowItems.map { it to selected.contains(it) } })

    private fun surveySingleTagCloud(options: List<String>, selectedIndex: Int = -1, itemsPerRow: Int = 3): LinearLayout =
        column().apply {
            var selected = selectedIndex
            val tags = mutableListOf<TextView>()

            fun applyStates() {
                tags.forEachIndexed { index, tag ->
                    val active = selected == index
                    tag.setTextColor(Color.parseColor(if (active) "#0C447C" else "#65635C"))
                    tag.background = solid(if (active) Color.parseColor("#E6F1FB") else C.bgPrimary, if (active) Color.parseColor("#185FA5") else C.borderSecondary, 8)
                }
            }

            options.chunked(itemsPerRow).forEachIndexed { rowIndex, rowItems ->
                addView(row().apply {
                    rowItems.forEachIndexed { columnIndex, text ->
                        val tagIndex = rowIndex * itemsPerRow + columnIndex
                        val tag = body(text, 12f, "#65635C").apply {
                            setPadding(dp(8), dp(6), dp(8), dp(6))
                            layoutParams = LinearLayout.LayoutParams(wrap, wrap).apply {
                                rightMargin = dp(5)
                                bottomMargin = dp(6)
                            }
                            isClickable = true
                            setOnClickListener {
                                selected = tagIndex
                                applyStates()
                            }
                        }
                        tags.add(tag)
                        addView(tag)
                    }
                })
            }
            applyStates()
        }

    private fun surveyCheckList(options: List<String>): LinearLayout =
        column().apply {
            options.forEach { addView(surveyCheckOption(it, false)) }
        }

    private fun scaleTenRow(selectedValue: Int = -1): LinearLayout =
        column().apply {
            var selected = selectedValue
            val boxes = mutableListOf<Pair<Int, TextView>>()

            fun applyStates() {
                boxes.forEach { (value, box) ->
                    val active = selected == value
                    box.setTextColor(Color.parseColor(if (active) "#FFFFFF" else "#65635C"))
                    box.typeface = Typeface.create("sans", if (active) Typeface.BOLD else Typeface.NORMAL)
                    box.background = solid(if (active) C.accent else C.bgSecondary, if (active) C.accent else C.borderTertiary, 6)
                }
            }

            (1..10).chunked(5).forEach { rowValues ->
                addView(row().apply {
                    rowValues.forEach { value ->
                        val box = body(value.toString(), 13f, "#65635C").apply {
                            gravity = Gravity.CENTER
                            layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).apply {
                                rightMargin = dp(5)
                                bottomMargin = dp(6)
                            }
                            isClickable = true
                            setOnClickListener {
                                selected = value
                                applyStates()
                            }
                        }
                        boxes.add(value to box)
                        addView(box)
                    }
                })
            }
            applyStates()
        }

    private fun setSurveyEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        view.alpha = if (enabled) 1f else 0.42f
        if (view is ViewGroup) setSurveyChildrenEnabled(view, enabled)
    }

    private fun setSurveyChildrenEnabled(group: ViewGroup, enabled: Boolean) {
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index)
            child.isEnabled = enabled
            if (child is ViewGroup) setSurveyChildrenEnabled(child, enabled)
        }
    }

    private fun surveyOption(text: String, selected: Boolean): LinearLayout =
        surveyChoice(text, selected, square = false)

    private fun surveyCheckOption(text: String, selected: Boolean): LinearLayout =
        surveyChoice(text, selected, square = true)

    private fun surveyChoice(text: String, selected: Boolean, square: Boolean): LinearLayout =
        row(Gravity.CENTER_VERTICAL).apply {
            var active = selected
            setPadding(dp(10), dp(9), dp(10), dp(9))
            layoutParams = LinearLayout.LayoutParams(match, wrap).apply { bottomMargin = dp(6) }
            val mark = TextView(this@MainActivity).apply {
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                layoutParams = LinearLayout.LayoutParams(dp(17), dp(17)).apply { rightMargin = dp(9) }
            }
            val labelView = body(text, 14f, if (active) "#0C447C" else "#25231E").apply {
                layoutParams = LinearLayout.LayoutParams(0, wrap, 1f)
            }
            fun applyState() {
                background = solid(if (active) Color.parseColor("#E6F1FB") else C.bgPrimary, if (active) Color.parseColor("#185FA5") else C.borderTertiary, 10)
                mark.background = solid(if (active) Color.parseColor("#185FA5") else C.transparent, if (active) Color.parseColor("#185FA5") else C.borderSecondary, if (square) 4 else 8, strokeWidthDp = 2)
                mark.text = if (active && square) "✓" else ""
                labelView.setTextColor(Color.parseColor(if (active) "#0C447C" else "#25231E"))
            }
            addView(mark)
            addView(labelView)
            applyState()
            isClickable = true
            setOnClickListener {
                active = !active
                applyState()
            }
        }

    private fun surveyFooterButtons(previousText: String, nextText: String, onPrevious: () -> Unit, onNext: () -> Unit): LinearLayout =
        row().apply {
            setPadding(0, dp(10), 0, 0)
            addView(surveyButton(previousText, false, onPrevious).apply {
                layoutParams = LinearLayout.LayoutParams(0, wrap, 1f).apply { rightMargin = dp(8) }
            })
            addView(surveyButton(nextText, true, onNext).apply {
                layoutParams = LinearLayout.LayoutParams(0, wrap, 2f)
            })
        }

    private fun surveyButton(text: String, primary: Boolean, onClick: () -> Unit): TextView =
        body(text, 15f, if (primary) "#FFFFFF" else "#25231E", true).apply {
            background = solid(if (primary) C.accent else C.bgSecondary, if (primary) Color.TRANSPARENT else C.borderSecondary, 10)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
            isClickable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(match, wrap).apply { topMargin = dp(8) }
        }

    private fun tagRows(rows: List<List<Pair<String, Boolean>>>): LinearLayout =
        column().apply {
            rows.forEach { rowItems ->
                addView(row().apply {
                    rowItems.forEach { (text, selected) -> addView(surveyTag(text, selected)) }
                })
            }
        }

    private fun surveyTag(text: String, selected: Boolean): TextView =
        body(text, 12f, if (selected) "#0C447C" else "#65635C").apply {
            var active = selected
            fun applyState() {
                setTextColor(Color.parseColor(if (active) "#0C447C" else "#65635C"))
                background = solid(if (active) Color.parseColor("#E6F1FB") else C.bgPrimary, if (active) Color.parseColor("#185FA5") else C.borderSecondary, 8)
            }
            setPadding(dp(8), dp(6), dp(8), dp(6))
            layoutParams = LinearLayout.LayoutParams(wrap, wrap).apply {
                rightMargin = dp(5)
                bottomMargin = dp(6)
            }
            isClickable = true
            setOnClickListener {
                active = !active
                applyState()
            }
            applyState()
        }

    private fun scaleRow(selected: Int, accent: String): LinearLayout =
        row().apply {
            (0..4).forEach { index ->
                addView(body(index.toString(), 13f, if (index == selected) "#FFFFFF" else "#65635C", index == selected).apply {
                    background = solid(if (index == selected) Color.parseColor(accent) else C.bgSecondary, if (index == selected) Color.parseColor(accent) else C.borderTertiary, 6)
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).apply {
                        if (index > 0) leftMargin = dp(5)
                        bottomMargin = dp(8)
                    }
                })
            }
        }

    private fun habitReviewRow(label: String, selected: Int): LinearLayout =
        column().apply {
            setPadding(0, dp(6), 0, dp(7))
            addView(body(label, 14f, "#25231E"))
            addView(row().apply {
                setPadding(0, dp(6), 0, 0)
                addView(reviewChoice("明显改善", selected == 0, "#EAF3DE", "#27500A", "#97C459"))
                addView(reviewChoice("部分改善", selected == 1, "#FAEEDA", "#633806", "#EF9F27"))
                addView(reviewChoice("未改善", selected == 2, "#FCEBEB", "#791F1F", "#F09595"))
            })
        }

    private fun reviewChoice(text: String, selected: Boolean, fill: String, color: String, stroke: String): TextView =
        body(text, 12f, if (selected) color else "#65635C").apply {
            background = solid(if (selected) Color.parseColor(fill) else C.bgSecondary, if (selected) Color.parseColor(stroke) else Color.TRANSPARENT, 8)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, wrap, 1f).apply { rightMargin = dp(5) }
        }

    private fun debugToolsPage(): LinearLayout {
        if (sleepRecords.isEmpty()) return cloudDataStatePage("调试工具")
        return screen().apply {
            val first = sleepRecords.first()
            val latest = latestSleepRecord()
            val bundles = contentBundles()
            val videoCount = bundles.count { it.item.type == "video" }
            val comicCount = bundles.count { it.item.type == "comic" }
            val config = apiConfig
            addView(backHeader("调试工具") { showPage(Page.Profile) })
            addView(label("云端模拟数据 · 云端内容清单 · 联调状态"))
            addView(sectionLabel("睡眠数据源"))
            addView(
                card("#E6F1FB", null).apply {
                    addView(row(Gravity.CENTER_VERTICAL).apply {
                        addView(iconCircle("clipboard-list", "#FFFFFF", "#185FA5", 38))
                        addView(column().apply {
                            layoutParams = LinearLayout.LayoutParams(0, wrap, 1f)
                            setPadding(dp(10), 0, dp(6), 0)
                            addView(body("7 天睡眠数据模型", 16f, "#0C447C", true))
                            addView(small(config.url(config.sleepRecordsEndpoint), "#0C447C"))
                        })
                        addView(badge(sleepDataSource, "#185FA5", "#FFFFFF"))
                    })
                    addView(metricRow(
                        metricBox("记录数", "${sleepRecords.size}晚", "#185FA5"),
                        metricBox("日期范围", "${shortDate(first.date)}-${shortDate(latest.date)}", "#185FA5"),
                    ))
                },
            )
            addView(sectionLabel("最近一晚快照"))
            addView(metricRow(
                metricBox("睡眠评分", latest.score.toString(), if (latest.score >= 80) "#1D9E75" else "#BA7517"),
                metricBox("睡眠效率", "${latest.sleepEfficiency}%", "#185FA5"),
                metricBox("总睡眠", formatDuration(latest.totalSleepMinutes), "#185FA5"),
            ))
            addView(metricRow(
                metricBox("入睡潜伏", "${latest.sleepLatencyMinutes}min", "#BA7517"),
                metricBox("深睡比例", "${percent(latest.deepSleepMinutes, latest.totalSleepMinutes)}%", "#185FA5"),
                metricBox("REM比例", "${percent(latest.remSleepMinutes, latest.totalSleepMinutes)}%", "#793434"),
            ))
            addView(sectionLabel("健康教育内容"))
            addView(
                card().apply {
                    addView(metricRow(
                        metricBox("视频", "${videoCount}个", "#185FA5"),
                        metricBox("漫画", "${comicCount}个", "#793434"),
                        metricBox("来源", contentDataSource, "#185FA5"),
                    ))
                    addView(small("APP 不再打包本地视频或漫画清单，内容由云端接口返回。"))
                    addView(nativeMenu("book", "#FCEBEB", "#793434", "查看健康教育内容中心", "读取云端视频 / 漫画清单", null, onClick = {
                        showDetail(contentCenterPage(Page.Profile), Page.Profile)
                    }))
                },
            )
            addView(sectionLabel("APP 接口配置"))
            addView(
                card().apply {
                    addView(body(config.apiBaseUrl, 15f, "#185FA5", true))
                    addView(small("模式：${config.mode}"))
                    addView(progressRow("睡眠监测垫数据链路", if (remoteLoadError == null) "已联通" else "待恢复", if (remoteLoadError == null) "#1D9E75" else "#BA7517", if (remoteLoadError == null) 0.82f else 0.36f, if (remoteLoadError == null) "#1D9E75" else "#EF9F27"))
                    addView(body("已配置接口", 13f, "#65635C", true))
                    addView(small("GET ${config.sleepRecordsEndpoint}"))
                    addView(small("GET ${config.healthContentsEndpoint}"))
                    addView(small("GET ${config.deviceStatusEndpoint}"))
                    addView(small("POST ${config.uploadEndpoint}"))
                },
            )
        }
    }

    private fun sleepReportPage(): LinearLayout =
        LinearLayout(this).apply {
            val latest = latestSleepRecord()
            val deepPercent = percent(latest.deepSleepMinutes, latest.totalSleepMinutes)
            val remPercent = percent(latest.remSleepMinutes, latest.totalSleepMinutes)
            val headline = if (latest.score >= 80) "继续保持" else "善待自己"
            val headlineBody =
                if (latest.score >= 80) {
                    "你的睡眠分数保持在较好水平。继续维持稳定起床时间，并关注睡前放松与夜醒变化。"
                } else {
                    "你的睡眠分数低于往常。睡眠没有按计划发展时，最好的做法就温柔对待自己。一晚并不能代表整体睡眠健康，更好的夜晚依旧可期。"
                }

            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(18))
            setBackgroundColor(C.dark)
            addView(
                row(Gravity.CENTER_VERTICAL).apply {
                    addView(backButton("#8899AA") { showPage(Page.Data) })
                    addView(body("睡眠", 17f, "#E8EDF5", true).apply {
                        gravity = Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(0, wrap, 1f)
                    })
                    addView(icon("share", Color.parseColor("#8899AA"), 22))
                },
            )
            addView(row().apply {
                addView(body("昨天", 13f, "#8899AA"))
                addView(body("今天", 13f, "#E8EDF5", true).apply {
                    setPadding(dp(14), 0, 0, dp(2))
                })
            })
            addView(body(headline, 25f, "#E8EDF5", true).apply { setPadding(0, dp(14), 0, dp(4)) })
            addView(body(headlineBody, 14f, "#8899AA"))
            addView(darkSection("相关因素"))
            addView(darkFactor("总睡眠时长", formatDuration(latest.totalSleepMinutes), "#E8EDF5", latest.totalSleepMinutes / 480f, "#5DCAA5"))
            addView(darkFactor("效率", "${latest.sleepEfficiency}%", "#E8EDF5", latest.sleepEfficiency / 100f, "#5DCAA5"))
            addView(darkFactor("睡眠安稳度", latest.stability, "#E8EDF5", if (latest.stability == "良好") 0.75f else 0.50f, "#5DCAA5"))
            addView(darkFactor("快速眼动期", "${latest.remSleepMinutes}分 · $remPercent%", "#EF9F27", remPercent / 30f))
            addView(darkFactor("深度睡眠", "${latest.deepSleepMinutes}分 · $deepPercent%", "#E8EDF5", deepPercent / 25f, "#378ADD"))
            addView(darkFactor("睡眠潜伏期", "${latest.sleepLatencyMinutes}分钟", "#EF9F27", (60 - latest.sleepLatencyMinutes).coerceAtLeast(0) / 60f))
            addView(darkFactor("入睡时机", latest.sleepTiming, "#E8EDF5", if (latest.sleepTiming == "良好") 0.72f else 0.45f, "#5DCAA5"))
            addView(darkSection("主要指标"))
            addView(metricRow(
                metricBox("总睡眠", formatDuration(latest.totalSleepMinutes), "#E8EDF5", dark = true),
                metricBox("睡眠评分", latest.score.toString(), if (latest.score >= 80) "#5DCAA5" else "#EF9F27", dark = true),
                metricBox("睡眠负债", formatSleepDebt(latest.sleepDebtMinutes), "#EF9F27", dark = true),
            ))
            addView(darkBottomNav())
        }

    private fun bioClockPage(): LinearLayout =
        LinearLayout(this).apply {
            val latest = latestSleepRecord()
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(18))
            setBackgroundColor(C.dark)
            addView(
                row(Gravity.CENTER_VERTICAL).apply {
                    addView(backButton("#8899AA") { showPage(Page.Data) })
                    addView(body("生物钟", 17f, "#E8EDF5", true).apply {
                        gravity = Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(0, wrap, 1f)
                    })
                    addView(icon("info-circle", Color.parseColor("#8899AA"), 22))
                },
            )
            addView(CircadianClockView(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(match, dp(240))
            })
            addView(
                darkCard().apply {
                    addView(body("睡眠周期同步", 13f, "#8899AA"))
                    addView(body("延后", 24f, "#E8EDF5", true))
                    addView(body("你的睡眠中点比昼夜节律类型延后 ${rhythmShiftHours(latest)} 小时。", 14f, "#8899AA"))
                },
            )
            addView(
                row().apply {
                    addView(darkMiniCard("昼夜节律类型", "早夜型", "#E8EDF5"))
                    addView(darkMiniCard("睡眠规律", latest.stability, "#5DCAA5").apply { (layoutParams as LinearLayout.LayoutParams).leftMargin = dp(8) })
                },
            )
            addView(
                darkCard().apply {
                    addView(body("节律调整建议", 15f, "#E8EDF5", true))
                    addView(bullet("每天早晨 7:00 进行 10 分钟户外光照，有助提前生物钟节律"))
                    addView(bullet("工作日与休息日起床时间差控制在 1 小时内，减少社会时差"))
                },
            )
        }

    private fun renderBottomNav() {
        bottomNav.removeAllViews()
        val tabs =
            listOf(
                Triple(Page.Home, "home", "首页"),
                Triple(Page.Data, "chart-line", "数据"),
                if (currentPage == Page.Qa) Triple(Page.Qa, "message-circle", "问答") else Triple(Page.Heal, "heart-rate-monitor", "疗愈"),
                Triple(Page.Community, "users", "社区"),
                Triple(Page.Profile, "user", "我的"),
            )
        tabs.forEach { (page, iconName, text) ->
            val active = page == currentPage || (page == Page.Heal && currentPage == Page.Qa)
            bottomNav.addView(
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    isClickable = true
                    setOnClickListener { showPage(page) }
                    layoutParams = LinearLayout.LayoutParams(0, wrap, 1f)
                    addView(icon(iconName, if (active) C.accent else C.textTertiary, 23))
                    addView(body(text, 11f, if (active) "#185FA5" else "#9A968D").apply { gravity = Gravity.CENTER })
                },
            )
        }
    }

    private fun backHeader(text: String, onBack: () -> Unit): LinearLayout =
        row(Gravity.CENTER_VERTICAL).apply {
            addView(backButton { onBack() })
            addView(title(text, 20f).apply { layoutParams = LinearLayout.LayoutParams(0, wrap, 1f) })
        }

    private fun backButton(color: String = "#65635C", onBack: () -> Unit): TextView =
        icon("arrow-left", Color.parseColor(color), 24).apply {
            isClickable = true
            setOnClickListener { onBack() }
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
            gravity = Gravity.CENTER
        }

    private fun notification(
        iconName: String,
        title: String,
        body: String,
        accent: String,
        fill: String = "#F2F0EA",
        titleColor: String = "#25231E",
        bodyColor: String = "#65635C",
    ): LinearLayout =
        card(fill, null).apply {
            background = solid(Color.parseColor(fill), Color.parseColor(accent), 12, strokeWidthDp = 0)
            addView(row(Gravity.CENTER_VERTICAL).apply {
                addView(icon(iconName, Color.parseColor(accent), 17))
                addView(body(title, 14f, titleColor, true).apply { setPadding(dp(6), 0, 0, 0) })
            })
            addView(body(body, 13f, bodyColor))
        }

    private fun nativeMenu(
        iconName: String,
        iconFill: String,
        iconColor: String,
        title: String,
        subtitle: String,
        badgeText: String? = null,
        onClick: (() -> Unit)? = null,
    ): LinearLayout =
        row(Gravity.CENTER_VERTICAL).apply {
            setPadding(dp(10), dp(10), dp(8), dp(10))
            background = solid(C.transparent, Color.TRANSPARENT, 12)
            isClickable = onClick != null
            if (onClick != null) setOnClickListener { onClick() }
            addView(iconCircle(iconName, iconFill, iconColor, 38))
            addView(
                column().apply {
                    layoutParams = LinearLayout.LayoutParams(0, wrap, 1f)
                    setPadding(dp(10), 0, dp(8), 0)
                    addView(body(title, 15f, "#25231E", true))
                    addView(small(subtitle))
                },
            )
            if (badgeText != null) {
                addView(badge(badgeText, "#E6F1FB", "#0C447C"))
            } else {
                addView(icon("chevron-right", C.textTertiary, 18))
            }
        }

    private fun sevenDaySummaryCard(
        startDate: String,
        endDate: String,
        avgSleepMinutes: Int,
        avgEfficiency: Int,
        avgLatency: Int,
        avgAwakenings: String,
    ): LinearLayout =
        card("#E6F1FB", null).apply {
            addView(
                row(Gravity.CENTER_VERTICAL).apply {
                    addView(
                        column().apply {
                            layoutParams = LinearLayout.LayoutParams(0, wrap, 1f)
                            addView(body("近7天数据概览", 16f, "#0C447C", true))
                            addView(small("${shortDate(startDate)} - ${shortDate(endDate)} · 共 ${sleepRecords.size} 晚记录", "#0C447C"))
                        },
                    )
                    addView(badge("7天", "#185FA5", "#FFFFFF"))
                },
            )
            addView(
                row().apply {
                    setPadding(0, dp(10), 0, 0)
                    addView(summaryCell("平均总睡眠", formatDuration(avgSleepMinutes), "#185FA5"))
                    addView(summaryCell("平均效率", "$avgEfficiency%", "#185FA5"))
                },
            )
            addView(
                row().apply {
                    setPadding(0, dp(6), 0, 0)
                    addView(summaryCell("平均入睡潜伏", "${avgLatency}min", "#BA7517"))
                    addView(summaryCell("平均夜醒", avgAwakenings, "#25231E"))
                },
            )
        }

    private fun summaryCell(label: String, value: String, valueColor: String): LinearLayout =
        column().apply {
            layoutParams = LinearLayout.LayoutParams(0, wrap, 1f).apply {
                rightMargin = dp(6)
            }
            addView(small(label, "#0C447C"))
            addView(body(value, 18f, valueColor, true))
        }

    private fun shortDate(date: String): String =
        date.split("-").let { parts ->
            if (parts.size >= 3) "${parts[1]}/${parts[2]}" else date
        }

    private fun indicatorCard(
        name: String,
        value: String,
        delta: String,
        valueColor: String,
        badgeFill: String,
        caption: String,
        percent: Float,
    ): LinearLayout =
        card().apply {
            addView(row(Gravity.CENTER_VERTICAL).apply {
                addView(body(name, 15f, "#25231E", true).apply { layoutParams = LinearLayout.LayoutParams(0, wrap, 1f) })
                addView(body(value, 17f, valueColor, true))
                addView(badge(delta, badgeFill, if (badgeFill == "#FAEEDA") "#633806" else "#27500A").apply { (layoutParams as LinearLayout.LayoutParams).leftMargin = dp(8) })
            })
            addView(progress(percent, valueColor))
            addView(small(caption))
        }

    private fun progressRow(label: String, value: String, valueColor: String, percent: Float, barColor: String = valueColor): LinearLayout =
        column().apply {
            setPadding(0, dp(4), 0, dp(8))
            addView(row(Gravity.CENTER_VERTICAL).apply {
                addView(body(label, 14f, "#25231E").apply { layoutParams = LinearLayout.LayoutParams(0, wrap, 1f) })
                addView(body(value, 14f, valueColor, true))
            })
            addView(progress(percent, barColor))
        }

    private fun darkFactor(label: String, value: String, valueColor: String, percent: Float, barColor: String = "#EF9F27"): LinearLayout =
        column().apply {
            addView(row(Gravity.CENTER_VERTICAL).apply {
                addView(body(label, 14f, "#E8EDF5").apply { layoutParams = LinearLayout.LayoutParams(0, wrap, 1f) })
                addView(body(value, 14f, valueColor, true))
                addView(icon("chevron-right", Color.parseColor("#8899AA"), 16))
            })
            addView(progress(percent, barColor, "#2A3A4A").apply { (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(8) })
        }

    private fun darkBottomNav(): LinearLayout =
        row(Gravity.CENTER).apply {
            setPadding(0, dp(12), 0, 0)
            listOf("walk" to "#8899AA", "moon" to "#378ADD", "flame" to "#8899AA", "tools-kitchen-2" to "#8899AA", "heart" to "#8899AA", "chart-area" to "#8899AA").forEach { (name, color) ->
                addView(icon(name, Color.parseColor(color), 26).apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f)
                    gravity = Gravity.CENTER
                })
            }
        }

    private fun darkMiniCard(label: String, value: String, color: String): LinearLayout =
        darkCard().apply {
            layoutParams = LinearLayout.LayoutParams(0, wrap, 1f)
            addView(body(label, 13f, "#8899AA"))
            addView(body(value, 17f, color, true))
            addView(icon("chevron-right", Color.parseColor("#8899AA"), 16))
        }

    private fun bullet(text: String): LinearLayout =
        row(Gravity.TOP).apply {
            setPadding(0, dp(8), 0, 0)
            addView(View(this@MainActivity).apply {
                background = oval("#378ADD")
                layoutParams = LinearLayout.LayoutParams(dp(7), dp(7)).apply {
                    topMargin = dp(6)
                    rightMargin = dp(10)
                }
            })
            addView(body(text, 13f, "#8899AA").apply { layoutParams = LinearLayout.LayoutParams(0, wrap, 1f) })
        }

    private fun darkSection(text: String): TextView =
        body(text, 16f, "#E8EDF5", true).apply { setPadding(0, dp(16), 0, dp(8)) }

    private fun darkCard(): LinearLayout =
        card("#2A3A4A", null).apply {
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

    private fun chatBubble(text: String, mine: Boolean): TextView =
        body(text, 14f, if (mine) "#25231E" else "#0C447C").apply {
            background = solid(if (mine) C.bgSecondary else Color.parseColor("#E6F1FB"), Color.TRANSPARENT, 12)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams =
                LinearLayout.LayoutParams((resources.displayMetrics.widthPixels * if (mine) 0.72f else 0.86f).toInt(), wrap).apply {
                    topMargin = dp(10)
                    gravity = if (mine) Gravity.END else Gravity.START
                }
            gravity = if (mine) Gravity.END else Gravity.START
        }

    private fun actionStrip(actions: List<Pair<String, () -> Unit>>): LinearLayout =
        row().apply {
            actions.forEachIndexed { index, action ->
                addView(textButton(action.first, null, action.second).apply {
                    layoutParams = LinearLayout.LayoutParams(0, wrap, 1f).apply {
                        if (index > 0) leftMargin = dp(8)
                    }
                })
            }
        }

    private fun tabRow(tabs: List<Pair<String, Boolean>>): HorizontalScrollView =
        HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(
                row().apply {
                    tabs.forEach { (text, active) ->
                        addView(chip(text, active))
                    }
                },
            )
        }

    private fun wrapChips(chips: List<Pair<String, Boolean>>): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(8))
            chips.forEach { (text, active) -> addView(chip(text, active)) }
        }

    private fun chip(text: String, active: Boolean): TextView =
        body(text, 13f, if (active) "#FFFFFF" else "#65635C", active).apply {
            background = solid(if (active) C.accent else C.bgSecondary, Color.TRANSPARENT, 9)
            setPadding(dp(12), dp(7), dp(12), dp(7))
            layoutParams = LinearLayout.LayoutParams(wrap, wrap).apply { rightMargin = dp(8); bottomMargin = dp(8) }
        }

    private fun metricRow(vararg views: View): LinearLayout =
        row().apply {
            views.forEachIndexed { index, view ->
                view.layoutParams = LinearLayout.LayoutParams(0, wrap, 1f).apply {
                    if (index > 0) leftMargin = dp(8)
                    bottomMargin = dp(8)
                }
                addView(view)
            }
        }

    private fun metricBox(label: String, value: String, valueColor: String = "#25231E", dark: Boolean = false, minHeightDp: Int = 0): LinearLayout =
        column(Gravity.CENTER).apply {
            background = solid(if (dark) Color.parseColor("#2A3A4A") else C.bgSecondary, if (dark) Color.TRANSPARENT else C.borderTertiary, 12)
            setPadding(dp(8), dp(10), dp(8), dp(10))
            if (minHeightDp > 0) {
                minimumHeight = dp(minHeightDp)
            }
            addView(body(label, 12f, if (dark) "#8899AA" else "#65635C").apply { gravity = Gravity.CENTER })
            addView(body(value, 17f, valueColor, true).apply { gravity = Gravity.CENTER })
        }

    private fun progress(percent: Float, fill: String, track: String = "#F2F0EA"): ProgressStripView =
        ProgressStripView(this, percent, Color.parseColor(fill), Color.parseColor(track)).apply {
            layoutParams = LinearLayout.LayoutParams(match, dp(9)).apply {
                topMargin = dp(8)
                bottomMargin = dp(7)
            }
        }

    private fun textButton(text: String, iconName: String? = null, onClick: () -> Unit): TextView =
        body(if (iconName == null) text else "＋  $text", 15f, if (iconName == null) "#25231E" else "#FFFFFF", true).apply {
            background = solid(if (iconName == null) C.bgSecondary else C.accent, if (iconName == null) C.borderSecondary else Color.TRANSPARENT, 12)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { onClick() }
            isClickable = true
            layoutParams = LinearLayout.LayoutParams(match, wrap).apply { topMargin = dp(8) }
        }

    private fun smallButton(iconName: String?, text: String): TextView =
        body(if (iconName == null) text else "${iconText(iconName)}  $text", 13f, "#65635C").apply {
            if (iconName != null) typeface = tablerTypeface
            background = solid(C.transparent, C.borderSecondary, 10)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(wrap, wrap)
        }

    private fun legend(text: String, color: String): TextView =
        body("■ $text", 12f, "#65635C").apply {
            setTextColor(Color.parseColor(color))
            setPadding(0, 0, dp(12), dp(8))
        }

    private fun sleepStageSummary(): TextView =
        body("深睡50M 23%；浅睡3H40M 60%；快速动眼期 50M 23%；", 13f, "#65635C", true).apply {
            background = solid(C.bgSecondary, C.borderTertiary, 10)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            layoutParams = LinearLayout.LayoutParams(match, wrap).apply {
                bottomMargin = dp(8)
            }
        }

    private fun tag(text: String, fill: String, color: String): TextView =
        body(text, 12f, color).apply {
            background = solid(Color.parseColor(fill), Color.TRANSPARENT, 9)
            setPadding(dp(9), dp(4), dp(9), dp(4))
            layoutParams = LinearLayout.LayoutParams(wrap, wrap).apply { rightMargin = dp(6) }
        }

    private fun badge(text: String, fill: String, color: String): TextView =
        tag(text, fill, color).apply { setTypeface(Typeface.DEFAULT_BOLD) }

    private fun avatar(text: String, fill: String, color: String, size: Int = 38, outlined: Boolean = false): TextView =
        body(text, 15f, color, true).apply {
            gravity = Gravity.CENTER
            background = solid(Color.parseColor(fill), if (outlined) Color.parseColor(color) else Color.TRANSPARENT, size / 2, if (outlined) 2 else 0)
            layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)).apply { rightMargin = dp(6) }
        }

    private fun iconCircle(iconName: String, fill: String, iconColor: String, size: Int): FrameLayout =
        FrameLayout(this).apply {
            background = solid(Color.parseColor(fill), Color.TRANSPARENT, size / 3)
            layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
            addView(icon(iconName, Color.parseColor(iconColor), (size * 0.52f).toInt()), FrameLayout.LayoutParams(match, match, Gravity.CENTER))
        }

    private fun icon(name: String, color: Int, sizeSp: Int): TextView =
        TextView(this).apply {
            text = iconText(name)
            typeface = tablerTypeface
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp.toFloat())
            gravity = Gravity.CENTER
            includeFontPadding = false
        }

    private fun iconText(name: String): String {
        val code = iconCodes[name] ?: error("Missing icon: $name")
        return String(Character.toChars(code))
    }

    private fun title(text: String, sp: Float): TextView =
        body(text, sp, "#25231E", true).apply { setPadding(0, dp(2), 0, dp(10)) }

    private fun cardTitle(text: String): TextView =
        body(text, 14f, "#25231E", true)

    private fun valueText(text: String, sp: Float, color: String): TextView =
        body(text, sp, color, true)

    private fun sectionLabel(text: String): TextView =
        body(text, 13f, "#65635C", true).apply {
            setPadding(0, dp(12), 0, dp(6))
            letterSpacing = 0.04f
        }

    private fun label(text: String): TextView =
        body(text, 13f, "#65635C")

    private fun small(text: String, color: String = "#65635C"): TextView =
        body(text, 12f, color)

    private fun body(text: String, sp: Float, color: String, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor(color))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setLineSpacing(0f, 1.14f)
            if (bold) setTypeface(Typeface.DEFAULT_BOLD)
            includeFontPadding = true
        }

    private fun row(gravity: Int = Gravity.NO_GRAVITY): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            this.gravity = gravity
        }

    private fun column(gravity: Int = Gravity.NO_GRAVITY): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            this.gravity = gravity
        }

    private fun card(fill: String = "#F2F0EA", stroke: String? = "#E2E0D7"): LinearLayout =
        column().apply {
            background = solid(Color.parseColor(fill), stroke?.let { Color.parseColor(it) } ?: Color.TRANSPARENT, 14)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(match, wrap).apply { bottomMargin = dp(10) }
        }

    private fun solid(fill: Int, strokeColor: Int, radiusDp: Int, strokeWidthDp: Int = 1): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeWidthDp > 0) setStroke(dp(strokeWidthDp), strokeColor)
        }

    private fun oval(fill: String): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(fill))
        }

    private fun gradient(start: String, end: String, radiusDp: Int): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor(start), Color.parseColor(end))).apply {
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private companion object {
        const val match = ViewGroup.LayoutParams.MATCH_PARENT
        const val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

        object C {
            val bgPrimary: Int = Color.parseColor("#FBFAF6")
            val bgSecondary: Int = Color.parseColor("#F2F0EA")
            val borderSecondary: Int = Color.parseColor("#C7C4BA")
            val borderTertiary: Int = Color.parseColor("#E2E0D7")
            val textSecondary: Int = Color.parseColor("#65635C")
            val textTertiary: Int = Color.parseColor("#9A968D")
            val accent: Int = Color.parseColor("#185FA5")
            val dark: Int = Color.parseColor("#1A2332")
            val transparent: Int = Color.TRANSPARENT
        }

        val iconCodes =
            mapOf(
                "wifi" to 0xEB52,
                "battery-2" to 0xEA30,
                "bell" to 0xEA35,
                "home" to 0xEAC1,
                "chart-line" to 0xEA5C,
                "heart-rate-monitor" to 0xEF61,
                "users" to 0xEBF2,
                "user" to 0xEB4D,
                "clock-hour-3" to 0xF318,
                "bed" to 0xEB5C,
                "brain" to 0xF59F,
                "activity" to 0xED23,
                "chevron-right" to 0xEA61,
                "video" to 0xED22,
                "book" to 0xEA39,
                "robot" to 0xF00B,
                "message-circle" to 0xEAED,
                "send" to 0xEB1E,
                "thumb-up" to 0xEB3C,
                "shield-check" to 0xEB22,
                "share" to 0xEB21,
                "trending-up" to 0xEB43,
                "alert-triangle" to 0xEA06,
                "settings" to 0xEB20,
                "clipboard-list" to 0xEA6D,
                "mood-smile" to 0xEAF7,
                "device-watch" to 0xEBF9,
                "file-export" to 0xEDE9,
                "shield-lock" to 0xED58,
                "arrow-left" to 0xEA19,
                "info-circle" to 0xEAC5,
                "walk" to 0xEC87,
                "moon" to 0xEAF8,
                "flame" to 0xEC2C,
                "tools-kitchen-2" to 0xEEFF,
                "heart" to 0xEABE,
                "chart-area" to 0xEA58,
                "plus" to 0xEB0B,
                "bulb" to 0xEA51,
            )
    }
}

private class ScoreRingView(context: android.content.Context, private val score: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        val pad = size * 0.16f
        val rect = RectF(pad, pad, size - pad, size - pad)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * 0.09f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.parseColor("#B5D4F4")
        canvas.drawArc(rect, 0f, 360f, false, paint)
        paint.color = Color.parseColor("#185FA5")
        canvas.drawArc(rect, -90f, 360f * score / 100f, false, paint)
        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = size * 0.20f
        canvas.drawText(score.toString(), size / 2f, size / 2f + paint.textSize / 3f, paint)
    }
}

private class SleepStageView(context: android.content.Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val heights = intArrayOf(12, 20, 36, 36, 24, 28, 36, 16, 28, 20, 12)
    private val colors = intArrayOf(
        Color.parseColor("#B5D4F4"),
        Color.parseColor("#378ADD"),
        Color.parseColor("#185FA5"),
        Color.parseColor("#185FA5"),
        Color.parseColor("#378ADD"),
        Color.parseColor("#9FE1CB"),
        Color.parseColor("#185FA5"),
        Color.parseColor("#378ADD"),
        Color.parseColor("#9FE1CB"),
        Color.parseColor("#378ADD"),
        Color.parseColor("#B5D4F4"),
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val gap = 4f
        val barWidth = (width - gap * (heights.size - 1)) / heights.size
        val base = height - 12f
        heights.forEachIndexed { index, rawHeight ->
            paint.color = colors[index]
            val h = rawHeight / 42f * (height - 18f)
            val left = index * (barWidth + gap)
            canvas.drawRoundRect(left, base - h, left + barWidth, base, 5f, 5f, paint)
        }
    }
}

private class BarChartView(
    context: android.content.Context,
    private val sleepMinutes: List<Int>,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labels = arrayOf("一", "二", "三", "四", "五", "六", "日")

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val gap = width * 0.035f
        val labelH = 22f
        val topLabelH = 26f
        val count = sleepMinutes.size.coerceAtLeast(1)
        val barW = (width - gap * (count + 1)) / count
        val maxH = height - labelH - 8f
        val maxMinutes = sleepMinutes.maxOrNull()?.coerceAtLeast(1) ?: 1
        sleepMinutes.forEachIndexed { index, minutes ->
            paint.color = if (minutes >= 420) Color.parseColor("#185FA5") else if (minutes >= 360) Color.parseColor("#378ADD") else Color.parseColor("#B5D4F4")
            val left = gap + index * (barW + gap)
            val chartHeight = maxH - topLabelH
            val top = topLabelH + chartHeight - minutes / maxMinutes.toFloat() * chartHeight
            canvas.drawRoundRect(left, top, left + barW, maxH, 7f, 7f, paint)
            paint.color = Color.parseColor("#65635C")
            paint.textSize = 19f
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText(String.format("%.1fH", minutes / 60f), left + barW / 2f, top - 6f, paint)
            paint.typeface = Typeface.DEFAULT
            paint.textSize = 24f
            canvas.drawText(labels.getOrElse(index) { "" }, left + barW / 2f, height - 2f, paint)
        }
    }
}

private class TrendChartView(
    context: android.content.Context,
    private val points: List<Float>,
    private val lineColor: String,
    private val callout: String,
    private val pointLabels: List<String> = emptyList(),
    private val dayLabels: List<String> = emptyList(),
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.isEmpty()) return
        val leftPad = 30f
        val rightPad = 30f
        val topPad = 30f
        val bottomPad = 32f
        val chartHeight = height - topPad - bottomPad
        val minPoint = points.minOrNull() ?: 0f
        val maxPoint = points.maxOrNull() ?: 1f
        val range = (maxPoint - minPoint).takeIf { it > 0.01f } ?: 1f

        paint.strokeWidth = 1.2f
        paint.color = Color.parseColor("#D3D1C7")
        for (i in 0..2) {
            val y = topPad + i * (chartHeight / 2f)
            canvas.drawLine(leftPad, y, width - rightPad, y, paint)
        }

        fun xFor(index: Int): Float =
            if (points.size == 1) width / 2f else leftPad + index * ((width - leftPad - rightPad) / (points.size - 1))

        fun yFor(value: Float): Float =
            topPad + ((value - minPoint) / range) * chartHeight

        paint.color = Color.parseColor(lineColor)
        paint.strokeWidth = 4f
        paint.style = Paint.Style.STROKE
        var lastX = xFor(0)
        var lastY = yFor(points[0])
        for (i in 1 until points.size) {
            val x = xFor(i)
            val y = yFor(points[i])
            canvas.drawLine(lastX, lastY, x, y, paint)
            lastX = x
            lastY = y
        }

        paint.style = Paint.Style.FILL
        points.forEachIndexed { index, point ->
            val x = xFor(index)
            val y = yFor(point)
            paint.color = Color.parseColor(lineColor)
            canvas.drawCircle(x, y, 5.5f, paint)
            val label = pointLabels.getOrNull(index)
            if (!label.isNullOrBlank()) {
                paint.textSize = 17f
                paint.typeface = Typeface.DEFAULT_BOLD
                paint.textAlign = Paint.Align.CENTER
                val labelY = if (y < topPad + 18f) y + 22f else y - 10f
                canvas.drawText(label, x, labelY, paint)
            }
            val day = dayLabels.getOrNull(index)
            if (!day.isNullOrBlank()) {
                paint.color = Color.parseColor("#888780")
                paint.textSize = 15f
                paint.typeface = Typeface.DEFAULT
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(day, x, height - 4f, paint)
            }
        }

        paint.color = Color.parseColor(lineColor)
        paint.textSize = 20f
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(callout, width - 10f, maxOf(22f, lastY - 10f), paint)
    }
}

private class DualTrendView(
    context: android.content.Context,
    private val latencyMinutes: List<Int>,
    private val awakeMinutes: List<Int>,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawLine(canvas, latencyMinutes.map { it.toFloat() }, Color.parseColor("#D85A30"), false)
        drawLine(canvas, awakeMinutes.map { it.toFloat() }, Color.parseColor("#7F77DD"), true)
        paint.style = Paint.Style.FILL
        paint.textSize = 22f
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.parseColor("#D85A30")
        canvas.drawText("入睡潜伏", 4f, height - 10f, paint)
        paint.color = Color.parseColor("#7F77DD")
        canvas.drawText("夜醒时长", width * 0.42f, height - 10f, paint)
    }

    private fun drawLine(canvas: Canvas, data: List<Float>, color: Int, dashed: Boolean) {
        if (data.size < 2) return
        paint.color = color
        paint.strokeWidth = 4f
        paint.style = Paint.Style.STROKE
        paint.pathEffect = if (dashed) android.graphics.DashPathEffect(floatArrayOf(8f, 6f), 0f) else null
        val top = 8f
        val bottom = 30f
        val maxValue = (data.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val xStep = (width - 18f) / (data.size - 1)
        val yScale = (height - top - bottom) / maxValue
        var lx = 8f
        var ly = top + data[0] * yScale
        for (i in 1 until data.size) {
            val x = 8f + i * xStep
            val y = top + data[i] * yScale
            canvas.drawLine(lx, ly, x, y, paint)
            lx = x
            ly = y
        }
        paint.pathEffect = null
    }
}

private class ProgressStripView(
    context: android.content.Context,
    private val progress: Float,
    private val fill: Int,
    private val track: Int,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = height / 2f
        paint.color = track
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, paint)
        paint.color = fill
        canvas.drawRoundRect(0f, 0f, width * progress.coerceIn(0f, 1f), height.toFloat(), radius, radius, paint)
    }
}

private class CircadianClockView(context: android.content.Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val r = size * 0.36f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * 0.055f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.parseColor("#2A3A4A")
        canvas.drawCircle(cx, cy, r, paint)
        paint.color = Color.parseColor("#378ADD")
        canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 70f, 82f, false, paint)
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#2A3A4A")
        canvas.drawCircle(cx, cy, r * 0.72f, paint)
        paint.style = Paint.Style.FILL
        paint.textSize = 24f
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.parseColor("#8899AA")
        val labels = listOf("0" to -90f, "3" to 0f, "6" to 45f, "12" to 90f, "18" to 180f, "21" to 225f)
        labels.forEach { (label, deg) ->
            val rad = Math.toRadians(deg.toDouble())
            val x = cx + cos(rad).toFloat() * r * 1.08f
            val y = cy + sin(rad).toFloat() * r * 1.08f + 8f
            paint.color = if (label == "3" || label == "6") Color.parseColor("#E8EDF5") else Color.parseColor("#8899AA")
            canvas.drawText(label, x, y, paint)
        }
        paint.color = Color.parseColor("#378ADD")
        paint.strokeWidth = 3f
        val bedX = cx + cos(Math.toRadians(18.0)).toFloat() * r * 0.88f
        val bedY = cy + sin(Math.toRadians(18.0)).toFloat() * r * 0.88f
        canvas.drawLine(cx, cy, bedX, bedY, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, 8f, paint)
        canvas.drawRoundRect(bedX - 24f, bedY - 16f, bedX + 24f, bedY + 16f, 16f, 16f, paint)
        paint.color = Color.WHITE
        paint.textSize = 20f
        canvas.drawText("床", bedX, bedY + 7f, paint)
    }
}

