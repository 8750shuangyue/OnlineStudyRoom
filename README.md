# 网页版自习室

一个前后端分离的网页版自习室：和同伴一起开房间、专注计时、实时聊天、刷成就、看数据、问 AI。

## 功能总览

| 模块 | 能力 |
|---|---|
| 用户 | 注册 / 登录（JWT）、BCrypt 密码加密 |
| 房间 | 创建 / 加入（支持私密密码）/ 退出 / 解散 / 改名 / 公告 / 标签 / 搜索 / 房主转移 / 踢人 |
| 专注 | 开始 / 结束计时、同步专注、番茄多轮 + 自动休息、任务绑定自动完成 |
| 统计 | 个人统计、每日目标进度环、房间 / 全站排行榜（今日/本周/本月/全部） |
| 实时 | WebSocket 房间聊天（持久化）、在线人数、成员专注状态 |
| PWA | 可安装到桌面（manifest + Service Worker，支持离线打开首页） |
| 激励 | 等级 / 经验 / 连续打卡 / 12 枚徽章 / 6 个称号（成就页自带华丽/简洁模式） |
| 看板 | 周报、14 天趋势图、90 天打卡热力图、时段效率分析、CSV 导出 |
| AI | 聊天（Markdown + 公式渲染）、番茄总结、错题讲解、学习计划、资料问答（RAG） |
| 社交 | 好友（请求 / 在线状态）、房间邀请 |
| 效率 | 任务清单、笔记、白噪音、浏览器通知、标签页倒计时 |

## 技术栈

- 后端：Java 21、Spring Boot 4.1、Spring Security + JWT（Nimbus）、Spring Data JPA、H2（文件模式）、Spring AI 2.0（DeepSeek）、WebSocket（STOMP）
- 前端：Vite + React 19、React Router、STOMP.js、react-markdown + KaTeX

## 快速开始

后端（终端 1）：

```powershell
mvn spring-boot:run
```

前端（终端 2）：

```powershell
cd client
npm.cmd run dev   # 注意：这台机器上 PowerShell 请用 npm.cmd
```

打开 http://localhost:5173（前端已配置代理，`/api` 与 WebSocket 自动转发到后端 8081）。

### 安装为桌面应用

生产构建（`npm.cmd run build`）后访问站点即可满足 PWA 安装条件（localhost 或 HTTPS），
导航栏会出现「安装 App」按钮；也可以直接用浏览器的「安装」菜单。Service Worker 会缓存应用壳，
断网时仍可打开应用（登录态与实时功能仍需网络）。

### 环境变量（项目根目录 `.env`，已被 .gitignore 忽略）

```properties
DEEPSEEK_API_KEY=sk-你的Key
# JWT_SECRET=生产环境建议设置至少 32 位随机字符串
```

DeepSeek Key 在 https://platform.deepseek.com/api_keys 创建。未设置时应用可以启动，但 AI 相关接口会报 401。

## 核心规则

- **单次专注 ≥ 15 分钟才算有效专注**：不足 15 分钟不计入统计、经验、成就、任务完成和排行榜；每次专注只计一次，不会按 15 分钟切分
- 数据保存在 `./data/studyroom.mv.db`，重启不丢
- 后端默认端口 **8081**（8080 常被本机其他服务占用）

## API 一览

| 模块 | 接口 |
|---|---|
| 认证 | `POST /api/auth/register` `POST /api/auth/login` `GET /api/auth/me` |
| 房间 | `GET/POST /api/rooms` `GET /api/rooms/categories` `GET /api/rooms/mine` `GET /api/rooms/{id}` `PUT/DELETE /api/rooms/{id}` `POST /{id}/join|leave|transfer|kick|invite` |
| 专注 | `POST /api/sessions/start|sync-start` `POST /api/sessions/{id}/stop` `GET /api/sessions|/active` `GET /api/rooms/{id}/focus-status` |
| 统计 | `GET /api/stats/me|trend|heatmap|weekly|time-analysis` |
| 排行 | `GET /api/leaderboard/global?period=` `GET /api/rooms/{id}/leaderboard?period=` |
| 任务 | `GET/POST /api/tasks` `PUT/DELETE /api/tasks/{id}` |
| 笔记 | `GET/POST /api/notes` `PUT/DELETE /api/notes/{id}` |
| 错题 | `GET/POST /api/mistakes` `PUT/DELETE /api/mistakes/{id}` `POST /api/ai/mistakes/{id}/explain` |
| 好友 | `GET /api/friends` `POST /api/friends/requests` `POST /api/friends/requests/{id}/accept|reject` `DELETE /api/friends/{username}` |
| 邀请 | `GET /api/invites` `POST /api/invites/{id}/accept|reject` |
| AI | `POST /api/chat` `POST /api/chat/stream` `POST /api/ai/sessions/{id}/summary` `POST /api/ai/rag` `POST /api/ai/study-plan` |
| 资料 | `POST /api/documents`（multipart）`GET/DELETE /api/documents` |
| 导出 | `GET /api/export/sessions.csv` |
| WebSocket | `ws://localhost:8081/ws?token=xxx`（CONNECT 头带 `roomId`），主题 `/topic/rooms/{id}/chat|presence|focus|sync` |

错误统一返回：`{"status": 状态码, "error": "原因短语", "message": "具体信息"}`。

## 项目结构

```
Study Room/
├── src/main/java/com/studyroom/
│   ├── auth/ user/           # 认证与用户
│   ├── room/ friend/         # 房间与好友
│   ├── study/ stats/         # 专注与统计
│   ├── gamification/         # 成就 / 等级 / 目标
│   ├── realtime/             # WebSocket
│   ├── task/ note/ mistake/  # 效率工具
│   ├── document/ ai/         # 资料与 AI
│   ├── export/ common/       # 导出 / 全局异常
│   └── security/             # JWT 安全
├── src/test/java/...         # 15 个测试类
└── client/                   # Vite + React 前端
```

## 测试

```powershell
mvn test        # 后端全部测试（使用内存数据库，不影响你的数据文件）
```

## 常见问题

- **npm 报执行策略错误**：用 `npm.cmd` 而不是 `npm`
- **8081 端口被占用 / 数据库文件被锁**：确认没有重复启动后端；测试与运行实例互不干扰（测试用内存库）
- **AI 返回 401**：检查 `.env` 里的 `DEEPSEEK_API_KEY`
- **新增字段后查询报列不存在**：重启后端，Hibernate 会自动补列（已对旧库做了兼容处理）

## 路线图

- [x] 认证、房间、专注、统计、实时、激励、看板、任务、氛围、笔记、RAG、错题、AI 计划、导出
- [ ] 收尾冲刺：统一异常（已完成）、测试补强、安全加固、RAG 升级、实时增强、PWA、部署预案
- [ ] 部署上线：Docker / HTTPS / 备份（暂缓，按需执行）
