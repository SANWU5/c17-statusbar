# 开发约定

- 说明、界面与用户文档优先使用简体中文，代码标识保持项目命名。
- 当前正式版2.1.0/code293；版本身份在 `app/build.gradle` 与 `ModuleRuntimeStatus.BUILD_TOKEN` 同步维护。
- 使用JDK17、SDK37.0和Build Tools36.0.0，构建见 `docs/WINDOWS-BUILD.zh-CN.md`。
- 修改后执行相关检查；发布执行 `tools/dev/build.ps1` 和完整 `tools/dev/checks.ps1`，结果如实写入 `VALIDATION.md`。桌面检查不替代真机视觉验收。
- Java hooks保持major52，Compose代码按项目JVM11目标构建。保留反射入口名称与第三方版权头。
- 私钥、凭据、设备资料、本地备份、构建缓存和用户自备图包不提交。公开源码内置原创C17示例库，第三方图包由用户自行导入。
- 不因版本号增加而无条件改配置schema；保持已有设置、资源内容ID及取消/失败后的恢复行为。
