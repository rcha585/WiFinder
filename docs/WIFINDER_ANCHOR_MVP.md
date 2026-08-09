# WiFinder Anchor — MVP 1

WiFinder Anchor 是现有 WiFinder v1 之外的实验模式。v1 的 Building 302 地图、仿射标定和 Random Forest 定位路径保持不变；Anchor Mapping 建立独立的米制空间数据集，不用旧模型推断新房间的绝对位置。

## 当前实现边界

MVP 1 已实现以下真实数据路径：

- 浏览器根据 `sessionId + anchorId` 生成唯一高特征视觉标记。
- Android 用同一算法动态建立 ARCore `AugmentedImageDatabase`，从相机帧识别 Anchor A/B/C，并记录其世界位姿、追踪状态和时间。
- Mapping 阶段持续记录 ARCore Camera Pose、ARCore Depth hit-test（设备支持时）、ARCore 垂直 Plane 以及现有 `WifiScanner` 的原始扫描结果。
- Wi-Fi 结果绑定最近的 ARCore Pose，作为新的 spatial RF dataset 保存；旧 Building 302 Random Forest 不参与 Anchor Mapping 的坐标决策。
- `FloorPlanGenerator` 对墙点做异常点抑制、RANSAC 线拟合、近似共线合并、交点检测和严格闭合判定。
- 完成后展示可缩放 2D 预览，并写出 `mapping.json` 与按真实米制比例生成的 `floorplan.svg`。

没有实体 ARCore 设备接入本次开发环境，因此相机追踪、标记识别、Depth 质量和真实房间闭合仍需设备验证。软件不会为不支持 Depth 的设备生成假深度，也不会在墙段不闭合时伪造房间多边形。

## Architecture

```text
anchor-web/
  session URL + deterministic marker canvas

app/.../anchor/
  session/     session repository + shared marker pattern
  ar/          ARCore lifecycle, camera renderer, Augmented Image observations
  depth/       Depth/Plane hit-test sampling + vertical plane candidates
  wifi/        adapter over the existing WifiScanner/WifiViewModel
  spatial/     metric models, coordinate conversion, extension contracts
  floorplan/   line fitting, RANSAC, wall merge, corners, closure
  export/      mapping.json, floorplan.svg, Android file storage
  ui/          calibration, live mapping metrics, zoomable 2D preview
```

`AnchorMappingViewModel` 只负责工作流协调。AR 帧提取、Depth 采样、Wi-Fi 采集、持久化、重建和导出各自位于独立类中。`SpatialConstraintSource` 与 `PoseGraphCorrector` 是未来 correction/ranging/loop-closure 的空扩展契约；MVP 1 没有提供这些高级实现。

## Data flow

```text
Anchor Web marker
       ↓ ARCore Augmented Image
AnchorObservation ───────────┐
                            │
ARCore Camera → PoseSample ─┼→ AnchorSessionRepository
                            │             │
Depth/Plane → observations ─┤             ├→ mapping.json
                            │             │
WifiScanner → raw RF sample ┘             └→ FloorPlanGenerator → floorplan.svg + 2D preview
```

Mapping session 的主要字段：

- `anchors`: Anchor A/B/C 的 position、quaternion、timestamp、tracking state/method。
- `trajectory`: Camera Pose 时间序列。
- `depthObservations`: 世界坐标、来源相机 Pose、Depth/Plane 来源、关联 plane ID；ARCore hit-test 没有置信度时保存 `null`。
- `wifiSamples`: timestamp、pose、BSSID、RSSI、frequency。
- `planeWallCandidates`, `generatedWalls`, `generatedCorners`, `roomOutline`。

## Coordinate system

- 原始空间统一使用 ARCore world metres：`x` 向右、`y` 向上，`z` 由 ARCore 会话原点定义。
- Floor Plan 转换层为 `floor.x = world.x`、`floor.y = -world.z`。
- Height 不进入 2D 重建，但原始 `world.y` 完整保存在 JSON。
- SVG 使用 `100 units/metre`，并带 1 metre 比例尺；SVG 的 Y 轴仅在渲染导出层翻转。
- 本模式完全不读取 `CoordTransform`、Building 302 图片像素原点或旧仿射标定。

ARCore world origin 在每次新会话中是任意的。MVP 1 不把 Anchor A 强制变成坐标原点；后续 pose-graph/correction 实验可在导出的米制数据上完成这一层归一化。

## Hardware and compatibility assumptions

Android Scanner：

- Android 7.0 / API 24 或更高（项目 `minSdk 24`）。
- 后置相机、Google Play Services for AR / ARCore compatible device。
- 允许 `CAMERA`、`ACCESS_FINE_LOCATION`；Android 13+ 还需 `NEARBY_WIFI_DEVICES`。
- Wi-Fi 与系统 Location 开启。
- Depth 是可选能力。App 运行时通过 `Session.isDepthModeSupported(AUTOMATIC)` 检测；不支持时明确显示 `Depth unsupported on this device`，仍可保存 camera pose 与 ARCore plane。

Anchor 设备：

- 至少 3 台带现代浏览器和稳定屏幕的设备；不安装原生软件。
- iPad、Laptop、第二台手机或 Tablet 均可。
- 各设备必须能打开运行 Anchor Web 的电脑 LAN 地址。

ARCore Augmented Images 文档说明，提供实际图像宽度有助于更快获得 pose，因此首次测试必须测量每块屏幕上黑色正方形的实际边长，并在 Android 中输入该数值。相关参考：[Augmented Images](https://developers.google.com/ar/develop/java/augmented-images/guide)、[Depth API](https://developers.google.com/ar/develop/java/depth/developer-guide)。

## Run Anchor Web

需要 Node.js 18+。

```powershell
cd anchor-web
npm install
npm run dev
```

服务监听 `0.0.0.0:4173`，终端会输出 localhost 和 LAN URL。电脑与所有 Anchor 设备应位于同一局域网；如系统防火墙阻止访问，需要允许 Node.js 的本地网络入站连接。

Web 检查：

```powershell
npm run lint
npm test
npm run build
```

`npm run build` 生成可由任意静态服务器托管的 `anchor-web/dist/`。当前没有登录、云数据库或设备自动占位后台；Anchor 分配编码在 A/B/C/D 链接中。Create Session 后，将对应链接分别复制到各设备。

## Build and run Android Scanner

在 Android Studio 打开仓库根目录，选择 JDK 21，Sync Gradle，然后连接 ARCore compatible Android 手机并运行 `app`。

命令行：

```powershell
./gradlew.bat testDebugUnitTest assembleDebug lintDebug
```

本机若 `JAVA_HOME` 指向 JDK 24，请在当前终端临时改用 JDK 21；无需修改系统全局设置。Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

启动 App 后，在 v1 菜单中点击 `Anchor Mapping (Experimental)`。首次进入会请求 Camera、Location 和 Wi-Fi 权限，并可能提示安装/更新 Google Play Services for AR。

## First physical room test

1. 选择一个 3–6 m 见方、光线均匀、墙边少反光的房间。
2. 运行 Anchor Web，创建 session，例如 `7A4C91E2`。
3. 将 Anchor A、B、C 链接分别打开在三台设备上，调高亮度并进入 Full screen。
4. 把 A、B、C 放在三个不共线的位置：推荐 A 与 B 位于相邻墙的远端，C 位于第三面墙；彼此至少约 1.5 m。屏幕中心离地约 1.2–1.5 m，屏幕竖直、固定、完整无遮挡。不要把三块屏幕并排放在同一面墙。
5. 用尺测量每台设备黑色标记正方形的边长。首轮尽量调整显示缩放使三者宽度接近，并把实测值输入 Android；若三者差别明显，应分别测试或先让 Web 显示尺寸一致，因为 MVP 1 的 ARCore database 对本 session 使用一个共同宽度。
6. Android 进入 Anchor Mapping，输入相同 session ID 和标记宽度。
7. 依次对准 A、B、C。每次保持完整标记在画面中，并轻微左右移动手机，直到对应状态变为 `detected`。
8. 三个 Anchor 都检测后点击 `Start Mapping`。
9. 手机保持自然高度，缓慢沿房间边界走一圈。相机不要只看地面：以斜角扫过墙面和墙角，并持续移动以帮助 Depth-from-motion。避免快速转身和遮住镜头。
10. 回到起点附近并再次让至少一个 Anchor 进入画面，然后点击 `Finish Scan`。

## Expected result

Mapping 中应看到：

- `Tracking = TRACKING`；短暂遮挡时可能变为 `PAUSED`。
- Pose sample、时间、近似行走距离持续增加。
- 支持 Depth 的手机显示 `Depth API enabled`，Depth points 增加；不支持的手机显示明确的 unsupported 文案。
- Wi-Fi sample 会按 Android 系统允许的扫描节奏增加，通常慢于 AR Pose。

结束后应看到可缩放 2D Preview：橙色 trajectory、绿色 Anchors、深色 wall segments、紫色 corners，以及仅在几何确实闭合时出现的浅蓝 room outline。文件保存在 App external Documents 下的 `WiFinderAnchor/<session>-<timestamp>/`，也可通过 `Share mapping.json + floorplan.svg` 导出。

## Current limitations and known risks

- **未连接实体设备验证。** Debug build 与纯 JVM 算法测试通过不代表 ARCore camera/Depth 已在目标手机成功运行。
- 动态标记使用 ARCore Augmented Image 的高特征黑白图案，不是 AprilTag detector。屏幕摩尔纹、反光、浏览器缩放和较小屏幕都可能降低识别率。
- 一个 session 当前只有一个 marker width。不同屏幕的实际标记宽度差异会造成 Anchor pose scale bias。
- ARCore world tracking 会随步行累积 drift。MVP 1 保存 Anchor 重访数据，但没有 pose graph optimization 或 Anchor correction。
- Depth hit-test 提供真实命中点，但 API 不提供逐点 confidence，本字段因此为 `null`；白墙、远距离和静止手机可能得到稀疏或不稳定的深度。
- Floor Plan 是验证算法，不是 CAD。RANSAC 可能把家具边缘拟合成墙；不闭合时只输出墙段。
- Android Wi-Fi 扫描受到系统节流。MVP 复用 v1 的最短 8 秒扫描间隔与缓存回退，不能保证每 9 秒都有新结果。
- session 创建/分配是纯浏览器 URL 流程，没有 LAN 协调服务器；Android 通过相同 session ID 生成匹配 marker database。
- 没有 PNG preview；SVG 可直接在浏览器或矢量软件中打开。

## Next technical experiments

1. **视觉标记可靠性与尺度：** 在 iPad、Laptop、手机屏幕上测量首次识别距离、pose jitter 和宽度偏差，并比较不同亮度/尺寸。
2. **轨迹闭环漂移：** 走完一圈重访 A/B/C，量化 Anchor pose 前后差异，判断是否优先实现 Anchor correction / pose graph optimization。
3. **墙重建信息源：** 同一房间分别比较 ARCore vertical planes、Depth hit points 和两者融合的 precision/recall，调优 RANSAC 阈值与墙合并规则。

明确不在 MVP 1 中：云后台、账号、支付、LLM/Agent、家具/室内设计、声学测距、Wi-Fi RTT、UWB、Bluetooth ranging、CSI。
