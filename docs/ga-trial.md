# CoSky 8.0 GA 试运行手册

> GA 的条件见[路线图](roadmap.md) §2:RC 在至少一个真实环境试运行两周,没有 P0/P1 问题。
> 本文说明这两周怎么跑:怎么升级、盯哪些指标、什么算 P0/P1、做哪些演练、怎样算通过。
> 文中的链接指向文档站的中文页面;从故障排查页能查到每条告警出现时该怎么办。

---

## 1. 规则

- **时钟**:从某个 RC 在环境中承接真实流量、并且第 3 节的告警都已就位的那天算起,连续 14 天。
- **环境**:生产,或与生产同等的环境:真实的应用在上面注册、读取 Config,Redis 的拓扑和网络与生产一致。只有压测流量的环境不算。
- **出现 P0/P1**:修复后发新 RC,时钟从新 RC 上线那天重新算。
- **只为 P2 发新 RC**:时钟不重算,但最后一个 RC 自身至少跑满 7 天,并重做与修复相关的演练。
- **8.0.0 就是最后一个 RC 的代码**:之后只改版本号和文档。RC 之后的任何代码改动都先发 RC。
- 维护者根据第 8 节的记录决定发布 8.0.0。

## 2. 升级到当前的 RC

下文以 `8.0.0-rc.3` 为例;试运行期间发了新 RC,就换成新的版本号。

### 2.1 准备

- **读发版说明**:从当前版本到目标版本之间每一版的 “Upgrading from …” 和 “Known limits”,见[发布页](https://github.com/Ahoo-Wang/CoSky/releases)。
- **备份**:RDB 快照,并把 Config 导出为 zip,见[备份与恢复](https://cosky.ahoo.me/zh/guide/backup#备份)。
- **Redis**:7.0 及以上(8 也可以),`maxmemory-policy noeviction`,持久化与复制按[Redis 必须做到的](https://cosky.ahoo.me/zh/guide/backup#redis-必须做到的)。
- **鉴权开着**:`cosky_auth_enabled` 为 1;bootstrap token 用完已吊销,并留好第二条管理员路径,见[安全](https://cosky.ahoo.me/zh/guide/security)。
- **可观测性**:Prometheus 抓取每个 Pod 的 `/actuator/prometheus`,`k8s/base` 把 Actuator 放在 8081 端口;server 日志和审计日志(每行一条 JSON)一起收集。

### 2.2 从 rc.1 或 rc.2

直接替换。server 和应用先后不限,滚动更新时新旧版本可以并存。

### 2.3 从 beta

- 先升级 server,再升级应用,在同一个时间窗口内完成:beta 应用和 rc server 互相不认识 watch 结束的新表达,这样的 watch 要等应用重启才会重新开始。
- 逐项核对[从 8.0 beta 升级到 rc.1](https://cosky.ahoo.me/zh/guide/upgrading#从-8-0-beta-升级到-rc-1)的清单:bootstrap token 的格式、不带单位的时长按秒计、starter 的健康指示器改名为 `cosky`、告警中 `cosky_lease_active` 要取 `max by (namespace)`、Actuator 在 8081、镜像以 `999:999` 运行等。
- 从 beta.1 起,Redis 中的数据都能沿用。

### 2.4 server

1. 在 overlay 的 `kustomization.yaml` 中把 `newTag` 设为 `8.0.0-rc.3`,然后 apply。`k8s/base` 的 Deployment 一次替换一个 Pod,停下的副本的客户端重连到其他副本,从原来的位置继续。
2. 每个新 Pod 就绪(`/actuator/health/readiness` 为 `UP`)后再看下一个;日志中没有 ERROR。
3. 全部换完后,各副本的 `cosky_cache_revision` 一致。`cosky_watch_gone_total` 在滚动期间小幅上升是正常的,之后不再涨。

### 2.5 应用

1. 把 `cosky-bom` 提到 `8.0.0-rc.3`,照常构建和部署。
2. 健康指示器 `cosky` 为 `UP`,`connection` 为 `SYNCED`,不是 `DEGRADED`。
3. Dashboard 中每个服务的实例数与实际运行的进程数一致。
4. 容器里的用户没有可写的家目录时,应用记录一条 INFO `Snapshots are off`,这是预期的;要保留快照,就挂载卷并设置 `COSKY_SNAPSHOT_DIR`,见[应用写不了快照](https://cosky.ahoo.me/zh/guide/troubleshooting#应用写不了快照)。

### 2.6 回退

RC 之间 Redis 中的数据没有变化,可以回到上一个 RC:把镜像标签和 `cosky-bom` 改回去。回到 beta 不在承诺之内。

## 3. 指标与告警

下面的规则可以直接放进 Prometheus 的规则文件,或者 PrometheusRule 的 `spec.groups`。`instance` 是抓取目标的标签,`job="cosky"` 请按你的抓取任务改。

```yaml
groups:
  - name: cosky
    rules:
      # 副本访问 Redis 失败。空闲时也会计数:每个副本的回收器每秒都向 Redis 申请锁。
      - alert: CoSkyStoreFailing
        expr: sum by (instance) (rate(cosky_store_failures_total[1m])) > 0
        for: 3m
        labels: { severity: warning }
      # 最慢的副本还没到达最快的副本一分钟前的位置:它与 Redis 断开了。
      - alert: CoSkyReplicaBehind
        expr: min by (namespace) (cosky_cache_revision) < max by (namespace) (cosky_cache_revision offset 1m)
        for: 1m
        labels: { severity: warning }
      - alert: CoSkyWriteLatencyHigh
        expr: histogram_quantile(0.99, sum by (le) (rate(cosky_store_apply_seconds_bucket[5m]))) > 0.1
        for: 10m
        labels: { severity: warning }
      # Redis 丢失了已确认的写入。
      - alert: CoSkyRevisionRegression
        expr: increase(cosky_revision_regression_total[5m]) > 0
        labels: { severity: critical }
      # 副本的视图与 Redis 不一致,这本不该发生。
      - alert: CoSkyAntiEntropyDrift
        expr: increase(cosky_anti_entropy_drift_total[10m]) > 0
        labels: { severity: critical }
      - alert: CoSkyReaperFailing
        expr: sum by (namespace) (increase(cosky_reaper_failures_total[5m])) > 0
        labels: { severity: warning }
      - alert: CoSkyLeaseProtection
        expr: max by (namespace) (cosky_lease_protection_active) == 1
        labels: { severity: warning }
      # 某个客户端没带 token,或者带着吊销、过期的 token。
      - alert: CoSkyAuthFailures
        expr: sum by (reason) (rate(cosky_auth_failures_total{reason=~"missing|invalid"}[5m])) > 0
        for: 10m
        labels: { severity: warning }
      - alert: CoSkyAuthDisabled
        expr: min(cosky_auth_enabled) < 1
        labels: { severity: critical }
      # compacted 在滚动更新时会零星出现,不在其中。
      - alert: CoSkyWatchGone
        expr: sum by (reason) (increase(cosky_watch_gone_total{reason=~"slow|epoch|regression"}[10m])) > 0
        labels: { severity: warning }
      - alert: CoSkyDown
        expr: up{job="cosky"} == 0
        for: 2m
        labels: { severity: critical }
      - alert: CoSkyHeapHigh
        expr: >-
          sum by (instance) (jvm_memory_used_bytes{area="heap"})
          / sum by (instance) (jvm_memory_max_bytes{area="heap"}) > 0.85
        for: 15m
        labels: { severity: warning }
```

| 告警 | 出现时 | 怎么查 |
|---|---|---|
| `CoSkyStoreFailing`、`CoSkyReplicaBehind` | 只有一个副本:它到 Redis 的路径断了;每个副本都有:Redis 宕机或正在故障切换。Redis 丢失写入、revision 回退时,`CoSkyReplicaBehind` 也会短暂触发 | [副本与 Redis 断开](https://cosky.ahoo.me/zh/guide/troubleshooting#副本与-redis-断开)、[写入失败并返回 STORE_UNAVAILABLE](https://cosky.ahoo.me/zh/guide/troubleshooting#写入失败并返回-store-unavailable) |
| `CoSkyWriteLatencyHigh` | 写入 p99 超过 100 ms。性能目标是 20 ms,告警留了余量 | Redis 的延迟和 CPU,server 到 Redis 的网络 |
| `CoSkyRevisionRegression` | Redis 故障切换丢掉了最后的写入,或恢复了较旧的快照 | [Redis 丢失了写入](https://cosky.ahoo.me/zh/guide/troubleshooting#redis-丢失了写入)。没有故障切换或恢复时出现,按 P0 报告 |
| `CoSkyAntiEntropyDrift` | 不该发生 | 按 P1 报告,附上日志 |
| `CoSkyReaperFailing` | 某个 namespace 所在的分片失败 | [cosky_reaper_failures_total 上升](https://cosky.ahoo.me/zh/guide/troubleshooting#cosky-reaper-failures-total-上升) |
| `CoSkyLeaseProtection` | 超过一半的租约同时到期,回收暂停 | [回收暂停](https://cosky.ahoo.me/zh/guide/troubleshooting#回收暂停) |
| `CoSkyAuthFailures` | 某个客户端的 token 不对 | [调用因 token 被拒绝](https://cosky.ahoo.me/zh/guide/troubleshooting#调用因-token-被拒绝),按审计日志中的 `remote` 找到它 |
| `CoSkyAuthDisabled` | 鉴权被关闭,每个调用者都是管理员 | 检查 `cosky.auth.enabled` |
| `CoSkyWatchGone` | 投递跟不上(`slow`),或 Redis 丢失了数据(`epoch`、`regression`) | [cosky_watch_gone_total 上升](https://cosky.ahoo.me/zh/guide/troubleshooting#cosky-watch-gone-total-上升) |
| `CoSkyDown`、`CoSkyHeapHigh` | 副本挂了,或堆接近上限 | [副本没有就绪](https://cosky.ahoo.me/zh/guide/troubleshooting#副本没有就绪)、[容量规划](https://cosky.ahoo.me/zh/guide/deployment#容量规划) |

有 redis_exporter 的话,再加上 Redis 内存接近 `maxmemory`(`redis_memory_used_bytes / redis_memory_max_bytes > 0.8`):在 `noeviction` 下,Redis 满了就拒绝写入。有 kube-state-metrics 的话,再加上 Pod 重启,尤其是 OOMKilled。

告警是信号,P0/P1 是对 CoSky 缺陷的判断:Redis 或网络本身出了故障、CoSky 的表现与文档(包括 Known limits)一致,不算 CoSky 的问题;但如果文档写的表现在真实环境中不可接受,也请提出来。

看板上建议放:写入 p99、`max by (namespace) (cosky_cache_lag_seconds)`、每个副本的 `cosky_watch_active`、`rate(cosky_watch_events_sent_total[5m])`、`max by (namespace) (cosky_lease_active)`、`rate(cosky_lease_expired_total[5m])`、各副本的堆和 GC 停顿、Redis 的 CPU 和内存。

## 4. 什么算 P0/P1

判断不了时按高的一级记,再讨论降级。

**P0**:阻断 GA,立即修。

- **数据丢失或损坏**:Redis 没有丢数据,CoSky 却丢了或改错了 Config、Instance 或访问管理的对象。
- **返回错误的数据**:应用读到从未写过的值;或者 server 已有新值,应用却停在旧值超过一分钟,而且不会自己恢复。
- **安全**:token 拿到它的角色不允许的数据或写入;鉴权被绕过;吊销的 token 仍然可用。
- **活着的实例被删除**:续约正常的应用的实例被回收,调用方找不到它。
- **全部副本同时不可用**,而 Redis 是好的。
- **server 起不来**:合法的配置下,或者按升级说明升级之后。

**P1**:阻断 GA,修复后发新 RC。

- **功能失效,又没有可接受的规避办法**:watch 停止收到事件且不自己恢复;应用断开后不再重连;Config 变化后应用一直没有刷新。
- **故障恢复明显差于文档**:Redis 故障切换后,不可用的时间远超[性能](https://cosky.ahoo.me/zh/guide/performance#redis-cluster-故障切换)一页的数字(比如超过判定下线的时间再加 30 秒),或者要重启 server 或应用才恢复。
- **资源泄漏**:两周内堆、线程、连接或 Redis 内存只涨不落,或者 OOM。
- **`cosky_anti_entropy_drift_total` 上升。**
- **违反冻结的契约**:rc.1 起只增不改的内容(gRPC 与 REST API、client 与 starter 的公开 API 和配置项、server 的配置项、Deployment 列出的指标、Redis 中的数据)被删除、改名或改了含义。
- **按文档操作会出故障**,或者日志刷屏、又无法用配置关掉。

**P2**:不阻断 GA,修复或写进 8.0.0 发版说明的 Known limits。有规避办法的问题、可以关掉的日志噪音、Dashboard 的显示问题、文档错误、低于性能目标但不影响使用。

## 5. 日常检查

**每天**,约 5 分钟,结果记一行到第 8 节的记录:

- 过去 24 小时触发过的告警,每一条都有解释。
- server 日志中的 ERROR,每一条都有解释:`kubectl logs -l app=cosky --since=24h --tail=-1 --prefix | grep ERROR`。
- Pod 重启次数:`kubectl get pods -l app=cosky`。
- 堆和 Redis 内存的趋势。日志受 `cosky.watch.window` 和 `window-bytes` 限制,涨到顶后应当持平,不应一直上涨。
- 应用一侧:健康指示器 `cosky` 没有停在 `connection: DEGRADED`;应用日志中 `me.ahoo.cosky` 的 WARN 和 ERROR 都有解释。

**每周**:

- `cosky_watch_gone_total` 按原因、`cosky_auth_failures_total` 按原因、写入 p99 和 `cosky_cache_lag_seconds` 的趋势。
- 抽查几个 Config:Dashboard 中的值与应用实际使用的一致。
- 抽查几个服务:Dashboard 中的实例与实际运行的进程一一对应,没有多也没有少。

## 6. 演练

`testing/failover` 中的 `measure.py` 停的是 Docker 容器,只能用于它自己启动的本机 Redis。在你们的环境中,用同目录的 `probe.py` 持续写入,用你们自己的方式制造故障。

### 6.1 准备探针

探针每 100 ms 通过 REST 写一次 Config,打印每种失败、何时恢复、失败了多久,Ctrl-C 后给出汇总。时间是运行它的机器的时钟,方便与 Redis 和 server 的日志对照。

给它一个单独的 namespace 和只能写这一个 Config 的账号。`$ADMIN` 是管理员 token,`$COSKY` 是 server 的 HTTP 地址:

```bash
H=(-H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json')
curl -X PUT "$COSKY/v1/namespaces/ga-trial" "${H[@]}" -d '{"namespace":{}}'
curl -X PUT "$COSKY/v1/roles/ga-trial-probe" "${H[@]}" \
  -d '{"role":{"description":"GA 试运行的探针","rules":[{"kinds":["KIND_CONFIG"],"namespaces":["ga-trial"],"verbs":["VERB_GET","VERB_PUT"],"names":["probe"]}]}}'
curl -X PUT "$COSKY/v1/serviceaccounts/ga-trial-probe" "${H[@]}" -d '{"serviceAccount":{"description":"GA 试运行的探针"}}'
curl -X PUT "$COSKY/v1/rolebindings/ga-trial-probe" "${H[@]}" \
  -d '{"roleBinding":{"role":"ga-trial-probe","subjects":[{"type":"TYPE_SERVICE_ACCOUNT","name":"ga-trial-probe"}],"namespaces":["ga-trial"]}}'
curl -X POST "$COSKY/v1/serviceaccounts/ga-trial-probe/tokens" "${H[@]}" -d '{}'
```

最后一步返回的 token 只显示这一次。然后:

```bash
COSKY_TOKEN=<token> testing/failover/probe.py "$COSKY"
```

在 Redis Cluster 上,一个 namespace 的数据都在同一个槽位。要让探针落在将被停掉的主节点上,在任一节点上执行 `CLUSTER KEYSLOT cosky:{ga-trial}:rev` 和 `CLUSTER NODES` 找出它的主节点;或者换一个 namespace 名,直到落在想要的节点上。

演练时同时盯住一两个真实应用:健康指示器、日志,以及它读到的 Config 和找到的实例。

### 6.2 演练清单

低风险的先做;涉及 Redis 的放在低峰期,按你们的变更流程审批。

| 演练 | 怎么做 | 预期 |
|---|---|---|
| **D1 滚动重启 server** | `kubectl rollout restart deployment/cosky` | 探针最多零星失败,几秒内恢复;应用不会停在 `DEGRADED`;实例数和 Config 不变;回收器的 Leader 换手后等 30 秒宽限,不会误删实例 |
| **D2 Redis 主动切换** | Sentinel:在一个 Sentinel 上执行 `SENTINEL FAILOVER <master>`;Cluster:在从节点上执行 `CLUSTER FAILOVER`;托管 Redis:控制台的主备切换 | 秒级。本机的 Sentinel 主动交接没有一次写入失败 |
| **D3 Redis 主节点故障** | 杀掉主节点的进程,或关掉它的主机;再做一次冻结:`kill -STOP <redis 进程>`,等 Redis 完成切换后 `kill -CONT` | 不可用的时间约为判定下线的时间(Sentinel 的 `down-after-milliseconds`、Cluster 的 `cluster-node-timeout`)加选举,Cluster 上再加最多 10 秒的拓扑读取。本机 5 秒超时下 rc.3 实测 9–16 秒。主节点被杀掉时写入立即以 `STORE_UNAVAILABLE` 失败,被冻结时 5 秒后失败;读取不受影响,应用保留注册;丢掉了租约的应用在 5 秒内重新注册。之后可能出现 `CoSkyRevisionRegression`:异步复制丢掉了最后的写入。Cluster 上冻结主节点时,在 node timeout 内所有 namespace 暂停回收(已知限制) |
| **D4 单个副本与 Redis 断开** | 给一个 server Pod 打上 `cosky-drill=isolated` 标签,应用下面的 NetworkPolicy;结束后删除它 | 这个副本仍然就绪(已知限制),它的客户端换到其他副本;3 分钟内 `CoSkyStoreFailing` 和 `CoSkyReplicaBehind` 触发;恢复后它追上,告警消失。其他副本不受影响 |
| **D5 应用退出与崩溃** | 正常退出:`kubectl delete pod <应用的 Pod>`;崩溃:`kubectl delete pod <应用的 Pod> --grace-period=0 --force` | 正常退出 3 秒内实例消失;崩溃时默认约 40 秒后消失(TTL 加三分之一)。调用方随之更新 |
| **D6 token 轮换**(用 `cosky.token-file` 时) | 为应用的账号创建新 token,写进 token 文件,再吊销旧 token | 应用不重启,`cosky_auth_failures_total{reason="invalid"}` 不涨 |

D4 用的 NetworkPolicy 只放行 DNS,其余出站都拦住。它要求 CNI 执行 NetworkPolicy;有的 CNI 不切断已经建立的连接,这时改在节点上用 iptables 丢弃到 Redis 端口的包。

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: cosky-drill-isolate
spec:
  podSelector:
    matchLabels:
      cosky-drill: isolated
  policyTypes: [Egress]
  egress:
    - ports:
        - { protocol: UDP, port: 53 }
        - { protocol: TCP, port: 53 }
```

每次演练记下:时间、做了什么、探针的汇总(失败次数、最长失败时间、p99)、触发了哪些告警、应用一侧看到了什么。与预期不符就按第 4 节定级。

不要在生产上演练大面积失联(让超过一半的应用同时断开,触发回收暂停);需要的话放在预发环境。

## 7. 规模与真实网络

路线图的验证表中,Redis Cluster 故障切换、Redis Sentinel 故障切换、Redis Cluster 大规模、真实网络四项都只在本机模拟过,写着“维护者环境再验”。试运行能验证你们用到的那几项:用 Sentinel 就验证 Sentinel 的故障切换(D2、D3),用 Cluster 就验证 Cluster 的故障切换和规模。没验证的那些,8.0.0 的发版说明照旧写进 Known limits。

试运行开始和结束时各记一次:

| 项 | 怎么取 |
|---|---|
| namespace 数 | `count(count by (namespace) (cosky_cache_revision))` |
| 租约数 | `sum(max by (namespace) (cosky_lease_active))`:每个进程在它注册的每个 namespace 中各一个 |
| 实例数、Config 数 | Dashboard |
| 每个副本的 watch 数 | `cosky_watch_active` |
| 写入速率 | `sum(rate(cosky_store_apply_seconds_count[5m]))` |
| 写入 p99 | `histogram_quantile(0.99, sum by (le) (rate(cosky_store_apply_seconds_bucket[5m])))`,目标 20 ms |
| 写入到达副本的耗时 | `max by (namespace) (cosky_cache_lag_seconds)` |
| 事件投递速率 | `sum(rate(cosky_watch_events_sent_total[5m]))` |
| 资源 | 每个副本的堆、CPU 和 GC 停顿;Redis 的 CPU、内存和网络 |
| 网络 | server 到 Redis 的延迟(`redis-cli --latency`),应用到 server 的往返时间 |

与[性能](https://cosky.ahoo.me/zh/guide/performance#结果)一页的目标和本机数字对比:写入 p99 低于 20 ms,传播 p99 低于 500 ms;单个 Redis 上 5,000 个 namespace、三个副本时空闲 CPU 约 35%。结束后把结果补进路线图的验证表和性能页。

## 8. 记录与报告

记录放在你们自己的地方,每天一行,演练和问题另起一行:

| 日期 | 版本 | 事件或演练 | 结果 | 级别 | issue |
|---|---|---|---|---|---|
| 11-27 | rc.3 | 日常检查 | 无告警,无 ERROR | | |
| 11-28 | rc.3 | D3 Sentinel 主节点被杀 | 探针:失败 _ 次,最长 _ 秒;应用 `DEGRADED` _ 秒 | | |

需要修的问题用 [bug report](https://github.com/Ahoo-Wang/CoSky/issues/new/choose) 提 issue,标题以 `[P0]`、`[P1]` 或 `[P2]` 开头,写明:

- server 和应用的 CoSky 版本;
- Redis 的版本和拓扑(单机、Sentinel 或 Cluster),是否托管;
- 发生的时间,以及前后的 server 日志、应用日志、相关指标的截图,有探针的话附上它的输出;
- 怎样重现,或者当时在做哪个演练。

贴日志前去掉 token、密码和内部地址。

## 9. 通过条件

- [ ] 同一个 RC 在真实流量下跑满 14 天,或者按第 1 节的规则。
- [ ] 没有未关闭的 P0 或 P1。
- [ ] 每个 P2 已修复,或者写进了 Known limits。
- [ ] D1–D5 都做过,结果记录在案;用到的 Redis 拓扑做过 D2 和 D3。D6 在用 token 文件时做过。
- [ ] 能演练出来的告警都在演练中触发过:D3、D4 覆盖 `CoSkyStoreFailing`、`CoSkyReplicaBehind` 和 `CoSkyReaperFailing`。
- [ ] 第 7 节的规模与网络数字已记录,并补进了路线图和性能页。
- [ ] 维护者确认后,准备 8.0.0 的发布。
