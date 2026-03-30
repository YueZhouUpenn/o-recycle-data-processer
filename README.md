# 财务回收对账系统

基于 Java + SQLite + Spring Boot 的本地财务回收对账工具，用于处理销售出库与回收数据的对账与分析。

## 功能特性

- ✅ 支持 80w+ 出库数据规模
- ✅ 三类回收数据管理：现场回收、统一回收、退货回收
- ✅ 自动检测多来源冲突（问题序列号）
- ✅ 幂等导入（文件哈希检测，避免重复）
- ✅ 自动生成总台账并导出
- ✅ 统计看板（回收率、各状态分布）
- ✅ 本地 SQLite 存储，无需安装数据库服务
- ✨ **Web界面** - 美观的可视化界面，实时查看数据并导出Excel
- ✨ **Web可视化界面**（新功能）
  - 🌐 在线查看数据库内容
  - 🔍 实时搜索与筛选
  - 📊 可视化统计面板
  - 📥 一键下载Excel文件
  - 🎨 现代化美观UI设计

## 项目结构

```
o-recycle-data-processer/
├── design/                 # 设计文档
│   ├── PRD.md             # 产品需求文档
│   └── TECH_DESIGN.md     # 技术设计方案
├── input_test/            # 测试数据集
├── data/                  # SQLite 数据库文件
├── output/                # 导出文件目录
├── start-web.sh           # Web启动脚本（macOS/Linux）
├── start-web.bat          # Web启动脚本（Windows）
├── WEB_USAGE.md           # Web界面使用指南
└── src/main/
    ├── java/com/company/recycle/
    │   ├── Main.java              # 命令行入口
    │   ├── db/                    # 数据库模块
    │   ├── model/                 # 实体类
    │   ├── pipeline/              # 导入管线
    │   ├── exporter/              # 导出模块
    │   ├── util/                  # 工具类
    │   ├── exception/             # 异常类
    │   └── web/                   # Web模块（新）
    │       ├── WebApplication.java    # Spring Boot启动类
    │       ├── WebController.java     # REST API控制器
    │       └── DataService.java       # 数据服务层
    └── resources/
        ├── static/
        │   └── index.html         # Web前端页面
        ├── application.properties # Spring Boot配置
        ├── schema.sql             # 数据库建表脚本
        └── logback.xml            # 日志配置
```

## 快速开始

### 1. 环境要求

- Java 17+
- Maven 3.6+

### 2. 启动Web界面（推荐）⭐

**最简单的方式：双击运行启动脚本**

- macOS/Linux: `./start-web.sh`
- Windows: `start-web.bat`

启动后在浏览器访问：`http://localhost:8080`

**或使用Maven命令启动：**

```bash
# 方式一：直接启动Web服务
mvn spring-boot:run -Dspring-boot.run.mainClass="com.company.recycle.web.WebApplication"

# 方式二：通过主程序菜单
mvn exec:java -Dexec.mainClass="com.company.recycle.Main"
# 然后选择 "6. 启动Web界面"
```

详细使用说明：[WEB_USAGE.md](WEB_USAGE.md)

### 3. 使用命令行模式

```bash
# 编译并运行
mvn clean package
java -jar target/o-recycle-data-processer-1.0.0.jar

# 或直接运行
mvn exec:java -Dexec.mainClass="com.company.recycle.Main"
```

### 4. 使用测试数据

程序内置"快速导入测试数据"功能，会自动导入 `input_test` 目录下的测试数据：
- 100 条出库数据
- 30 条现场回收（序列号 1-30）
- 30 条统一回收（序列号 31-60）
- 10 条退货（序列号 61-70）
- 30 条未回收（序列号 71-100）

## 主要功能

### 🌐 Web界面功能（推荐使用）

**核心特性：**
- 📊 **实时数据展示** - 美观的表格展示总台账数据
- 🔍 **智能搜索** - 支持多字段模糊搜索（序列号、物料、销售员等）
- 📈 **统计面板** - 实时显示关键指标和回收率
- 📥 **Excel导出** - 浏览器直接下载Excel文件
- 📱 **响应式设计** - 支持电脑、平板、手机访问
- 🎨 **现代化UI** - 渐变色背景、卡片式布局、流畅动画

**使用场景：**
- 日常查询和浏览数据
- 快速导出报表
- 移动办公查看数据

### 💻 命令行功能

**1. 导入文件**

支持导入四类文件：
- 销售出库单.xlsx
- 现场回收.xlsx
- 统一回收.xlsx
- 退货表.xlsx

**2. 刷新总台账**

根据全量出库数据与回收数据，计算每个序列号的最终回收状态：
- `未回收`：仅在出库表中
- `现场回收`：仅在现场回收文件中
- `统一回收`：仅在统一回收文件中
- `退货回收`：仅在退货表中
- `问题序列号`：出现在多个回收来源中（冲突）

**3. 导出总台账**

导出为 Excel 文件：`销售出库总台账_更新后.xlsx`

字段包括：
- 出库基础信息（序列号、物料、客户等）
- 回收状态
- 回收来源
- 回收日期
- 实际回收客户

**4. 统计信息**

- 总出库数
- 已回收数
- 未回收数
- 问题序列号数
- 回收率

## 数据库设计

SQLite 数据库包含 6 张表：

1. `t_outbound` - 销售出库主表
2. `t_recycle` - 现场/统一回收明细表
3. `t_return` - 退货回收明细表
4. `t_ledger` - 总台账（物化结果）
5. `t_import_batch` - 导入批次日志
6. `t_anomaly` - 异常记录表

完整设计见：[TECH_DESIGN.md](design/TECH_DESIGN.md)

## 业务规则

### 幂等性

- 使用文件名 + SHA-256 哈希作为批次唯一键
- 同一文件重复导入会被自动跳过

### 冲突检测

同一序列号若出现在多个回收来源中，会被标记为"问题序列号"并记录到异常日志。

### 异常类型

- 多来源冲突
- 回收越界（回收数据中的序列号不在出库表中）
- 文件内重复
- 必填列缺失

## 性能指标

- 适配 80w+ 出库数据
- 分块读取（5000 行/块）
- 内存峰值 < 500MB
- 全量刷新时间：3-8 分钟（普通办公电脑）

## 技术栈

### 后端
- **Java 17** - 编程语言
- **Spring Boot 3.2.0** - Web框架
- **SQLite** - 数据库
- **Apache POI 5.2.5** - Excel读写
- **SLF4J + Logback** - 日志框架

### 前端
- **HTML5/CSS3/JavaScript** - 原生Web技术
- **响应式设计** - 支持PC和移动端
- **Fetch API** - 异步数据请求

## 开发者文档

- [PRD 产品需求文档](design/PRD.md)
- [TECH_DESIGN 技术设计方案](design/TECH_DESIGN.md)
- [WEB_USAGE Web界面使用指南](WEB_USAGE.md)
- [PROJECT_SUMMARY 项目实施总结](PROJECT_SUMMARY.md)

## 常见问题

### Q: 如何启动Web界面？
**A:** 最简单的方式是运行启动脚本：
- macOS/Linux: `./start-web.sh`
- Windows: `start-web.bat`

### Q: Web界面端口被占用怎么办？
**A:** 编辑 `src/main/resources/application.properties`，修改 `server.port=8080` 为其他端口。

### Q: 数据库文件在哪里？
**A:** 位于 `data/recycle.db`，使用SQLite格式。

### Q: 如何备份数据？
**A:** 直接复制 `data/recycle.db` 文件即可。

## 许可证

内部使用，保留所有权利。
