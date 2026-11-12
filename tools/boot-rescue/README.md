# 系统UI防炸监听器

`c17bootrescue` 是附属模块的独立原生监听器，不修改 `c17cached` 协议。

- 仅 `sys.boot_completed != 1` 且 `init.svc.bootanim=running` 时处理输入。启动后最多等动画出现30秒；已出现的动画结束、开机完成、模块停用或关闭恢复开关后退出。总运行上限10分钟。
- 指令为短按音量上键5次：从第一次按下到第五次松开不超过5秒，第五次松开时触发。中途按下电源、音量下或其他任何键（包括两个音量键同时按）即作废，需重新连按。只用音量上，是为了避开系统开机期间自己响应的按键：电源键（熄屏、关机菜单）、按住音量下（Android 进入安全模式）、两个音量键同时长按（无障碍快捷方式）。物理键使用Linux键码115，另外跟踪116/114只为作废，不抢占输入。代价是音量会调高5格。（附属模块2.3/code14起；此前为电源一次 → 音量上三次 → 音量下四次。）
- 每次按下必须释放；忽略自动连发、重复down和多个设备镜像，按住不放只算一次。同键跨设备40毫秒内视为镜像，异键同时按下、其他键、超时、时钟倒退或输入丢帧会清空当前计数。事件使用内核单调时钟，跨FD按时间排序。
- 同一进程只触发一次，同一启动通过root专用标记原子限制一次。脚本、配置、授权标记和单实例锁均为固定路径，不接受调用者传入事件、路径或清理命令。
- 成功后仅执行预先打开且验证过的 `boot-rescue.sh` 文件描述符。SystemUI数据处理由该脚本负责，监听器本身没有删除任何应用数据的实现。

固定路径：

```text
/data/adb/modules/c17_performance/boot-rescue.sh
/data/adb/c17-performance/boot-rescue-disabled
/data/adb/c17-performance/boot-rescue-watch.lock
/data/adb/c17-performance/boot-rescue-watch.pid
/data/adb/c17-performance/boot-rescue.<当前boot UUID>
```

授权标记内容为 `authorized\n<boot UUID>\n`，root0600；进程记录为 `<PID>\n<boot UUID>\n`，退出时核对记录归属后移除。`/data` 允许Android原生system或root所有者；信任边界 `/data/adb` 及其后目录必须root所有且不允许组或其他人写，所有被跟随组件拒绝符号链接。

构建与测试：

```powershell
python tools/boot-rescue/build.py
python tools/boot-rescue/test.py
```

使用固定NDK29，Android API26，四ABI、PIE、16KB ELF段对齐，输出到已有附属模块生成资产目录。测试把与Android完全同一份 `Sequence` 与探测能力汇总代码编译成WebAssembly，通过Node执行657项行为检查；不连接设备、不创建启动标记、不运行清理后端。

生产CLI仅 `--watch`（也为默认）、`--probe` 与 `--self-test`。后两项不会创建状态目录、锁或授权标记，也不会执行恢复脚本；`--probe` 不读取按键事件，只对新开的只读evdev FD检查能力和单调时钟契约。它报告 `boot_window`、`input_devices`、`usable_clock_devices`、`key_coverage`、`disabled`。coverage只汇总接受单调时钟的节点，power/up/down分别为1/2/4，完整覆盖为7；其它键不贡献能力。现在的指令只需要音量上（含2即可）。其它参数拒绝。

测试无法代替具体ROM的开机按键驱动、Root管理器早期启动时序与SELinux权限验证。输入节点不支持单调时钟或不可读时不会降低到猜测输入或强制触发。
