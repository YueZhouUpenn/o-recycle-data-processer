# 问题排查与解决

## ✅ 已修复的问题

### 问题1：页面显示"加载失败: undefined"

**原因：** 数据库查询结果包含NULL值，前端JavaScript无法正确处理。

**解决方案：**
1. 在`DataService.java`中添加了`nvl()`方法处理NULL值
2. 所有`rs.getString()`调用都包装了`nvl()`
3. 确保所有返回给前端的字段都不会是null

**修改的文件：**
- `src/main/java/com/company/recycle/web/DataService.java`
- `src/main/java/com/company/recycle/web/WebApplication.java`

### 问题2：javax.annotation包找不到

**原因：** Spring Boot 3.x使用Jakarta EE规范，包名从`javax`改为`jakarta`。

**解决方案：**
- 将`javax.annotation.PostConstruct`改为`jakarta.annotation.PostConstruct`
- 将`javax.annotation.PreDestroy`改为`jakarta.annotation.PreDestroy`

## 🚀 重新启动服务

修复代码后，需要重新启动Web服务：

### 方式一：如果正在运行，先停止
1. 在运行Web服务的终端窗口按 `Ctrl + C`
2. 等待服务完全停止

### 方式二：重新启动

**使用IntelliJ IDEA（推荐）：**
1. 在IDEA中找到`WebApplication.java`
2. 右键点击文件或类名
3. 选择 **Run 'WebApplication.main()'**
4. 等待编译和启动完成

**使用Maven命令：**
```bash
cd /Users/yuezhou/IdeaProjects/o-recycle-data-processer
mvn spring-boot:run -Dspring-boot.run.mainClass="com.company.recycle.web.WebApplication"
```

**使用启动脚本：**
```bash
# macOS/Linux
./start-web.sh

# Windows
start-web.bat
```

### 方式三：查看是否有旧进程

如果端口被占用，查找并关闭旧进程：

```bash
# 查找占用8080端口的进程
lsof -i :8080

# 或者
netstat -an | grep 8080

# 如果找到进程ID (PID)，强制关闭
kill -9 <PID>
```

## 🔍 验证修复

### 1. 检查启动日志

启动服务后，查看日志中是否有：
```
✓ 数据库初始化完成
✓ Web服务已启动，请访问: http://localhost:8080
```

没有错误堆栈信息即为正常。

### 2. 访问页面

在浏览器中打开：`http://localhost:8080`

**正常情况应该看到：**
- ✅ 统计卡片显示数字（不是"-"）
- ✅ 表格显示数据（不是"加载中..."或错误）
- ✅ 搜索框可用
- ✅ 导出按钮可点击

### 3. 测试功能

- **查看数据**：页面应该显示总台账数据
- **搜索功能**：输入关键词后能搜索
- **导出Excel**：点击按钮能下载文件
- **分页**：能切换不同页面

## 🐛 常见问题

### Q1: 页面仍然显示"暂无数据"

**可能原因：**
- 数据库是空的
- 没有导入数据

**解决方案：**
1. 运行命令行版本导入测试数据：
```bash
mvn exec:java -Dexec.mainClass="com.company.recycle.Main"
# 然后选择 "5. 快速导入测试数据"
```

2. 或在IDEA中运行`Main.java`，选择菜单项5

### Q2: 统计面板显示"0"

**可能原因：**
- t_ledger表是空的
- 需要刷新总台账

**解决方案：**
1. 先导入数据（见Q1）
2. 运行命令行程序，选择"2. 刷新总台账"
3. 刷新浏览器页面

### Q3: 导出Excel失败

**可能原因：**
- output目录不存在
- 没有写入权限

**解决方案：**
```bash
mkdir -p /Users/yuezhou/IdeaProjects/o-recycle-data-processer/output
chmod 755 /Users/yuezhou/IdeaProjects/o-recycle-data-processer/output
```

### Q4: 浏览器控制台显示CORS错误

**可能原因：**
- 浏览器跨域限制

**解决方案：**
- 确保访问的是 `http://localhost:8080` 而不是其他地址
- WebController已配置CORS，应该不会有问题

### Q5: 端口8080被占用

**解决方案：**

编辑 `src/main/resources/application.properties`：
```properties
server.port=9090  # 改为其他端口
```

然后重新启动服务，访问新端口。

## 📊 查看日志

如果问题仍然存在，查看详细日志：

```bash
# 查看最新日志
tail -f /Users/yuezhou/IdeaProjects/o-recycle-data-processer/logs/application.log

# 查看最后100行
tail -100 /Users/yuezhou/IdeaProjects/o-recycle-data-processer/logs/application.log

# 搜索错误
grep -i error /Users/yuezhou/IdeaProjects/o-recycle-data-processer/logs/application.log
```

## 🔧 完整重置流程

如果所有方法都不行，尝试完整重置：

```bash
cd /Users/yuezhou/IdeaProjects/o-recycle-data-processer

# 1. 停止所有Java进程
pkill -f "WebApplication"

# 2. 清理编译文件
rm -rf target/

# 3. 清理日志（可选）
rm -f logs/application.log

# 4. 重新编译（如果有Maven）
mvn clean compile

# 5. 在IDEA中重新运行WebApplication
```

## ✅ 确认修复成功

修复成功的标志：
1. ✅ 页面正常加载，没有错误提示
2. ✅ 统计卡片显示实际数字
3. ✅ 表格显示数据记录
4. ✅ 搜索功能正常工作
5. ✅ 可以导出Excel文件
6. ✅ 分页切换正常

## 📚 相关文档

- [QUICK_START.md](QUICK_START.md) - 快速开始指南
- [WEB_USAGE.md](WEB_USAGE.md) - Web界面使用说明
- [README.md](../README.md) - 项目完整说明

## 🆘 仍然无法解决？

如果按照以上步骤仍无法解决：

1. **查看完整错误日志**
   ```bash
   cat logs/application.log | grep -A 20 "Exception"
   ```

2. **检查数据库文件**
   ```bash
   ls -lh data/recycle.db
   ```

3. **验证Java版本**
   ```bash
   java -version
   # 需要Java 17+
   ```

4. **在IDEA中查看编译错误**
   - Build > Rebuild Project
   - 查看Problems面板

记录错误信息，以便进一步排查。
