# C17 常驻资源服务

这是附属模块资源缓存的原生源代码。当前附属模块2.2/code13还包含独立的[系统UI防炸](../boot-rescue/README.md)，两者互不依赖。主 App 的 `preBuild` 会构建四种 ABI，打入附属模块资源；不提交生成的二进制。

构建需要 Python 3 与 Android NDK `29.0.14206865`。SDK 默认取 `C17_SDK`、`ANDROID_HOME` 或仓库 `.local-toolchain/sdk`；也可用 `C17_NDK` 指定 NDK。执行 `python tools/performance-cache/build.py`，或用 `--abi arm64-v8a` 只构建一种架构。输出位于 `build/generated/c17-performance-assets/performance-module/bin/`。

服务仅从已安装的 `c17_performance/bin/` 启动。入口为 `cache-service.sh start|stop|status|purge`；读取状态不会安装或创建服务。停用／卸载标记通过 inotify 停止服务；闲时阻塞等待，无轮询定时器。意外退出后，下次开机、启用或优化事件重新启动，不使用无限拉起循环。

协议 `C17C1` 经抽象 Unix socket 传递：调用方校验 Root 对端；服务以 SO_PEERCRED 的 UID 和实际进程名对照 Root 生成的允许列表。GET 返回只读共享内存 FD、大小和 CRC。PUT 的同一 UID、同一版本键不可改写已有内容。Java 消费者 API 33 起在资源线程 mmap 读取，并保留原资源回退；主线程不连接。插件资源还核对清单 SHA-256。

预算：常驻有效资源至多 64 MiB / 256 项；单项至多 32 MiB；持久化至多 256 MiB / 512 项。请求准备、恢复旧项和序列化期间另有有界临时空间，64 MiB 不是整个进程 RSS 上限。Java 编码资源缓存另限 8 MiB。客户端接收后立即释放映射和 FD，不缓存完整动态画面。

进程自身设置 `oom_score_adj=-600`，不修改 SystemUI、全局 LMKD 或原厂调度组；仅父级已启用 memory 控制器时建立自有 cgroup，尝试 `memory.low=16 MiB`。保护是尽力而为；失败或内核不支持如实显示。未使用 mlock、memory.min、常驻满频或温控改写。

本地验证：`validation/companion-cache-client-check.py` 检查真实 Java 客户端的协议、线程限制、回退和资源释放；`validation/performance-cache-service-check.py` 检查真实脚本生命周期；`validation/performance-transport-check.py` 复现 ART 输出 FD 拒绝。Android 原生二进制另提供 `--self-test`，需在后续授权的设备会话中执行。桌面替身测试不证明 Android socket、只读映射、SELinux 或内核保护已经实机生效。
