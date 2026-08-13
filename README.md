# 网页版自习室

一个前后端分离的网页版自习室：开房间、专注计时、实时聊天、刷成就、组队专注、AI 学习助手，一应俱全。

## 功能一览

| 模块 | 说明 |
|---|---|
| 用户 | 注册 / 登录（JWT + BCrypt）、刷新令牌、公开名片 |
| 房间 | 创建 / 加入（支持私密密码）/ 退出 / 解散 / 编辑 / 踢人 / 转移 / 邀请好友 / 标签 / 公告 |
| 专注 | 开始 / 结束专注、同步专注、自动休息、连续轮次、AI 学习总结与复盘 |
| 组队 | 组队专注（2-6 人共享倒计时，到点自动结算）、团队徽章 |
| 实时 | WebSocket（STOMP）：聊天持久化、在线状态、专注状态、组队状态、@提及、已读回执 |
| 成就 | 等级 / 经验 / 连续天数 / 15 枚徽章 / 称号、赛季结算徽章（每周一）、华丽成就页（粒子/撒花/简洁模式） |
| 统计 | 累计 / 今日统计、每日目标、房间与全站排行榜（时长/次数/连续/最佳） |
| AI | 对话（Markdown + 公式渲染）、流式输出、会话记忆、学习计划、错题讲解与变式、笔记整理与抽卡、每日简报、周度报告、房间助教、资料问答（RAG 预切分） |
| 资料 | 上传 PDF / Word / 文本（自动切分入库，RAG 提问即问即答）、文档管理 |
| 效率 | 任务清单、笔记、错题本、闪卡复习、站内通知、好友动态推送 |
| PWA | 可安装到桌面（manifest + Service Worker） |

## 技术栈

- 后端：Java 21、Spring Boot 4.1、Spring Security + JWT（Nimbus）、Spring Data JPA、H2（文件模式）、Spring AI 2.0（DeepSeek）、WebSocket / STOMP
- 前端：Vite + React 19、React Router、STOMP.js、react-markdown + KaTeX

## 快速开始

### 方式一：一键启动（推荐）

双击根目录的 `start-dev.bat`，脚本会自动拉起后端（8081）和前端（5173），并打开浏览器。

### 方式二：手动

终端 1（后端，端口 8081）：

```powershell
mvn -B spring-boot:run
```

终端 2（前端，端口 5173）：

```powershell
cd client
npm install
npm run dev
```

浏览器访问 http://localhost:5173/ （前端通过 Vite 代理把 `/api`、`/ws` 转发到 8081）。

> 直接访问 http://localhost:8081/ 也可以看到页面，但那是打包产物，改动前端代码不会热更新；日常开发请用 5173。

## 配置

项目根目录的 `.env`（已被 git 忽略，不会提交）：

```bash
DEEPSEEK_API_KEY=sk-你的Key
JWT_SECRET=至少32位随机字符串
```

- 未设置 `JWT_SECRET` 或使用开发默认值时，**应用会拒绝启动**（安全保护）
- AI 相关功能需要有效的 DeepSeek Key

## 测试与构建

```powershell
# 后端全量测试（53 个）
mvn -B test

# 前端检查
cd client
npm run lint
npm run build

# 打生产包（自动构建最新前端并打进 jar，需本机有 Node 20+）
mvn -B package
```

打包产物：`target/study-room-0.1.0-SNAPSHOT.jar`，jar 自带前端，部署时一个包搞定。

## 部署

服务器部署的完整指南见 [deploy/README.md](deploy/README.md)，包含：
systemd 服务、Nginx 反代 + WebSocket、防火墙端口、HTTPS、数据迁移、验收清单。

## 安全说明

- AI 接口全部要求登录，`/api/chat` 旧别名已移除
- 限流：认证 20 次/分、AI 15 次/分、其余 300 次/分（按真实 IP，Nginx 需透传 `X-Real-IP`）
- 数据库 H2 文件存放在 `data/`（git 忽略），上线前务必迁移并定期备份