package com.keepaccounts.data.local.db

import androidx.room.withTransaction
import com.keepaccounts.core.Palette

/**
 * 首次安装写入的种子数据：26 个内置分类 + 1 个默认账户 + 默认设置项。
 *
 * 挂在 Application 启动时调用，用 `app_settings.seeded` 做幂等标记。
 * 放在这里而不是 RoomDatabase.Callback.onCreate，是为了避开回调里拿不到
 * suspend DAO 的问题 —— onCreate 在数据库首次创建的线程上同步执行，
 * 往里塞协程容易死在「数据库还没 open 完」。
 */
object SeedData {

    private data class SeedCategory(val name: String, val type: String, val icon: String)

    private val expenseCategories = listOf(
        SeedCategory("餐饮", CategoryType.EXPENSE, "food"),
        SeedCategory("交通", CategoryType.EXPENSE, "transport"),
        SeedCategory("购物", CategoryType.EXPENSE, "shopping"),
        SeedCategory("居住", CategoryType.EXPENSE, "home"),
        SeedCategory("通讯", CategoryType.EXPENSE, "phone"),
        SeedCategory("娱乐", CategoryType.EXPENSE, "fun"),
        SeedCategory("医疗", CategoryType.EXPENSE, "medical"),
        SeedCategory("教育", CategoryType.EXPENSE, "study"),
        SeedCategory("人情", CategoryType.EXPENSE, "gift"),
        SeedCategory("宠物", CategoryType.EXPENSE, "pet"),
        SeedCategory("旅行", CategoryType.EXPENSE, "travel"),
        SeedCategory("数码", CategoryType.EXPENSE, "digital"),
        SeedCategory("服饰", CategoryType.EXPENSE, "cloth"),
        SeedCategory("美容", CategoryType.EXPENSE, "beauty"),
        SeedCategory("运动", CategoryType.EXPENSE, "sport"),
        SeedCategory("烟酒", CategoryType.EXPENSE, "drink"),
        SeedCategory("母婴", CategoryType.EXPENSE, "baby"),
        SeedCategory("保险", CategoryType.EXPENSE, "insurance"),
        SeedCategory("税费", CategoryType.EXPENSE, "tax"),
        SeedCategory("其他", CategoryType.EXPENSE, "more")
    )

    private val incomeCategories = listOf(
        SeedCategory("工资", CategoryType.INCOME, "salary"),
        SeedCategory("奖金", CategoryType.INCOME, "bonus"),
        SeedCategory("红包", CategoryType.INCOME, "redpacket"),
        SeedCategory("投资收益", CategoryType.INCOME, "invest"),
        SeedCategory("报销", CategoryType.INCOME, "reimburse"),
        SeedCategory("退款", CategoryType.INCOME, "refund")
    )

    suspend fun ensure(db: AppDatabase) {
        val settings = db.settingsDao()
        if (settings.get(SettingKeys.SEEDED) != null) return

        db.withTransaction {
            val now = System.currentTimeMillis()
            val existing = db.categoryDao().count()
            if (existing == 0) {
                val all = expenseCategories.mapIndexed { i, c ->
                    c to Palette.CATEGORY[i % Palette.CATEGORY.size]
                } + incomeCategories.mapIndexed { i, c ->
                    c to Palette.CATEGORY[(i + expenseCategories.size) % Palette.CATEGORY.size]
                }
                db.categoryDao().insertAll(
                    all.mapIndexed { index, (seed, color) ->
                        CategoryEntity(
                            name = seed.name,
                            type = seed.type,
                            icon = seed.icon,
                            color = color,
                            sortOrder = index,
                            createdAt = now
                        )
                    }
                )
            }

            if (db.accountDao().countActive() == 0) {
                db.accountDao().insert(
                    AccountEntity(
                        name = "现金",
                        type = AccountType.CASH,
                        initialBalance = 0,
                        currentBalance = 0,
                        icon = "cash",
                        color = Palette.CATEGORY[3],
                        sortOrder = 0,
                        createdAt = now
                    )
                )
            }

            settings.put(AppSettingEntity(SettingKeys.RECYCLE_RETENTION_DAYS, "7"))
            settings.put(AppSettingEntity(SettingKeys.AUTO_BACKUP_ENABLED, "false"))
            settings.put(AppSettingEntity(SettingKeys.AUTO_BACKUP_INTERVAL_DAYS, "7"))
            settings.put(AppSettingEntity(SettingKeys.FEEDBACK_LEVEL, "all"))
            settings.put(AppSettingEntity(SettingKeys.SEEDED, now.toString()))
        }
    }
}
