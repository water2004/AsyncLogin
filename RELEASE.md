# AsyncLogin {{VERSION}}

适用于 **Minecraft {{MINECRAFT_VERSION}}** 的服务端 Fabric 正式版本。

## 本版内容

- 将登录阶段两次独立的玩家 DAT 读取移出服务端 tick 线程。
- 将每一次原版 `usercache.json` 保存放入同一条单线程 FIFO IO 队列。
- 将玩家 DAT 的压缩写入与安全文件替换移出服务端 tick 线程。
- 保留原版损坏文件备份、`.dat_old` 回退、DataFixer 和错误处理路径。
- 正常停服时排空 IO 队列，确保已提交的保存完成。

## 安装

1. 使用 Minecraft {{MINECRAFT_VERSION}}、Java 25 和 Fabric Loader 0.17.3 或更高版本。
2. 下载本 Release 中的 `{{ARTIFACT_NAME}}`。
3. 将 JAR 放入服务端 `mods` 目录；客户端不需要安装。

> 请勿将不同 Minecraft 版本的 AsyncLogin JAR 混用。
