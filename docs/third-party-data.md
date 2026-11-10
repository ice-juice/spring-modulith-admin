# 第三方数据

登录日志使用 ip2region 3.3.8（Maven 坐标 `org.lionsoul:ip2region`，上游声明 Apache-2.0）离线解析 IPv4 / IPv6。数据库随应用打包，运行时不发送 IP 到第三方。

数据来源：官方仓库 [lionsoul2014/ip2region](https://github.com/lionsoul2014/ip2region)，固定提交 `92e6a788d17c3185c852ca983124c3fca280f992`，下载日期 2026-11-10。更新时同时更换文件并记录新提交和校验值。

| 资源 | 大小（字节） | SHA-256 |
|---|---:|---|
| `backend/src/main/resources/ip2region/ip2region_v4.xdb` | 11122742 | `a12ad5aca1277d67e525efcda531a20cc0d654ff4f087150d45bc17fca4bc260` |
| `backend/src/main/resources/ip2region/ip2region_v6.xdb` | 37277813 | `939f6b46bd2b8bec3cf7c5ceb8ba782266ae9b1f35b5ba7916700dec0b7506ed` |

本地、私网和无法解析的地址单独标记，不推断地理位置。异地标记只在前后两次成功登录都有有效位置、且国家或省份发生变化时设置；IP 变化另行保存。
