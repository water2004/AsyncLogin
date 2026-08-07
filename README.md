# AsyncLogin

[English](#english) | 简体中文

AsyncLogin 是一个服务端 Fabric 模组，将玩家登录/退出过程中涉及的玩家 DAT 和
`usercache.json` 文件 IO 移出 Minecraft 服务端 tick 线程，避免慢磁盘或数据修复
阻塞游戏刻。

## 支持版本

| Minecraft | Fabric Loader | Java | 模组版本 |
| --- | --- | --- | --- |
| 26.1 | 0.17.3+ | 25+ | `1.0.0+mc26.1` |
| 26.2 | 0.17.3+ | 25+ | `1.0.0+mc26.2` |

两个 Minecraft 版本拥有完全独立的 Mixin 和适配层。请只安装与服务端版本匹配的
JAR；本项目不依赖 Fabric API。

## 工作方式

所有相关文件操作共用一条无界、单工作线程 FIFO 队列，线程名为
`AsyncLogin IO Thread`：

1. 准备出生点时的第一次玩家 DAT 读取；
2. 玩家进入世界前的第二次玩家 DAT 读取；
3. 原版发起的每一次 `usercache.json` 保存；
4. 玩家 DAT 保存中的临时文件创建、压缩写入与安全替换。

服务端主线程只提交事务并在后续 tick 轮询已经完成的 Future，不会调用
`Future.join()`、`Future.get()`、阻塞式 `put()`、`CallerRuns` 或同步回退。
队列有序地处理读取和保存，因此玩家退出后立即重连时，新的读取也会排在之前的
保存之后。

玩家实体到 NBT 的快照仍在主线程生成。直接在后台读取一个仍会变化的 `Player`
会产生数据竞争；真正的磁盘 IO 才会进入后台队列。

## 原版兼容性

AsyncLogin 保留 Mojang 原版的登录状态机和调用次数：

- 两次 DAT 读取保持为两个独立事务，不复用、不合并；
- 损坏文件备份、`.dat_old` 回退和 DataFixer 路径不变；
- NBT 与 usercache JSON 格式不变；
- 原版每请求一次 usercache 保存，队列就执行一次保存，不合并写入；
- 读取失败会回到原版对应的配置错误或无效玩家数据处理路径；
- 正常停服时停止接收事务，排空队列后再关闭 Minecraft 存储。

异步实现无法让文件系统可见时间或进程崩溃前尚未排空的数据与同步写入完全一致。
除此之外，目标是只改变文件 IO 的执行线程。

## 安装

1. 安装对应 Minecraft 版本的 Fabric Loader。
2. 下载匹配版本的 AsyncLogin JAR。
3. 将 JAR 放入服务端的 `mods` 目录并启动服务端。

这是纯服务端模组，客户端不需要安装。

## 构建

需要 Java 25。Gradle Toolchain 可以自动准备所需 JDK。

```powershell
.\gradlew.bat clean build
```

构建产物位于：

- `minecraft-26.1/build/libs/AsyncLogin-1.0.0+mc26.1.jar`
- `minecraft-26.2/build/libs/AsyncLogin-1.0.0+mc26.2.jar`

## 项目结构

```text
AsyncLogin/
├─ core/             # 不引用 Minecraft API 的单线程 IO 队列
├─ minecraft-26.1/   # Minecraft 26.1 独立适配层与 Mixins
└─ minecraft-26.2/   # Minecraft 26.2 独立适配层与 Mixins
```

## 许可证

[MIT](LICENSE.txt)

---

## English

AsyncLogin is a server-side Fabric mod that moves player DAT and
`usercache.json` file IO off Minecraft's server tick thread.

Minecraft 26.1 and 26.2 are built as separate artifacts with independent
Mixins and adapters. Both use the same embedded, Minecraft-independent,
unbounded single-worker FIFO core. Vanilla's two DAT reads remain separate,
every requested usercache save remains a separate transaction, and normal
shutdown drains the queue before Minecraft closes its storage.

Player-to-NBT snapshotting remains on the main thread to avoid racing live
entity state; only the filesystem portion of DAT saves runs on the IO worker.
See the sections above for the complete compatibility and build details.
