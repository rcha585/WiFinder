# WiFinder Anchor - MVP 1

WiFinder Anchor 是现有 WiFinder v1 之外的实验模式。v1 的 Building 302 地图、仿射标定和 Random Forest 定位路径保持不变；Anchor Mapping 建立独立的米制空间数据集，不用旧模型推断新房间的绝对位置。

## 当前实现

- Anchor Web 根据 `sessionId + anchorId` 生成唯一高特征黑白视觉标记。
- Android 动态建立视觉标记数据库，识别 Anchor A/B/C，并记录世界位姿、追踪状态和时间。
- 华为手机优先使用 Huawei AR Engine；其他 Android 设备回退到 Google ARCore。
- Mapping 阶段记录 AR camera pose、可用的 Depth/Plane hit-test、竖直 plane，以及现有 `WifiScanner` 的原始扫描结果。
- 完成后展示可缩放 2D 预览，并写出 `mapping.json` 与按米制比例生成的 `floorplan.svg`。

Huawei AR Engine 路径目前记录 camera pose、image anchors、plane hit-test 和竖直 plane；Depth 点先作为 Google ARCore 支持设备上的可选能力，不会伪造深度数据。

## 默认设备预设

首轮测试不要求用尺量。系统默认按这三种设备预设填入 Android：

- Anchor A: 14 inch laptop Chrome, marker width `14.0 cm`
- Anchor B: iPhone 14 Pro Safari, marker width `5.4 cm`
- Anchor C: 13 inch iPad Pro Safari, marker width `16.0 cm`

这些是实验预设，不是物理真值。浏览器工具栏、系统缩放、是否全屏、页面缩放都会影响真实显示尺寸。Anchor Web 会在页面底部显示 preset width 和 rendered estimate；如果两者差很多，以页面显示值或实际测量值为准。

## Run Anchor Web

```powershell
cd anchor-web
npm install
npm run dev
```

服务监听 `0.0.0.0:4173`，终端会输出 localhost 和 LAN URL。电脑与所有 Anchor 设备应位于同一局域网。Create Session 后，复制 A/B/C 链接到三台设备；链接会自动带上默认 `preset` 参数。

Web 检查：

```powershell
npm test
npm run build
```

## Build and run Android

建议用 JDK 21：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
./gradlew.bat testDebugUnitTest assembleDebug lintDebug
```

Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

启动 App 后，在 v1 菜单点击 `Anchor Mapping (Experimental)`。华为手机会先尝试 Huawei AR Engine，不会主动请求安装 Google Play Services for AR；非华为 Android 设备仍使用 Google ARCore 路径。

## First physical room test

1. 运行 Anchor Web，创建 session。
2. Anchor A 放 14 寸电脑，Anchor B 放 iPhone 14 Pro，Anchor C 放 13 寸 iPad Pro。
3. 三个屏幕固定、亮度调高、标记完整可见，尽量分布在不共线的位置。
4. Android 进入 Anchor Mapping，输入同一个 session ID；默认 A/B/C 尺寸先不用改。
5. 依次对准 A/B/C，直到状态变成 `detected`。
6. 点击 `Start Mapping`，沿房间边界缓慢走一圈，镜头斜扫墙面和墙角。
7. 回到起点附近，让至少一个 Anchor 再次进入画面，然后点击 `Finish Scan`。

## Limitations

- 动态标记不是 AprilTag detector；屏幕摩尔纹、反光、浏览器缩放和过小尺寸都可能降低识别率。
- 预设尺寸会带来尺度误差；要做正式定量实验时仍建议测量每块屏幕上的实际 marker 边长。
- AR world tracking 会随步行累积 drift；MVP 1 保存 Anchor 重访数据，但没有 pose graph optimization。
- Floor Plan 是验证算法，不是 CAD。白墙、远距离、家具边缘和稀疏 plane 都可能影响墙线拟合。
- Android Wi-Fi 扫描受系统节流影响，MVP 复用 v1 的扫描间隔与缓存回退。
