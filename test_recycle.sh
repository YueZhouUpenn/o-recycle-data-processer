#!/bin/bash
cd /Users/yuezhou/IdeaProjects/o-recycle-data-processer

# 清理数据库
rm -f data/*.db

# 运行程序并自动选择菜单项
printf "5\n0\n" | timeout 30 java -jar target/o-recycle-data-processer-1.0.0.jar 2>&1 | tee /tmp/recycle_test.log

echo ""
echo "=== 数据库统计 ==="
sqlite3 data/recycle.db "SELECT 'Outbound' as table_name, COUNT(*) as count FROM t_outbound UNION ALL SELECT 'Recycle', COUNT(*) FROM t_recycle UNION ALL SELECT 'Return', COUNT(*) FROM t_return;"
