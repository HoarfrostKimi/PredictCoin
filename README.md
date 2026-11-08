# 预测币计算器 · PredictCoin

给 B 站赛事预测算仓位的 Android 小工具。输入四个数（队名、B 站显示的比例、盘口胜率、当前余额），
它只回答一件事：**这一场该投多少币，还是别投**。

> B 站预测币是虚拟道具，不涉及真实货币。本项目不保证盈利，只是把「投入多少」这件事算清楚。

## 为什么不是教科书上的凯利公式

B 站这类玩法是**双边奖池（parimutuel）**：两边投进去的币合成一个池子，猜中的人按自己投入占该选项池的比例瓜分全部奖池。
也就是说，**每投入 1 币，猜中后能分回 `1/p` 币**（`p` = 该选项占全部奖池的比例）。

所以：

- 池比例 `p` **本身就是赔率**，唯一有正期望的条件是 `q > p`（`q` = 盘口给的胜率）。
- 固定赔率凯利 `f = (bp − q) / b` 在这里**是错的**：那套公式假设赔率不变，而这里你自己的下注会把赔率推向不利方向。
- 正确做法是直接对期望对数增长率 `E[ln W]` 求导，得到

```
f* = (q − p) / (1 − p)
```

## 模型链路

不是为了精确，而是为了**别上头**。每一层都在往下砍仓位：

```
1. 封口漂移   pEff = p + drift                    盘口比值通常在收池前还会变一点
2. 可信度收缩 qAdj = pEff + (q − pEff) × (0.35 + 0.65 × conf)
3. 理论凯利   fKelly = (qAdj − pEff) / (1 − pEff)
4. 折扣       f = fKelly × kellyFrac × 0.86^min(连败场次, 8)
5. 四道约束   单场上限、剩余敞口、硬性 50%、凯利值 —— 取最小
6. 建议区间   把漂移上下各浮动 0.5pp 再跑一遍，得到下界与上界
```

- `q ≤ p`（或收缩后优势归零）时直接返回 **不下注**，绝不硬凑一个金额。
- **总敞口**按「所有待定记录的投入之和」实时占用，打满之后新的场次一律判定不下注。
- 连败降档：最近连续猜错的场次每多一场，仓位 ×0.86。

## 功能

**计算页（一屏内完成）**
- 两队对战 / 冠军两种模式；冠军模式可切换押「夺冠」或「不夺冠」（p、q 同时取补）
- B 站比例、盘口胜率各一条滑块，点数字可弹窗精确输入（支持 45.3）
- 结果：建议投入区间、占资金比例、猜中净赚 / 猜错亏掉、期望收益、风险等级
- 无优势时显示「不下注」并给出一句话原因

**详情页**
- 盘口对比条（B 站比例 + 盘口位置标记）
- 优势分解：原始优势 → 封口漂移后 → 可信度收缩后
- 仓位阶梯：理论凯利 → × 分数凯利 → × 连败 → 约束后（含约束来源）
- 风险评分（仓位 / 优势大小 / 可信度 / 极端盘口 / 连败，满分 10）与文字原因
- 敏感性表格：池比例 ±6pp 时你的优势与期望
- 收益曲线：期望净收益与对数增长率双曲线，Canvas 手绘，标出峰值与建议区间
- 历史记录：默认显示最近 3 场，侧滑删除，点标签循环切换 待定 / 猜中 / 猜错
- 高级设置（默认折叠，避免误触）：可信度、单场上限、总敞口上限、漂移、凯利分数

**全部记录页**：所有历史场次的完整列表，同样支持侧滑删除与切换结果。

## 一些刻意的取舍

- **完全离线**：不申请任何权限，不联网，数据只存在本机 `SharedPreferences`（`org.json` 序列化）。
- **余额手动维护**：币还能在 B 站干别的事，所以记录输赢**不会**自动加减余额，余额以你在首页点右上角填的为准。
- **收益只给估算值**：封盘前赔率还会变，历史里显示的是「≈ 多少」，精确值以 B 站结算为准。
- **UI 全部手写**：没有用 Material 的 Card / Button / SegmentedButton / Slider 默认样式，卡片、分段控件、滑块、按钮都是自绘，配色与间距按 iOS 规范（#F2F2F7 背景、16dp 圆角卡片、等宽数字 `tnum`）。

## 构建

需要 JDK 17 与 Android SDK（compileSdk 37）。

```bash
./gradlew :app:testDebugUnitTest   # 引擎单元测试
./gradlew :app:assembleDebug       # 调试包
./gradlew :app:assembleRelease     # 发布包
```

发布签名从项目根目录的 `keystore.properties` 读取，**该文件和 `*.jks` 都没有进仓库**；
文件缺失时会自动回退到 debug 签名，所以克隆下来直接能构建。
自己打包的话在根目录新建 `keystore.properties`：

```properties
storeFile=predcoin-release.jks
storePassword=***
keyAlias=predcoin
keyPassword=***
```

安装包可以在 [Releases](https://github.com/HoarfrostKimi/PredictCoin/releases) 下载。

## 目录结构

```
app/src/main/java/com/zxy13/predcoin/
├── CalcEngine.kt              纯 Kotlin 计算引擎，无 Android 依赖（可单测）
├── CalcViewModel.kt           计算页状态（StateFlow）
├── MainActivity.kt            计算页
├── DetailActivity.kt          详情页 + 详情 ViewModel
├── HistoryActivity.kt         全部记录页
├── data/Store.kt              SharedPreferences + org.json 单例存储
└── ui/
    ├── AppColors.kt           配色
    ├── components/IOSComponents.kt   卡片 / 分段控件 / 滑块 / 按钮 / 侧滑删除
    └── components/MiniChart.kt       收益曲线（Canvas 手绘）
```

## 免责声明

仅用于个人研究虚拟币仓位管理，不构成任何投资建议；虚拟币无实际价值，请勿据此投入真实资金。
