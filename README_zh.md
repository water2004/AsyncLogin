# AsyncLogin

[English](README.md) | 简体中文

[![CI and Release](https://github.com/water2004/AsyncLogin/actions/workflows/ci-release.yml/badge.svg)](https://github.com/water2004/AsyncLogin/actions/workflows/ci-release.yml)

AsyncLogin 是一个服务端 Fabric 模组，将玩家登录/退出过程中涉及的玩家 DAT、
统计、进度和 `usercache.json` 文件 IO 移出 Minecraft 服务端 tick 线程，避免
慢磁盘、JSON 解析或数据修复阻塞游戏刻。

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

1. 准备出生点时的第一次玩家 DAT 读取、解压、解析与 DataFix；
2. 玩家通过重复登录、封禁、白名单和容量检查后，按原版顺序读取统计 JSON、
   进度 JSON 和第二次玩家 DAT；统计旧用户名文件迁移、两种 JSON 的解析与
   DataFix 也在队列中完成；
3. 原版发起的每一次 `usercache.json` 保存；
4. 玩家 DAT 保存中的临时文件创建、压缩写入与安全替换；
5. 玩家统计和进度 JSON 的目录创建与写入。

服务端主线程只提交事务并在后续 tick 轮询已经完成的 Future，不会调用
`Future.join()`、`Future.get()`、阻塞式 `put()`、`CallerRuns` 或同步回退。
队列有序地处理读取和保存，因此玩家退出后立即重连时，新的读取也会排在之前的
保存之后。

玩家实体到 NBT 的快照仍在主线程生成。直接在后台读取一个仍会变化的 `Player`
会产生数据竞争；统计和进度的保存快照同样在主线程生成，之后才把不可变的 JSON
树交给 IO 队列。`usercache.json` 也在原版调用时生成完整字符串，后台只负责打开、
写入和关闭文件，因此排队期间发生的新缓存更新不会改写较早事务的内容。

Mojang 的出生区域实体文件本来就由其专用存储执行器异步加载，但原版在真正生成
玩家前会通过 `waitForEntities` 阻塞服务端线程等待结果。AsyncLogin 保留原执行器，
改为每 tick 接收已完成的加载并检查 7×7 出生区块；实体尚未就绪时只延后玩家进入，
不阻塞该 tick，也不把世界实体 IO 塞进玩家文件 FIFO。

## 原版兼容性

AsyncLogin 保留 Mojang 原版的状态转换、操作顺序和文件格式：

- 两次 DAT 读取保持为两个独立事务，不复用、不合并；
- 损坏文件备份、`.dat_old` 回退和玩家 DataFix 路径不变；
- 统计旧用户名文件迁移、统计/进度 DataFix、进度应用顺序和错误处理不变；
- NBT、统计、进度与 usercache JSON 格式不变；
- 原版每请求一次保存，都会在调用时生成对应快照并创建独立 FIFO 任务，不合并；
- 读取失败会在主线程回到原版对应的配置错误或无效玩家数据处理路径；
- 异步等待期间保留同 UUID 登录占位，避免绕过原版重复登录检查；
- 正常停服时停止接收事务，排空队列后再关闭 Minecraft 存储。

异步写入无法让文件系统可见时间或进程崩溃前尚未排空的数据与同步写入完全一致。
除此之外，目标是只改变文件工作的执行线程。

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

## 自动构建与发布

`main` 的 push、面向 `main` 的 Pull Request 以及手动运行都会执行 Java 25
全量构建和 core 测试。构建生成的两个非 sources JAR 会作为 GitHub Actions
Artifact 保留 14 天。

发布正式版本时，在需要发布的 `main` 提交上创建并推送对应 tag：

```powershell
git tag "v1.0.0+mc26.1"
git push origin "v1.0.0+mc26.1"
```

或：

```powershell
git tag "v1.0.0+mc26.2"
git push origin "v1.0.0+mc26.2"
```

CI 会验证 tag 格式、tag 是否属于 `main`、tag 版本是否与对应模组元数据一致，
随后创建 GitHub Release。每个 Release 只上传与 tag 中 Minecraft 版本匹配的正式
JAR，发布说明由 [`RELEASE.md`](RELEASE.md) 生成。

## 项目结构

```text
AsyncLogin/
├─ core/             # 不引用 Minecraft API 的单线程 IO 队列
├─ minecraft-26.1/   # Minecraft 26.1 独立适配层与 Mixins
└─ minecraft-26.2/   # Minecraft 26.2 独立适配层与 Mixins
```

## 许可证

[MIT](LICENSE.txt)
