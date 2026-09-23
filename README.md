# WMS 仓储管理系统

基于 **Java 8 / Spring Boot 2.7 / MyBatis-Plus** 与 **Vue 3 / Element Plus** 的仓储管理系统，业务模型参考富勒 WMS 与 SAP EWM：
以 ASN → 收货 → 上架、出库单 → 分配 → 拣货 → 发运 为主链路，库存以“仓库 / 库位 / 货主 / 物料 / 批次”为最小粒度，
所有库存变动统一经库存引擎记账并写库存流水。

项目亮点见 [docs/项目亮点.md](docs/项目亮点.md)。

## 功能范围

| 模块 | 功能 |
| --- | --- |
| 基础数据 | 仓库、库区、库位（类型 / ABC / 拣货顺序 / 混放规则）、货主、物料（批次 / 效期 / 安全库存 / 质检 / 序列号管理，CSV 导入导出）、供应商、客户、**包材 / 箱型**（尺寸、容积、承重，可关联包材 SKU） |
| 入库管理 | 入库通知单 ASN、按行收货（批次 / 效期 / 收货库位 / SN 登记）、自动生成上架任务与推荐库位、上架确认、关闭收货、**收货质检放行 / 拒收**、**退货入库**（进质检位冻结）、**越库（ASN 绑定出库单，收货直接分拨到发货暂存）** |
| 出库管理 | 出库单、库存分配（FEFO → FIFO，仅存储/拣货位，支持部分分配）、取消分配、拣货任务确认（含少拣释放）、**复核打包（箱型推荐、包材库存扣减、承运商 / 运单）**、发运（SN 物料需扫描序列号）、**波次总拣 + 播种（多单合并拣货、按播种位分拨、波次发运）**、**波次策略引擎（8 种预置策略按优先级自动成波 / 拆波、预览试跑）**、**缺货登记**、**包裹对象（一单一包 / 按重量拆箱 / 手工分箱、运单回填、拆包）** |
| 库内管理 | 库存查询、库存汇总、库存流水（含 CSV 导出）、移库、库存调整、冻结 / 解冻、盘点（生成快照 → 录入 → 差异 → 过账）、**盘点计划体系（计划 → 审批 → 一库存一任务锁库 → 领取 / 指派 → 提交 → 多轮复盘 → 库存调整单审核 → 盘点报告；循环 / 动碰 / 随机 / 异动盘点）**、**拣货位 Min/Max 补货**、**跨仓调拨补货**、**序列号 (SN/IMEI) 查询与追溯** |
| 报表 | 库龄、效期预警、作业 KPI、**ABC 动态分析（可一键应用到物料）**、**操作员节点计件效能** |
| 工作台 | 库位使用率、库存总量、待办入库 / 上架 / 出库 / 拣货、最近流水、安全库存预警 |
| 系统管理 | 登录 / 登出 / 修改密码，用户管理（ADMIN 管理员 / OPERATOR 作业员 / VIEWER 只读），操作日志审计，全部 API 需登录 |

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

> 注意：`schema.sql` 仅 `CREATE TABLE IF NOT EXISTS`，不会修改已有表。H2 文件库（`./data/wms`）启动时会额外执行 `schema-h2-upgrade.sql` 自动补列；**已有 MySQL 库升级**需手工执行 `backend/src/main/resources/db/mysql-upgrade.sql`（含历次新增列，列已存在的语句报 1060 可忽略），并补建新表（`wms_wave*` / `wms_sow_task` / `wms_user` / `wms_count_*` / `wms_package*` 等，可直接执行 `schema.sql`）。

### 单元 / 集成测试

```bash
cd backend && mvn test   # 内存 H2：鉴权 API、角色策略、令牌 / 密码哈希、波次播种与越库业务闭环、盘点计划闭环、波次策略 / 缺货 / 包裹闭环
```

## API 概览

统一返回 `{ code: 0, msg: "success", data: ... }`，业务错误 `code = 400`；未登录 / 令牌失效 HTTP 401，角色无权 HTTP 403。除登录外所有接口需携带 `Authorization: Bearer <token>`。

| 模块 | 接口 |
| --- | --- |
| 认证 | `POST /api/auth/login`（username, password → token + user），`GET /api/auth/me`，`POST /api/auth/password`（oldPassword, newPassword），`POST /api/auth/logout` |
| 用户 | `GET/POST/PUT/DELETE /api/system/user`（仅 ADMIN；至少保留一个启用的管理员） |
| 基础数据 | `GET/POST /api/basic/{warehouse,zone,location,owner,item,supplier,customer,carton}` `/page` `/list` `/{id}`；`GET /api/basic/item/export`，`POST /api/basic/item/import`（CSV），`POST /api/basic/item/abc-apply`（ownerCode, days） |
| 入库 | `POST /api/inbound/asn`，`/{id}/receive`（行可带 `serialNos`），`/{id}/close-receiving`，`/{id}/cancel`，`GET /{id}/tasks`，`/{id}/qc-tasks`；`GET /api/inbound/putaway/page`，`POST /{taskId}/confirm`；`GET /api/inbound/qc/page`，`POST /{taskId}/inspect`（passQty, rejectQty） |
| 出库 | `POST /api/outbound/order`，`/{id}/allocate`，`/{id}/deallocate`，`GET /{id}/carton-suggest`，`POST /{id}/pack`（packageCount, grossWeight, carrier, trackingNo, cartonCode），`/{id}/ship`（carrier, trackingNo, serialNos），`/{id}/cancel`；`GET /api/outbound/pick/page`，`POST /{taskId}/confirm` |
| 波次 | `GET /api/outbound/wave/page`，`/{id}`；`POST /api/outbound/wave`（orderIds），`/pick/{taskId}/confirm`（总拣，可少拣），`/sow/{taskId}/confirm`（播种，可分次），`/{id}/ship`，`/{id}/cancel` |
| 库存 | `GET /api/inventory/page`，`/summary`，`/txn/page`，`/export`，`/txn/export`；`POST /move`，`/adjust`，`/freeze` |
| 补货 | `GET /api/inventory/replenish/page`；`POST /api/inventory/replenish/generate`（Min/Max），`POST /api/inventory/replenish/transfer`（跨仓，源仓与目标仓相同则退回仓内补货），`POST /api/inventory/replenish`，`/{id}/confirm`，`/{id}/cancel` |
| 控制塔 | `GET /api/open/ir/snapshots`；`POST /api/open/ir/actions`，以及 `/allocate`、`/replenish`。请求头 `X-Api-Key`，同一幂等键重复调用返回缓存结果 |
| 序列号 | `GET /api/inventory/serial/page`，`GET /api/inventory/serial/{serialNo}` |
| 报表 | `GET /api/report/aging`，`/expiry`，`/kpi`，`/abc`（warehouseCode, ownerCode, days），`/labor`（days, operator） |
| 审计 | `GET /api/system/oplog/page` |
| 盘点 | `POST /api/inventory/count`，`/{id}/submit`，`/{id}/post`，`/{id}/cancel`，`GET /{id}/lines` |
| 盘点计划 | `GET /api/inventory/count-plan/page`，`/{id}`，`/{id}/report`，`/{id}/tasks`，`/{id}/recounts`；`POST /api/inventory/count-plan`，`/{id}/submit`，`/{id}/approve`，`/{id}/generate`，`/{id}/complete`，`/{id}/cancel`，`/task/{tid}/claim`、`/assign`、`/count`，`/recount/{rid}/count`、`/next-round`、`/confirm`，`/{id}/adjust`，`/adjust/{aid}/approve` |
| 波次策略 | `GET /api/outbound/wave-strategy/list`；`POST /api/outbound/wave-strategy`，`/{id}/toggle`，`/run`（warehouseCode, strategyId 可空, dryRun） |
| 缺货 | `GET /api/outbound/shortage/page`；`POST /api/outbound/shortage/task/{taskId}`，`/wave-task/{taskId}`，`/{id}/close` |
| 包裹 | `GET /api/outbound/package/page`，`/{id}`，`/by-order/{orderId}`，`/by-wave/{waveId}`；`POST /api/outbound/package/build`，`/manual`，`/unpack/{orderId}`；`PUT /{id}/tracking` |
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
- **盘点计划**：`DRAFT → PENDING → APPROVED → EXECUTING → COMPLETED / CANCELLED`。审批通过后按范围（全仓 / 库区 / 物料 / ABC 等级 / 动碰日期 / 随机抽样比例）一条库存生成一条盘点任务并加 `count_lock`（锁定库存不可分配、移库、手工调整）；提交实盘有差异自动生成复盘任务（可多轮），确认不符后生成库存调整单，审核通过经 `InventoryService.adjust` 过账（盘后库存已变动则拒绝）；任务 / 复盘 / 调整单全部完成方可结束计划并释放锁定。
- **波次策略**：策略含优先级、订单数 / 单据 SKU 数 / 单据件数上下限、波次 SKU 品项与总件数上限、按物料 / 货主 / 承运商分组、储区限制、尾单截止小时、打包策略。运行时按优先级扫描已分配（含部分分配）且未入波次的出库单，满足条件的分组成波并按上限自动拆分；手工建波次不受影响。
- **缺货登记**：普通拣货 / 波次总拣确认时可登记缺货（缺货 + 实拣 ≤ 计划），系统按实拣走原拣货流程并记录缺货明细供查询、关闭。
- **包裹**：波次播种完成或出库单 `PICKED` 后按策略建包（一单一包 / 按物料重量拆箱）或手工分箱，包裹编码 `SH+日期+序号`，可回填承运商 / 运单 / 重量；拆包回退到 `PICKED`，发运后包裹状态同步 `SHIPPED`。
- **质检 / 退货**：物料 `qcRequired` 或 ASN 类型 `RETURN` 时，收货进入 `QC` 库位并冻结，生成质检任务；放行部分解冻并生成上架任务，拒收部分写 `QC_REJECT` 流水出库。
- **序列号 (SN/IMEI)**：物料 `snControl` 时，收货必须逐一登记 SN（个数 = 数量，同货主唯一，在库 SN 不可重复收货）；发运必须扫描在库 SN 且每个物料数量与应发一致，发运后 SN 变为 `SHIPPED` 并记录出库单；退货收货可使已发运 SN 回库。
- **箱型推荐 / 包材**：按订单已拣数量 × 物料体积 / 重量汇总，选择能容纳的最小箱型；单箱装不下时用最大箱型估算箱数。箱型关联包材 SKU 时，打包按箱数扣减包材库存（`PACK_CONSUME` 流水），不足报错。
- **补货**：拣货位库存低于物料 `minStock` 时从存储位生成补货任务，补至 `maxStock`，确认后写 `REPLENISH` 流水。跨仓时 `POST /api/inventory/replenish/transfer` 扣减源仓可用量并加到目标仓拣货位，任务直接完成；源仓与目标仓相同则退回仓内 Min/Max 补货。
- **ABC 分析**：按周期内 `SHIP` 流水发运量降序累计，累计占比 < 70% 为 A、< 90% 为 B、其余 C；管理员可一键应用到物料 `abcClass`。
- **计件效能**：基于库存流水 `operator` 按人 / 日统计收货、上架、拣货、发运、补货、质检拒收的数量与笔数。

## 控制塔对接

出库快照、分配和补货见 [技术方案](docs/技术方案.md)。

有 API Key 时，控制塔读 `GET /api/open/ir/snapshots`，写 `POST /api/open/ir/actions`，并回退 `/allocate`、`/replenish`。补货带了不同的源仓就走跨仓调拨。没有 Key 时，分配走 `/api/outbound/order/{id}/allocate`，跨仓走 `POST /api/inventory/replenish/transfer`。仓号 `WH-SH/BJ/GZ` 映成 `WH01/02/03`。

发行包默认端口是 `8082`。控制塔种子里的 WMS 地址是 `8083`，同机联调时两边要改成同一个端口。

## 发布包（开箱即用）

前端生产构建打进 Spring Boot 可执行 JAR。三种用法：

### 1. 服务端（任意已装 JDK 17 的机器）

```bash
java -jar wms-backend-1.0.0.jar --server.port=8082
```

Linux systemd 示例见发布包 `README.txt`。

### 2. 便携包（需本机已装 Java）

```bash
bash scripts/package-release.sh
unzip release/wms-1.0.0.zip
cd wms-1.0.0
```

| 系统 | 怎么用 |
| --- | --- |
| Linux | `./start.sh` |
| macOS | 双击 `start.command`，或 `./start.sh` |
| Windows | 双击 `start.bat` |

### 3. 原生包（捆绑 JRE，不必装 Java）

合并到默认分支且便携包冒烟通过后，GitHub Actions 自动发布 GitHub Release（也可在 Actions 里手动 `workflow_dispatch`）。分别在 Ubuntu / Windows / macOS 生成：

- `wms-1.0.0-linux-x64.zip` → `bin/wms`
- `wms-1.0.0-windows-x64.zip` → 双击 `wms.exe`
- `wms-1.0.0-macos-arm64.zip` → Apple Silicon（M 系列），双击 `wms.app`
- `wms-1.0.0-macos-x64.zip` → Intel Mac，双击 `wms.app`

浏览器访问 `http://127.0.0.1:8082`。默认账号 `admin / admin123`。

十二套系统可同时启动：OMS 8081 / WMS 8082 / TMS 8083 / BMS 8084 / SAP 8085 / OA 8086 / SRM 8087 / BOM 8088 / INV 8089 / IR 8090 / CRM 8091 / DMS 8092。

