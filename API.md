# API 文档

短链系统全部 HTTP 接口。除标注「公开」外均需登录：请求头携带 `shortLinkToken: {tokenValue}`
（登录接口返回）。`/api/admin/**` 额外要求 ADMIN 角色。
所有响应统一为 `Result` 包装：`{"code": 0, "message": "成功", "data": ...}`，非 0 为业务错误码。
雪花 ID（用户/分组/域名）均以字符串返回，前端按字符串传递，防止 JS 精度丢失。

## 一、认证

| 方法 | 路径 | 说明 | 鉴权 |
| ---- | ---- | ---- | ---- |
| POST | `/api/auth/register` | 注册 | 公开 |
| POST | `/api/auth/login` | 登录，返回 token | 公开 |
| POST | `/api/auth/logout` | 退出 | 登录 |
| GET | `/api/auth/me` | 当前用户信息 | 登录 |

## 二、短链管理

| 方法 | 路径 | 说明 | 鉴权 |
| ---- | ---- | ---- | ---- |
| POST | `/api/short-links` | 创建短链（可指定分组、域名、有效期、描述 title） | 登录 |
| GET | `/api/short-links?groupId=&pageNo=&pageSize=` | 我的短链分页（可按分组过滤，0=未分组；含今日/累计 PV·UV 实时统计） | 登录 |
| GET | `/api/short-links/{code}` | 详情（含实时统计） | 属主/管理员 |
| PUT | `/api/short-links/{code}` | 编辑短链（目标链接/标题/分组，缓存失效秒级生效） | 属主/管理员 |
| PUT | `/api/short-links/{code}/group` | 移动到分组（0=未分组） | 属主/管理员 |
| PUT | `/api/short-links/{code}/status` | 上线/下线（缓存失效，秒级生效） | 属主/管理员 |
| DELETE | `/api/short-links/{code}` | 删除（移入回收站，默认保留 30 天） | 属主/管理员 |

## 三、统计

| 方法 | 路径 | 说明 | 鉴权 |
| ---- | ---- | ---- | ---- |
| GET | `/api/stats/{code}` | 实时统计：当日 PV/UV、累计 PV、累计 UV | 属主/管理员 |
| GET | `/api/stats/{code}/history?days=7` | 按日归档历史统计（PV/UV） | 属主/管理员 |

累计 UV = 归档表 `stat_date < 今日` 的 SUM(uv) + 当日 HyperLogLog 计数（归档按天覆盖写，今日行不完整故不计入）。

## 四、分组

| 方法 | 路径 | 说明 | 鉴权 |
| ---- | ---- | ---- | ---- |
| POST | `/api/groups` | 创建分组 | 登录 |
| GET | `/api/groups` | 分组列表（含各分组短链数） | 登录 |
| PUT | `/api/groups/{id}` | 重命名分组 | 登录 |
| DELETE | `/api/groups/{id}` | 删除分组（组内短链移回未分组） | 登录 |

## 五、回收站

| 方法 | 路径 | 说明 | 鉴权 |
| ---- | ---- | ---- | ---- |
| GET | `/api/recycle-bin` | 回收站分页（含自动清除时间） | 登录 |
| PUT | `/api/recycle-bin/{code}/restore` | 从回收站还原 | 属主/管理员 |
| DELETE | `/api/recycle-bin/{code}` | 彻底删除（物理删除，不可恢复） | 属主/管理员 |

## 六、域名

| 方法 | 路径 | 说明 | 鉴权 |
| ---- | ---- | ---- | ---- |
| GET | `/api/domains` | 可用域名列表（仅启用，默认域名排前） | 登录 |
| GET/POST | `/api/admin/domains` | 域名列表 / 新增域名 | ADMIN |
| PUT | `/api/admin/domains/{id}/status` | 上线/下线域名（默认域名不可下线） | ADMIN |
| PUT | `/api/admin/domains/{id}/default` | 设为默认域名 | ADMIN |
| DELETE | `/api/admin/domains/{id}` | 删除域名（默认域名不可删除） | ADMIN |

## 七、管理端

| 方法 | 路径 | 说明 | 鉴权 |
| ---- | ---- | ---- | ---- |
| GET | `/api/admin/short-links` | 全部短链分页（可按状态过滤，2=回收站） | ADMIN |
| PUT | `/api/admin/short-links/{code}/status` | 强制上下线任意短链 | ADMIN |
| DELETE | `/api/admin/short-links/{code}` | 删除任意短链（入回收站） | ADMIN |
| POST | `/api/admin/recycle-bin/purge` | 手动触发回收站清理（返回清理条数） | ADMIN |

## 八、跳转

| 方法 | 路径 | 说明 | 鉴权 |
| ---- | ---- | ---- | ---- |
| GET | `/{code}` | 短码跳转（302 + 异步统计） | 公开 |

## 九、功能说明

### 分组、回收站与域名

- **分组**：短链创建时可指定 `groupId`，可随时移动；分组列表附带各组短链数；
  删除分组时组内短链自动移回“未分组”。
- **回收站**：删除的短链进入回收站（状态 2，记录 `delete_time`），可还原或彻底删除；
  超过保留天数（`shortlink.recycle-bin.retention-days`，默认 30 天）的短链由每日
  定时任务（`purge-cron`，默认 03:30）物理清除，并同步清理缓存键、累计 PV 与统计归档行。
- **域名**：管理端维护短链域名池（含协议前缀，校验合法性、全局唯一），默认域名全局唯一且
  不可删除/停用；创建短链时用 `domainId` 指定域名，返回的 `shortUrl` 使用对应域名。
  跳转解析与域名无关，任意已配置域名下的短码均可正常 302。
  域名表是唯一事实来源（配置文件中无域名项）：全新部署首次启动自动以
  `http://localhost:{server.port}` 作为引导默认域名，请通过域名管理替换为正式域名。

### 错误码

| code | 含义 |
| ---- | ---- |
| 0 | 成功 |
| 40001 | 参数校验失败 |
| 40100 | 未登录或登录已过期 |
| 40300 | 无权访问该资源 |
| 40400 | 资源不存在 |
| 42900 | 请求过于频繁 |
| 41001/41002 | 长链接不合法 / 命中黑名单 |
| 42001/42002 | 用户名已存在 / 用户名或密码错误 |
| 43001~43006 | 分组与域名相关错误 |
| 44001 | 短链不在回收站中 |
| 50000/50001 | 系统错误 / 发号器繁忙 |
