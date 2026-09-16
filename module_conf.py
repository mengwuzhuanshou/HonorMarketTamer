# -*- coding: utf-8 -*-
"""HonorMarketTamer 构建配置（供 tools/module-builder/builder.py 读取）。"""
MODULE = {
    "package": "com.tamer.honormarket",
    "version_name": "1.3.6",
    "version_code": 27,
    "app_label": u"荣耀市场净化",
    "xposed_description": u"荣耀应用市场功能开关：保留搜索与更新，其余功能可屏蔽；附运动健康传感器闸门",
    "xposed_scope": "com.hihonor.appmarket,com.hihonor.health",
    "dist_name": "HonorMarketTamer-v%s.apk",
    "icon_png": "tools/icon/ic_launcher.png",
}
