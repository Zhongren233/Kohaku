# Kohaku

QQ 机器人开放平台的 Java 客户端 + Spring Boot 自动配置：引 starter + 两行配置即可连上网关，
收发单聊/群聊/频道消息并处理按钮回调。

| 模块 | 说明 |
| --- | --- |
| `kohaku-client` | 纯 Java 核心：网关长连接、OpenAPI 客户端、事件模型。只依赖 `jackson-databind` + `slf4j-api` |
| `kohaku-spring-boot-autoconfigure` | Spring 装配：属性绑定、生命周期、事件转发 |
| `kohaku-spring-boot-starter` | 业务方只引这一个 |
| `kohaku-example` | 可运行示例 |

## 环境要求

Java 21+、Spring Boot 4.x（Jackson 3）。

## 快速开始

```bash
./mvnw -DskipTests install
```

```xml
<dependency>
  <groupId>love.aira</groupId>
  <artifactId>kohaku-spring-boot-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```yaml
kohaku:
  qq:
    app-id: ${QQBOT_APP_ID}        # 也可用 KOHAKU_QQ_APPID / KOHAKU_QQ_APPSECRET 环境变量
    app-secret: ${QQBOT_APP_SECRET}
    intents:
      - PUBLIC_GUILD_MESSAGES      # 频道内 @机器人（基础权限）
      - GROUP_AND_C2C_EVENT        # 群/单聊事件（需在开放平台申请）
      - INTERACTION                # 按钮回调（需申请；检测到按钮回调却没订阅时启动即失败）
```

引入即连接。`QqMessageApi`、`QqMediaApi`、`QqChannelMessageApi`、`QqGatewayClient`、`QqOpenApiClient`、
`AccessTokenProvider`、`BotReplies` 均可直接注入（都带 `@ConditionalOnMissingBean`，可自行覆写）。

## 从 GitHub Packages 引入

GitHub Packages 的 Maven 仓库即便对公开仓库也要求带 token 访问，因此需要在本机 `~/.m2/settings.xml` 配好凭据
（token 只需 `read:packages` 权限）：

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>你的 GitHub 用户名</username>
      <password>你的 Personal Access Token</password>
    </server>
  </servers>
</settings>
```

项目 pom 中声明仓库（`id` 必须与上面的 `github` 一致）：

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/zhongren233/kohaku</url>
  </repository>
</repositories>
```

之后按快速开始的方式引入 `kohaku-spring-boot-starter` 即可（把版本号换成已发布的 release 版本）。

## 发布流程（维护者）

发布由 `.github/workflows/maven-publish.yml` 完成：**版本号取自 Release 的 tag**，`main` 上的 pom 始终保留
`-SNAPSHOT`，无需手动改。

1. 确认 `main` 已合并待发布内容，且本地 `./mvnw -B package` 通过（workflow 会在 deploy 时跑全量测试）。
2. 打 tag 并推送，tag 名就是版本号（允许带 `v` 前缀）：`git tag 0.1.0 && git push origin 0.1.0`。
3. 在 GitHub 上以该 tag 创建并 **发布** Release，工作流自动执行 `versions:set` + `deploy`。
   （仅仅是创建 draft 不会触发；触发条件是 `release: published`。）
4. 重新发布某版本：Actions → Maven Package → Run workflow，填入版本号即可。

注意事项：

- GitHub Packages 不允许覆盖同名同版本，要重发必须先删除该版本再执行，或换个版本号。
- `kohaku-example` 已设 `maven.deploy.skip`，只发布 `kohaku-parent`（pom）、`kohaku-client`、
  `kohaku-spring-boot-autoconfigure`、`kohaku-spring-boot-starter` 四个构件。
