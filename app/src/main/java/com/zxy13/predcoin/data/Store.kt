package com.zxy13.predcoin.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class Record(
    val id: String,
    val date: String,
    val match: String,
    val opt: String,
    val q: Double,
    val p: Double,
    val stake: Int,
    val result: String           // "pending" / "win" / "lose"
    // 不记录实际返还：封盘前赔率还会变，赚多少只能事后按 B 站结算为准
)

/**
 * 全局单例存储：SharedPreferences + org.json，两个 Activity 共享同一份内存状态。
 * 首次访问前需调用 [init]（MainActivity.onCreate 中调用）。
 */
object Store {

    private const val PREFS_NAME = "predcoin_store"
    private const val KEY_STATE = "state"

    private var prefs: SharedPreferences? = null

    var balance: Double = 1100.0
    var p: Double = 0.45
    var q: Double = 0.52
    var optA: String = "EDG"
    var optB: String = "BLG"
    var solo: Boolean = false
    /** 冠军模式下是否押「不夺冠」：true 时 p / q 存的是补集 */
    var soloOpposite: Boolean = false

    var conf: Double = 0.6
    var maxFrac: Double = 0.05
    var expCap: Double = 0.20
    var drift: Double = 0.02
    var kellyFrac: Double = 0.5

    val records: MutableList<Record> = mutableListOf()

    /** 已占用敞口 = 所有待定记录的投入之和；结算或删除后自动释放 */
    val expUsed: Double
        get() = records.sumOf { if (it.result == "pending") it.stake.toDouble() else 0.0 }

    /** 待定场数 */
    val pendingCount: Int
        get() = records.count { it.result == "pending" }

    /** 敞口上限对应的币数 */
    val expLimit: Double
        get() = balance * expCap

    /**
     * 待定 → 猜中 → 猜错 → 待定。
     * 只改标记，不动余额：币还能在 B 站干别的事，余额以用户在首页手动填的为准。
     */
    fun cycleRecordResult(id: String) {
        val index = records.indexOfFirst { it.id == id }
        if (index < 0) return
        val record = records[index]
        val next = when (record.result) {
            "pending" -> "win"
            "win" -> "lose"
            else -> "pending"
        }
        records[index] = record.copy(result = next)
        save()
    }

    /** 删除一条记录（不影响余额） */
    fun removeRecord(id: String) {
        val index = records.indexOfFirst { it.id == id }
        if (index < 0) return
        records.removeAt(index)
        save()
    }

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        load()
    }

    fun save() {
        val sp = prefs ?: return
        val o = JSONObject()
        o.put("balance", balance)
        o.put("p", p)
        o.put("q", q)
        o.put("optA", optA)
        o.put("optB", optB)
        o.put("solo", solo)
        o.put("soloOpposite", soloOpposite)
        o.put("conf", conf)
        o.put("maxFrac", maxFrac)
        o.put("expCap", expCap)
        o.put("drift", drift)
        o.put("kellyFrac", kellyFrac)

        val arr = JSONArray()
        records.forEach { r ->
            arr.put(
                JSONObject()
                    .put("id", r.id)
                    .put("date", r.date)
                    .put("match", r.match)
                    .put("opt", r.opt)
                    .put("q", r.q)
                    .put("p", r.p)
                    .put("stake", r.stake)
                    .put("result", r.result)
            )
        }
        o.put("records", arr)

        sp.edit().putString(KEY_STATE, o.toString()).apply()
    }

    fun load() {
        val sp = prefs ?: return
        val raw = sp.getString(KEY_STATE, null) ?: return
        try {
            val o = JSONObject(raw)
            balance = o.optDouble("balance", balance)
            p = o.optDouble("p", p)
            q = o.optDouble("q", q)
            optA = o.optString("optA", optA)
            optB = o.optString("optB", optB)
            solo = o.optBoolean("solo", solo)
            soloOpposite = o.optBoolean("soloOpposite", soloOpposite)
            conf = o.optDouble("conf", conf)
            maxFrac = o.optDouble("maxFrac", maxFrac)
            expCap = o.optDouble("expCap", expCap)
            drift = o.optDouble("drift", drift)
            kellyFrac = o.optDouble("kellyFrac", kellyFrac)

            records.clear()
            val arr = o.optJSONArray("records") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val r = arr.optJSONObject(i) ?: continue
                records.add(
                    Record(
                        id = r.optString("id"),
                        date = r.optString("date"),
                        match = r.optString("match"),
                        opt = r.optString("opt"),
                        q = r.optDouble("q", 0.0),
                        p = r.optDouble("p", 0.0),
                        stake = r.optInt("stake", 0),
                        result = r.optString("result", "pending")
                    )
                )
            }
        } catch (_: Exception) {
            // 数据损坏时保持默认值，不崩溃
        }
    }
}
