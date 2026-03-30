# 🚀 快速开始指南

## 📋 已完成的功能

✅ **Web可视化界面** - 完整实现
- 美观的现代化UI设计（渐变紫色主题）
- 实时数据展示与搜索
- 统计面板（总出库数、回收率等）
- 一键导出Excel功能
- 响应式设计，支持手机/平板/电脑

✅ **后端API服务** - 完整实现
- Spring Boot 3.2.0 Web框架
- REST API接口
- 数据查询服务
- Excel文件导出

✅ **启动脚本** - 已创建
- `start-web.sh` (macOS/Linux)
- `start-web.bat` (Windows)

## 🎯 立即开始使用

### 步骤 1：启动Web服务

**最简单的方式（推荐）：**

```bash
# macOS/Linux
cd /Users/yuezhou/IdeaProjects/o-recycle-data-processer
./start-web.sh

# Windows
cd /Users/yuezhou/IdeaProjects/o-recycle-data-processer
start-web.bat
```

**或者使用Maven命令：**

```bash
cd /Users/yuezhou/IdeaProjects/o-recycle-data-processer

# 直接启动Web服务
mvn spring-boot:run -Dspring-boot.run.mainClass="com.company.recycle.web.WebApplication"
```

### 步骤 2：访问Web界面

启动成功后，在浏览器中打开：

```
http://localhost:8080
```

你将看到一个美观的页面，包含：
- 📊 统计卡片（顶部）
- 🔍 搜索框
- 📥 导出Excel按钮
- 📋 数据表格
- 📃 分页控件

### 步骤 3：使用功能

1. **查看数据**
   - 页面自动加载数据库中的总台账数据
   - 每页显示50条记录

2. **搜索数据**
   - 在搜索框输入关键词
   - 支持搜索：序列号、物料名称、销售员、客户等

3. **导出Excel**
   - 点击右上角"📥 导出Excel"按钮
   - 文件自动下载到本地

4. **查看统计**
   - 顶部卡片实时显示：
     - 总出库数（蓝色）
     - 已回收数（绿色）
     - 未回收数（橙色）
     - 回收率（红色）

## 📂 项目文件说明

### 新增文件（Web相关）

```
src/main/java/com/company/recycle/web/
├── WebApplication.java     # Spring Boot启动类
├── WebController.java      # REST API控制器
└── DataService.java        # 数据查询服务

src/main/resources/
├── static/
│   └── index.html          # Web前端页面（含CSS和JavaScript）
└── application.properties  # Spring Boot配置

根目录/
├── start-web.sh            # macOS/Linux启动脚本
├── start-web.bat           # Windows启动脚本
├── WEB_USAGE.md            # 详细使用指南
└── QUICK_START.md          # 本文件
```

### 修改的文件

- `pom.xml` - 新增Spring Boot和Gson依赖
- `Main.java` - 新增"6. 启动Web界面"菜单选项
- `README.md` - 更新项目说明

## 🎨 Web界面特点

### 视觉设计
- ✨ 渐变紫色背景（#667eea → #764ba2）
- 🎴 白色卡片式布局
- 🏷️ 彩色状态徽章
- 💫 流畅的悬停动画

### 功能特性
- 📱 响应式设计，自适应各种屏幕
- 🔍 实时搜索（防抖优化）
- 📊 分页浏览（每页50条）
- 📥 一键下载Excel
- 🔄 数据刷新功能

### 用户体验
- 🚀 快速加载
- 💨 流畅交互
- 🎯 清晰的信息层次
- 📐 合理的间距布局

## 🔧 配置说明

### 修改端口号

编辑 `src/main/resources/application.properties`：

```properties
server.port=8080  # 改为你需要的端口，如 9090
```

### 修改分页大小

编辑 `src/main/resources/static/index.html`，找到：

```javascript
let pageSize = 50;  // 改为你需要的数量
```

## 📊 API接口文档

### 1. 获取总台账数据

```
GET /api/ledger?page=1&pageSize=50&keyword=关键词
```

**响应示例：**
```json
{
  "success": true,
  "data": [...],
  "pagination": {
    "page": 1,
    "pageSize": 50,
    "total": 100,
    "totalPages": 2
  }
}
```

### 2. 获取统计信息

```
GET /api/statistics
```

**响应示例：**
```json
{
  "success": true,
  "statistics": {
    "totalOutbound": 100,
    "recycled": 70,
    "unrecycled": 30,
    "problem": 0,
    "recycleRate": 70.00
  },
  "distribution": [...]
}
```

### 3. 导出Excel

```
GET /api/export
```

直接返回Excel文件流，浏览器自动下载。

## ❓ 常见问题

### Q1: 启动后页面显示"暂无数据"？
**A:** 数据库可能是空的，需要先导入数据。有两种方式：
1. 通过命令行菜单导入数据
2. 运行 `Main.java`，选择"5. 快速导入测试数据"

### Q2: 如何停止Web服务？
**A:** 在启动Web服务的终端窗口按 `Ctrl + C`

### Q3: 端口8080被占用？
**A:** 修改 `application.properties` 中的 `server.port` 配置

### Q4: 浏览器无法访问？
**A:** 检查：
- 服务是否启动成功（查看控制台日志）
- 防火墙是否允许8080端口
- 尝试访问 `http://127.0.0.1:8080`

### Q5: 导出Excel失败？
**A:** 检查：
- `output` 目录是否有写入权限
- 浏览器是否允许下载文件
- 查看控制台日志了解详细错误

## 📚 更多文档

- **[WEB_USAGE.md](WEB_USAGE.md)** - Web界面详细使用指南
- **[README.md](README.md)** - 项目完整说明
- **[PROJECT_SUMMARY.md](PROJECT_SUMMARY.md)** - 项目实施总结
- **[design/PRD.md](design/PRD.md)** - 产品需求文档
- **[design/TECH_DESIGN.md](design/TECH_DESIGN.md)** - 技术设计方案

## 🎉 开始使用吧！

一切就绪，现在你可以：

1. ✅ 运行 `./start-web.sh` 启动Web服务
2. ✅ 访问 `http://localhost:8080` 查看界面
3. ✅ 享受美观的可视化数据查看体验
4. ✅ 一键导出Excel文件

祝使用愉快！ 🚀
