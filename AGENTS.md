# XyForgeCrafting AI / Codex维护手册

修改前必须阅读README、CHANGELOG、AI_USAGE、默认config.yml、默认ForgeRecipe和目标源码。

项目边界：

- 只支持Java 8与Paper/Spigot 1.12.2。
- 构建命令：`gradlew.bat clean build --no-daemon`。
- 强依赖XyCore与XyItems，可选XySoulSpace。
- 当前GUI只使用Bukkit Inventory，不加入DragonCore依赖。
- 不加入1.7.10 NMS、1.13+ Material、Adventure、Folia、ItemsAdder、NBTAPI、Rhino或跨版本兼容层。

核心安全约定：

- 图纸真伪只使用XyCore固定隐藏NBT身份，不按名称和Lore判断，也不再依赖本地签名密钥。
- 顶层GUI只有图纸槽代表真实输入，服务端ForgeSession是最终状态来源。
- 所有物品匹配必须调用XyCore `ItemLibraryService#matches`。
- 灵魂仓库必须使用XySoulSpace原子批量接口，禁止直接读取玩家YML。
- 抽取只调用一次XyItems `rollForgeOutcome`，禁止成功后再次随机品质。
- 任何事务异常必须退款并保留图纸；成功交付不配置为可省略动作。
- 关闭、掉线、重载、停服必须归还图纸；背包满进入pending-returns.yml。
- 配置重载必须先全量校验，新配置失败时保留当前快照。

交付前同步更新README、CHANGELOG、AI_USAGE和版本号，并完成四插件联合构建与JAR内容复核。
