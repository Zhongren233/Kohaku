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

## 依赖引入

### 从 Maven Central（推荐，无需任何配置）

```xml
<dependency>
  <groupId>love.aira</groupId>
  <artifactId>kohaku-spring-boot-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

### 从 GitHub Packages（需要 token）

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

发布由两个 workflow 完成，同一个 Release 会同时触发它们：

| workflow | 目标 |
| --- | --- |
| `maven-publish.yml` | GitHub Packages（`./mvnw deploy`） |
| `maven-central-publish.yml` | Maven Central（`./mvnw -Pcentral deploy`） |

**版本号取自 Release 的 tag**，`main` 上的 pom 始终保留 `-SNAPSHOT`，无需手动改；构建时 CI 用
`versions:set` 把版本改成 tag 名（允许带 `v` 前缀）。

1. 确认 `main` 已合并待发布内容，且本地 `./mvnw -B package` 通过（workflow 会在 deploy 时跑全量测试）。
2. 打 tag 并推送，tag 名就是版本号：`git tag 0.1.0 && git push origin 0.1.0`。
3. 在 GitHub 上以该 tag 创建并 **发布** Release。仅仅是创建 draft 不会触发；触发条件是 `release: published`。
4. 重新发布某版本：Actions → 对应 workflow → Run workflow，填入版本号即可。

### 一次性准备

- Central Portal 账号：为 groupId `love.aira` 完成 namespace 验证，然后在
  <https://central.sonatype.com> 生成 User Token。
- 仓库 secrets：`CENTRAL_USERNAME`、`CENTRAL_TOKEN`（Portal User Token）、`GPG_PRIVATE_KEY`、
  `GPG_PASSPHRASE`（签名私钥与其口令，均已配置）。
- 签名公钥需在 keyserver 上，指纹 `AC6896136221AE9BB32EF4B8A53FD383CD3EE088`
  （<https://keyserver.ubuntu.com/pks/lookup?search=0xAC6896136221AE9BB32EF4B8A53FD383CD3EE088&op=index>）。

### 注意事项

- **Central 的版本不可撤回、不可覆盖**，发错了只能发新版本；GitHub Packages 可删版本后重发。
- Central 要求每个 jar 都带 `-sources.jar` 与 `-javadoc.jar`，且所有文件都有 `.asc` 签名与 MD5/SHA1 校验和，
  这些都由 `central` profile 自动完成。`kohaku-spring-boot-starter` 没有源码，其 javadoc jar 是只含
  `README.md` 的占位包（Sonatype 允许的做法）。
- GitHub Packages 不允许覆盖同名同版本，要重发必须先删除该版本再执行，或换个版本号。
- `kohaku-example` 不发布：GitHub Packages 走 `maven.deploy.skip`，Central 走 `-pl '!:kohaku-example'`
  （该插件不识别 `maven.deploy.skip`）。

## 许可证

[Apache License 2.0](LICENSE)。
