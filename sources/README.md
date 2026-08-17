# 直播源聚合（`sources/`）

本目录由仓库内的 GitHub Actions **每日自动聚合**公开第三方 M3U/M3U8 播放列表，**不是** Android 应用内置源，也不会打进 APK。

脚本只拉取已经公开托管在 GitHub / GitHub Pages 上的播放列表，**不会**抓取央视网 / 央视频 / yangshipin 接口，也不会访问需要登录、DRM 或官方播放器的地址。

## 合并到 `main` 后的 Raw 地址

应用**首次安装的默认订阅**是中国频道聚合列表（`cn.m3u`）。仅看央视可在 **设置 → 直播源** 改为 `cctv.m3u`：

- 中国频道（合并去重，**应用默认**）：https://raw.githubusercontent.com/feverdestiny/miaoTv/main/sources/cn.m3u
- 仅 CCTV / 央视：https://raw.githubusercontent.com/feverdestiny/miaoTv/main/sources/cctv.m3u

在应用中打开 **设置 → 直播源**，粘贴上述 URL 保存。也可通过设备上的网页配置页（`http://<设备IP>:1616`）填写同一地址。

## 免责声明

聚合的是公开第三方地址，非官方授权，会失效；仅供自用。

流媒体地址来自上游开源列表，随时可能失效或变更。本仓库不提供、不保证、不授权任何节目内容，与央视网 / 央视频 / 各卫视官方无关。请仅在你已获得合法授权、且符合所在地法律法规的前提下使用。

## 如何增删上游

编辑 [`scripts/aggregate_sources.py`](../scripts/aggregate_sources.py) 顶部的 `UPSTREAMS` 列表：增加一项（`id` / `name` / `url`）或删除不需要的项。

当前默认上游：

- [iptv-org/iptv](https://github.com/iptv-org/iptv) — `countries/cn.m3u`
- [best-fan/iptv-sources](https://github.com/best-fan/iptv-sources) — `cn_cctv.m3u8`、`cn_all.m3u8`
- [CCSH/IPTV](https://github.com/CCSH/IPTV) — `live_lite.m3u`

某个上游 404 或下载失败时会跳过，不会让整次任务失败。工作流**不会**用 GitHub 托管 runner 的测速结果当硬过滤（美国节点测不准国内流）。

手动或定时更新：工作流 [Update IPTV sources](../.github/workflows/update-sources.yml)（每天 04:00 北京时间，也可 `workflow_dispatch`）。`sources/status.json` 记录最近一次拉取时间（UTC）和各上游 HTTP 状态。
