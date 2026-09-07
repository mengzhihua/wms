# WMS 仓储管理系统

基于 **Java 8 / Spring Boot 2.7 / MyBatis-Plus** 与 **Vue 3 / Element Plus** 的仓储管理系统，业务模型参考富勒 WMS 与 SAP EWM：
以 ASN → 收货 → 上架、出库单 → 分配 → 拣货 → 发运 为主链路，库存以“仓库 / 库位 / 货主 / 物料 / 批次”为最小粒度，
所有库存变动统一经库存引擎记账并写库存流水。

## 功能范围

| 模块 | 功能 |
| --- | --- |
| 基础数据 | 仓库、库区、库位（类型 / ABC / 拣货顺序 / 混放规则）、货主、物料（批次 / 效期 / 安全库存）、供应商、客户 |
| 入库管理 | 入库通知单 ASN、按行收货（批次 / 效期 / 收货库位）、自动生成上架任务与推荐库位、上架确认、关闭收货、**越库（ASN 绑定出库单，收货直接分拨到发货暂存）** |
| 出库管理 | 出库单、库存分配（FEFO → FIFO，仅存储/拣货位，支持部分分配）、取消分配、拣货任务确认（含少拣释放）、发运、**波次总拣 + 播种（多单合并拣货、按播种位分拨、波次发运）** |
| 库内管理 | 库存查询、库存汇总、库存流水、移库、库存调整、冻结 / 解冻、盘点（生成快照 → 录入 → 差异 → 过账） |
| 工作台 | 库位使用率、库存总量、待办入库 / 上架 / 出库 / 拣货、最近流水、安全库存预警 |
| 系统管理 | 登录 / 登出 / 修改密码，用户管理（ADMIN 管理员 / OPERATOR 作业员 / VIEWER 只读），全部 API 需登录 |

## 目录结构

```
backend/   Spring Boot 后端 (端口 8080)
  src/main/java/com/wms
    common/     统一响应 R、异常处理、BaseEntity、通用 CRUD 控制器、单号生成、工作台
    basic/      基础数据 entity / mapper / controller
    inbound/    ASN、收货、上架任务
    outbound/   出库单、分配、拣货任务、发运
    inventory/  库存引擎、库存流水、盘点
  src/main/resources
    schema.sql  表结构 (H2 / MySQL 兼容)
    data.sql    演示主数据 (WH01 仓库、OWN01 货主、SKU001-004 等)
frontend/  Vue 3 + Vite + Element Plus 前端 (端口 5173，/api 代理到 8080)
scripts/smoke.sh  全链路 API 冒烟脚本
```

## 快速开始

环境：JDK 8、Maven 3.6+、Node 18+。

```bash
# 后端（默认 H2 文件库 ./data/wms，零配置）
cd backend
mvn spring-boot:run

# 前端
cd frontend
npm install
npm run dev          # http://localhost:5173
```

首次启动自动创建管理员 `admin`，初始密码取 `WMS_ADMIN_PASSWORD`（默认 `admin123`，仅供本地演示，登录后请修改）。

H2 控制台默认关闭，排障时用 `WMS_H2_CONSOLE=true` 开启：<http://localhost:8080/h2>（JDBC URL `jdbc:h2:file:./data/wms`，用户 `sa`，无密码）。

### 安全相关环境变量

| 变量 | 说明 | 默认 |
| --- | --- | --- |
| `WMS_AUTH_SECRET` | 登录令牌签名密钥，**生产必须设置**；未设置时每次启动随机生成，重启后全员需重新登录 | 随机 |
| `WMS_TOKEN_TTL` | 令牌有效期（Spring Duration 格式） | `12h` |
| `WMS_ADMIN_PASSWORD` | 首次启动创建 admin 的初始密码 | `admin123` |
| `WMS_CORS_ORIGINS` | 允许跨域的前端来源，逗号分隔；前后端同源部署可设为空 | `http://localhost:5173` |
| `WMS_H2_CONSOLE` | 是否开启 H2 控制台 | `false` |

角色权限：`ADMIN` 不受限；`OPERATOR` 可执行入库 / 出库 / 库内作业，不能修改基础数据与用户；`VIEWER` 只读（可改自己密码）。

### 使用 MySQL

```bash
DB_HOST=127.0.0.1 DB_PORT=3306 DB_NAME=wms DB_USER=root DB_PASSWORD=xxx \
  mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

`DB_USER` / `DB_PASSWORD` 必填（无默认值）。两种数据库启动时都会自动执行 `schema.sql` / `data.sql`（均为幂等语句，MySQL 建议库字符集 utf8mb4）。

### 冒烟测试

后端启动后执行（需要 `curl`、`jq`）：

```bash
scripts/smoke.sh
```

脚本先校验匿名请求被拒（401）并以 `WMS_USER` / `WMS_PASS`（默认 admin / admin123）登录，再依次完成：创建 ASN → 收货 → 上架 → 建出库单 → 分配（含部分分配）→ 拣货 → 发运 → 移库 / 冻结 / 调整 → 盘点过账 → 两单波次总拣 / 播种 / 波次发运 → 越库收货直发，并输出库存汇总与流水统计。

> 注意：H2 默认使用 `./data/wms` 文件库，`schema.sql` 仅 `CREATE TABLE IF NOT EXISTS`；升级到含越库 / 波次 / 登录鉴权的版本时请删除旧的 `backend/data` 目录（或在 MySQL 中手工补充 `wms_asn.cross_dock_*`、`wms_pick_task.wave_id` 及 `wms_wave*` / `wms_sow_task` / `wms_user` 表）。

### 单元 / 集成测试

```bash
cd backend && mvn test   # 内存 H2：鉴权 API、角色策略、令牌 / 密码哈希、波次播种与越库业务闭环
```

## API 概览

统一返回 `{ code: 0, msg: "success", data: ... }`，业务错误 `code = 400`；未登录 / 令牌失效 HTTP 401，角色无权 HTTP 403。除登录外所有接口需携带 `Authorization: Bearer <token>`。

| 模块 | 接口 |
| --- | --- |
| 认证 | `POST /api/auth/login`（username, password → token + user），`GET /api/auth/me`，`POST /api/auth/password`（oldPassword, newPassword），`POST /api/auth/logout` |
| 用户 | `GET/POST/PUT/DELETE /api/system/user`（仅 ADMIN；至少保留一个启用的管理员） |
| 基础数据 | `GET/POST /api/basic/{warehouse,zone,location,owner,item,supplier,customer}` `/page` `/list` `/{id}` |
| 入库 | `POST /api/inbound/asn`，`/{id}/receive`，`/{id}/close-receiving`，`/{id}/cancel`，`GET /{id}/tasks`；`GET /api/inbound/putaway/page`，`POST /{taskId}/confirm` |
| 出库 | `POST /api/outbound/order`，`/{id}/allocate`，`/{id}/deallocate`，`/{id}/ship`，`/{id}/cancel`；`GET /api/outbound/pick/page`，`POST /{taskId}/confirm` |
| 波次 | `GET /api/outbound/wave/page`，`/{id}`；`POST /api/outbound/wave`（orderIds），`/pick/{taskId}/confirm`（总拣，可少拣），`/sow/{taskId}/confirm`（播种，可分次），`/{id}/ship`，`/{id}/cancel` |
| 库存 | `GET /api/inventory/page`，`/summary`，`/txn/page`；`POST /move`，`/adjust`，`/freeze` |
| 盘点 | `POST /api/inventory/count`，`/{id}/submit`，`/{id}/post`，`/{id}/cancel`，`GET /{id}/lines` |
| 工作台 | `GET /api/dashboard` |

## 业务规则要点

- **库位类型**：`STAGING_IN` 收货暂存、`STORAGE` 存储、`PICKING` 拣货、`STAGING_OUT` 发货暂存、`QC` 质检。只有 STORAGE / PICKING 可被分配。
- **收货**：批次管理物料必须录入批次；效期缺省按物料保质期推算；库存先进入收货暂存位并生成上架任务。
- **上架推荐**：优先同货主同物料同批次的存储位 → 按拣货顺序的空库位 → 满足混放规则（`mixSku` / `mixLot`）的库位；已被其他待上架任务占用的库位视为已占用。
- **分配**：FEFO（效期早优先）→ FIFO（收货早优先），使用 `allocatedQty` 预占；库存不足时状态为 `PART_ALLOCATED`，可再次分配。
- **拣货**：库存从拣货位移入 `STAGING_OUT`；少拣部分自动释放预占。
- **越库（Cross-Dock）**：ASN 填写 `crossDockOrderCode`（同仓同货主、未发运的出库单）后，收货时按物料 / 批次匹配出库单未分配需求，直接扣减收货暂存并写入 `STAGING_OUT`，生成已完成的 `XD` 拣货任务，出库单变为可发运；超出需求的数量仍走正常上架。
- **波次 / 播种（Wave / Pick-to-Sort）**：选择同仓多张已分配出库单建波次，普通拣货任务按“库位 + 物料 + 批次”合并为总拣任务；总拣确认后数量按出库单优先级分摊到各单（少拣缺口自动释放），并按出库单生成带播种位的播种任务；全部播种完成后可整波发运。已入波次的任务不可在普通拣货页操作，出库单也不可取消分配，需先取消波次。
- **盘点**：按仓库 / 库区生成账面快照，录入实盘后计算差异，过账时按差异写 `ADJUST` 流水。
