# XyForgeCrafting 1.0.6

XyForgeCrafting 是XyPlugins RPG服务器的事务型锻造插件，**只支持Java 8与Paper/Spigot 1.12.2**。

当前界面使用原版Bukkit 54格箱子GUI，不依赖DragonCore。插件强依赖XyCore和XyItems，可选接入XySoulSpace；材料可由MythicMobs、XyItems和原版Minecraft物品共同组成。

## 依赖与安装

推荐版本：

- Paper 1.12.2 build 1620
- Java 8
- XyCore 0.3.10
- XyItems 1.0.4
- XySoulSpace 1.1.1，可选
- Vault和一个经济插件；配方金币为0时可以不使用经济
- MythicMobs 4.11，可选；仅在配方引用 `mythicmobs:` 材料时需要

安装顺序：

1. 将 `XyCore-0.3.10.jar`、`XyItems-1.0.4.jar` 和 `XyForgeCrafting-1.0.6.jar` 放入 `plugins/`。
2. 需要读取灵魂仓库时同时安装 `XySoulSpace-1.1.1.jar`。
3. 完整重启服务器，不使用Bukkit `/reload`。
4. 插件生成 `plugins/XyForgeCrafting/config.yml` 和 `ForgeRecipe/Example.yml`。
5. 先确认XyItems成品ID与材料ID存在，再使用 `/xyfc reload`。

## 玩家流程

1. 玩家输入 `/xyfc open` 打开锻造台。
2. 唯一的图纸槽只接受XyForgeCrafting生成并写入固定隐藏NBT身份的图纸。
3. 放入有效图纸后，GUI显示材料拥有量、金币、全部非零最终概率和成品预览。
4. 材料默认统计 `灵魂仓库 + 主背包36格`，默认优先扣灵魂仓库，再扣背包。
5. 点击开始后播放原版GUI绿色轨迹动画，结束时重新验证并执行事务。
6. 成功时XyItems一次决定品质并立即生成随机属性；失败时按图纸配置处理图纸、材料和金币退款。

非图纸物品会被拒绝，并提示“当前放入的不是图纸”。GUI顶层除图纸槽外全部锁定；图纸槽也由插件托管，不允许客户端直接移动展示物品。

## 命令与权限

| 命令 | 说明 | 权限 |
| --- | --- | --- |
| `/xyfc open` | 打开锻造台 | `xyforgecrafting.use` |
| `/xyfc get <配方ID> [数量]` | 玩家为自己取得锻造图纸，数量默认1、最大64 | `xyforgecrafting.get` |
| `/xyfc give <玩家> <配方ID> [数量]` | 给予指定在线玩家锻造图纸 | `xyforgecrafting.give` |
| `/xyfc list` | 查看已加载配方ID | `xyforgecrafting.list` |
| `/xyfc reload` | 全量校验后安全重载 | `xyforgecrafting.reload` |
| `/xyfc help` | 查看帮助 | 无 |

主命令别名为 `/xyforge` 和 `/xyforgecrafting`。

`xyforgecrafting.get` 默认只授予OP，避免普通玩家随意生成图纸；如需提供给管理组或测试组，请通过权限插件单独授权。`get` 与 `give` 都根据配方ID生成带固定隐藏NBT身份的真实图纸，不能用普通物品名称或Lore伪造。

## GUI配置

配置文件：

```text
plugins/XyForgeCrafting/config.yml
```

布局由1到6行、每行正好9个字符组成：

```yaml
gui:
  title: '&8锻造台'
  layout:
    - '111111111'
    - '141333331'
    - '111333331'
    - '111111111'
    - '151612171'
    - '111111111'
  cons:
    '1':
      type: BACKGROUND
      display:
        material: STAINED_GLASS_PANE
        data: 15
        name: '&8锻造台边框'
```

组件类型：

| 类型 | 功能 |
| --- | --- |
| `BACKGROUND` | 锁定背景，也是火焰动画路径 |
| `FORGE_REQUIREMENTS` | 按布局槽位从上到下、从左到右显示材料 |
| `FORGE_BLUEPRINT` | 唯一真实图纸输入；必须且只能配置一个 |
| `FORGE_START` | 显示成功/失败概率和金币，点击开始 |
| `FORGE_PROBABILITY` | 在一个物品的Lore中汇总失败与全部非零结果概率 |
| `FORGE_RESULT` | 显示XyItems成品预览 |
| `CLOSE` | 关闭界面并安全归还未使用图纸 |

推荐使用上面的 `gui.layout + gui.cons` 结构。为兼容最初设计稿，也可以把布局直接写成顶层 `gui:` 列表，并把 `cons:` 放在顶层；两种结构不能在同一文件混用。

每个 `cons` 组件都必须显式填写 `type`。漏写或写错时，配置重载会失败并继续保留当前正在使用的安全快照，不会把该组件默认为背景槽。

所有 `material` 必须使用Bukkit 1.12.2 Material名称。配置不支持1.13扁平化材质名、RGB颜色或现代Paper组件。

如果材料种类超过布局提供的 `FORGE_REQUIREMENTS` 槽位，插件会显示已有内容但拒绝开始锻造。概率只需要至少一个 `FORGE_PROBABILITY` 槽位；配置多个时，每个槽位显示相同的完整概率Lore。

动画配置使用三套内置轻量路径，只会修改 `BACKGROUND` 槽位：

```yaml
animation:
  enabled: true
  active-preset: BORDER_CONVERGE
  interval-ticks: 1
  loops: 1
  head:
    material: STAINED_GLASS_PANE
    data: 5
    name: '&a锻造之火'
  trail:
    material: STAINED_GLASS_PANE
    data: 13
    name: '&2锻造余焰'
```

`active-preset` 可选：

- `BORDER_CONVERGE`：两路沿边框推进，最后汇合到右下角。
- `BOTTOM_SWEEP`：底部边框从左向右推进。
- `DOUBLE_SWEEP`：顶部和底部边框同时从左向右推进。

## 配方配置

配方目录：

```text
plugins/XyForgeCrafting/ForgeRecipe/
```

插件递归读取 `.yml` 和 `.yaml`。一个文件对应一个 `recipe` 根节点：

```yaml
recipe:
  enabled: true
  id: 'chumo_zhifeng'
  blueprint:
    material: 'minecraft:GLOWSTONE_DUST'
    name: '&a初墨之锋锻造图'
    lore:
      - '&e这是初墨之锋的锻造图纸。'
      - '&e打开锻造界面后可以使用。'
  result:
    item: 'xyitems:chumo_zhifeng'
    amount: 1
  requirements:
    'mythicmobs:ForgingCrystal': 1
    'minecraft:IRON_INGOT': 16
    'xyitems:forge_crystal': 8
  money: 1000
  failure:
    blueprint: DESTROY
    refund-materials: 50
    refund-money: 0
    message: '&c锻造失败，返还了部分材料。'
  success-commands:
    - 'console:say 玩家 %player_name% 锻造成功了 %result_name_plain%，让我们恭喜他！'
```

字段说明：

- `id`：配方唯一ID，也写入图纸隐藏身份。发布后不要随意修改。
- `blueprint.material`：生成图纸时的基础物品完整ID；不写前缀时默认按 `minecraft:` 处理。
- `blueprint.name/lore`：图纸玩家可见外观，不参与真伪判断。
- `result.item`：当前固定使用 `xyitems:<成品ID>`。
- `result.amount`：成功后交付数量，建议明确写出。
- `requirements`：完整物品库ID到数量的简单映射，无需重复配置来源和扣除顺序。
- `money`：当前默认使用 `VAULT`；`0` 表示不收费。
- `success-commands`：成功交付成品后执行，可用 `console:` 或 `player:` 前缀。
- `failure.blueprint`：`DESTROY`销毁图纸，`RETURN`保留图纸。
- 退款百分比必须为0到100，物品按实际扣除记录向下取整返还。

成功交付成品是插件固定核心流程，不需要配置 `DELIVER_RESULT`。成功图纸固定消耗；失败图纸按照 `failure.blueprint` 处理。

旧配置中的 `blueprint.template/display-name`、`economy.amount` 与 `outcomes.*` 仍然兼容，但默认示例和后续文档推荐使用短格式。

命令占位符：

- `%player_name%`
- `%result_name%`，包含颜色
- `%result_name_plain%`，去除颜色
- `%recipe_id%`

## 失败与品质概率

配方文件不重复配置成功率。最终概率只从XyItems成品读取：

```yaml
forge:
  failure:
    weight: 30
    name: '锻造失败'
    color: '&c'
```

它与 `identify.qualities` 下六个品质权重组成一次抽取。GUI展示的失败概率和六品质概率与实际结算使用同一份XyItems快照；成功后返回值已经携带确定品质和随机属性的物品，不会二次抽品质。

概率Lore会忽略最终概率为0的结果。例如XyItems只配置“传说”一个品质并设置 `forge.failure.weight: 0` 时，只显示：

```text
传说: 100%
```

失败概率大于0时才显示“失败几率”，其他结果按照XyItems品质配置顺序逐行显示。概率槽数量不再限制可配置的结果数量。

普通右键鉴定由XyItems处理，会忽略 `forge.failure`，因此不会因为锻造失败权重而鉴定失败。

## 锻造者与成功时间

锻造成功后，XyForgeCrafting在XyItems最终成品Lore末尾追加锻造记录。若原Lore最后一行只是分隔线，会先删除该行，再加入：

```text
------------[ 锻造 ]--------------
锻造者：XiYouuuuu
锻造时间：2026-07-30 23:36:18
----------------------------------
```

全局格式位于 `config.yml`：

```yaml
forge-record:
  enabled: true
  timezone: 'Asia/Shanghai'
  time-format: 'yyyy-MM-dd HH:mm:ss'
  replace-last-separator: true
  lore:
    - '&7------------[ &c锻造&7 ]--------------'
    - '&e锻造者：&7%player_name%'
    - '&e锻造时间：&7%forge_time%'
    - '&7----------------------------------'
```

可用占位符为 `%player_name%`、`%forge_time%` 和 `%recipe_id%`。旧版 `config.yml` 没有 `forge-record` 时会自动使用以上默认值，不需要删除已有配置。

除可见Lore外，成品还通过XyCore写入锻造者UUID、锻造者当时的名字、成功时间戳和配方ID隐藏NBT。普通XyItems鉴定或命令获取的物品不会带锻造记录。

## 图纸安全

图纸通过XyCore NBT服务写入：

```text
xyforge-blueprint-id
xyforge-blueprint-schema
```

插件只检查隐藏的配方ID与schema版本，不检查图纸可见名称和Lore。服务器已经禁止铁砧改名时，这种固定NBT身份足够轻量，也不会因为 `blueprint-secret.key` 丢失或重装插件导致旧图纸失效。

从1.0.6开始，插件不会生成或依赖 `plugins/XyForgeCrafting/blueprint-secret.key`。1.0.5及更早图纸上可能残留 `xyforge-blueprint-signature`，新版会直接忽略该旧字段；只要 `xyforge-blueprint-id` 与 `xyforge-blueprint-schema` 正确且对应配方仍启用，旧图纸仍可继续使用。

`version`、`validation`、`gui.display-source` 等实现字段不需要写进配方。

## 材料匹配与事务

完整ID示例：

- `minecraft:IRON_INGOT`
- `mythicmobs:ForgingCrystal`
- `xyitems:forge_crystal`

XyCore按隐藏身份识别XyItems与MythicMobs物品。匹配 `minecraft:` 时会排除已经具有自定义物品身份的同材质物品，避免把RPG道具误扣为原版材料。

一次锻造按以下顺序执行：

1. 检查概率配置、GUI展示容量、结果背包空格、金币和全部材料。
2. 计算灵魂仓库优先、背包补足的材料计划。
3. 扣除金币、原子扣除灵魂仓库、再原子扣除主背包材料。
4. 调用XyItems进行一次最终抽取。
5. 成功时原子交付成品；失败时按配置退款。
6. 任一中间步骤异常时执行100%回滚并保留图纸。

扫描范围只包括玩家主背包36格，不包括盔甲和副手。

## 图纸归还与待领取

关闭GUI、掉线、插件安全重载或服务器停服时，未使用图纸会先尝试放回玩家背包。空间不足时写入：

```text
plugins/XyForgeCrafting/pending-returns.yml
```

玩家下次进入服务器时自动再次领取。退款物品遇到极端背包不足时也使用同一安全队列，不会主动丢到地上。

## 安全重载

使用：

```text
/xyfc reload
```

插件先将新GUI和全部配方加载到候选快照，并验证图纸模板、每项材料、XyItems成品及其锻造概率都能被当前物品库读取。只有所有内容通过校验时才替换当前配置；任一文件错误时返回失败并继续使用旧快照。成功替换前会关闭现有锻造界面并归还图纸。

XyForgeCrafting也注册到XyCore重载管理器，可由 `/xycore reload` 调用。不要使用Bukkit `/reload` 或第三方热卸载替代完整重启。

## 构建

项目使用Gradle Wrapper，不使用Maven：

```powershell
.\gradlew.bat clean build --no-daemon
```

输出：

```text
build/libs/XyForgeCrafting-1.0.6.jar
```

编译目标固定为Java 8，仓库内附Paper 1.12.2编译期API。最终JAR不会打入Paper、XyCore或XyItems类。

## FishForgeCrafting参考边界

开发前审查了用户提供的FishForgeCrafting框架。新插件吸收了InventoryHolder隔离、关闭归还、权重结果和命令占位符等通用设计经验，但源码为针对XyPlugins接口重新实现。

没有引入框架中的1.7.10硬编码NMS、跨版本材质兼容、数据库/JavaScript系统、ItemsAdder、NBTAPI、Rhino、现代Paper API或其他与本服1.12.2锻造流程无关的依赖。
