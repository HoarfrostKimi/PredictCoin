package com.zxy13.predcoin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zxy13.predcoin.data.Record
import com.zxy13.predcoin.data.Store
import com.zxy13.predcoin.ui.AppColors
import com.zxy13.predcoin.ui.components.IOSCard
import com.zxy13.predcoin.ui.components.SwipeToDeleteRow
import com.zxy13.predcoin.ui.components.Tnum
import com.zxy13.predcoin.ui.theme.PredictCoinTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HistoryActivity : ComponentActivity() {

    private val vm: HistoryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            PredictCoinTheme {
                HistoryScreen(vm) { finish() }
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.fade_in, R.anim.slide_out_right)
    }
}

data class HistoryUiState(
    val records: List<Record> = emptyList()
)

class HistoryViewModel : ViewModel() {

    private val _state = MutableStateFlow(load())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    private fun load(): HistoryUiState = HistoryUiState(records = Store.records.toList())

    /** 待定 → 猜中 → 猜错 → 待定 */
    fun cycle(id: String) {
        Store.cycleRecordResult(id)
        _state.value = load()
    }

    /** 侧滑删除，已结算的盈亏会回滚到余额 */
    fun delete(id: String) {
        Store.removeRecord(id)
        _state.value = load()
    }
}

@Composable
private fun HistoryScreen(vm: HistoryViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = AppColors.bg) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            TopBar(title = "全部记录", onBack = onBack)
            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                IOSCard(contentPadding = 14.dp) {
                    val winCount = state.records.count { it.result == "win" }
                    val loseCount = state.records.count { it.result == "lose" }
                    val pendingCount = state.records.count { it.result == "pending" }

                    Text(
                        text = "共 ${state.records.size} 场",
                        fontSize = 15.sp,
                        color = AppColors.text,
                        style = Tnum
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "猜中 $winCount · 猜错 $loseCount · 待定 $pendingCount",
                        fontSize = 13.sp,
                        color = AppColors.textSecondary,
                        style = Tnum
                    )
                }

                Spacer(Modifier.height(12.dp))

                IOSCard {
                    if (state.records.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "还没有记录",
                                fontSize = 13.sp,
                                color = AppColors.textSecondary
                            )
                        }
                    } else {
                        state.records.forEachIndexed { index, record ->
                            if (index > 0) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(0.5.dp)
                                        .background(AppColors.separator)
                                )
                            }
                            SwipeToDeleteRow(onDelete = { vm.delete(record.id) }) {
                                RecordRow(record = record, onCycle = { vm.cycle(it) })
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "向左滑动某场可以删除 · 点右侧标签切换结果",
                    fontSize = 12.sp,
                    color = AppColors.textTertiary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "赚多少按记录时的池比例估算，实际以 B 站结算为准",
                    fontSize = 12.sp,
                    color = AppColors.textTertiary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "记录不会改动余额，币数请在首页点右上角手动填写",
                    fontSize = 12.sp,
                    color = AppColors.textTertiary
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
