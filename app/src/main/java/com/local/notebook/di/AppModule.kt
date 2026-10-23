package com.local.notebook.di

import android.content.Context
import androidx.room.Room
import com.local.notebook.data.local.AccountDao
import com.local.notebook.data.local.BudgetDao
import com.local.notebook.data.local.CategoryDao
import com.local.notebook.data.local.LedgerDatabase
import com.local.notebook.data.local.NotificationRawDao
import com.local.notebook.data.local.RuleDao
import com.local.notebook.data.local.TxnDao
import com.local.notebook.data.local.TxnEditLogDao
import com.local.notebook.data.repository.LedgerRepository
import com.local.notebook.data.repository.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LedgerDatabase =
        Room.databaseBuilder(context, LedgerDatabase::class.java, "notiledger.db").build()

    @Provides
    fun provideAccountDao(db: LedgerDatabase): AccountDao = db.accountDao()

    @Provides
    fun provideCategoryDao(db: LedgerDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideTxnDao(db: LedgerDatabase): TxnDao = db.txnDao()

    @Provides
    fun provideTxnEditLogDao(db: LedgerDatabase): TxnEditLogDao = db.txnEditLogDao()

    @Provides
    fun provideBudgetDao(db: LedgerDatabase): BudgetDao = db.budgetDao()

    @Provides
    fun provideNotificationRawDao(db: LedgerDatabase): NotificationRawDao = db.notificationRawDao()

    @Provides
    fun provideRuleDao(db: LedgerDatabase): RuleDao = db.ruleDao()

    @Provides
    @Singleton
    fun provideLedgerRepository(
        db: LedgerDatabase,
        settings: SettingsRepository
    ): LedgerRepository = LedgerRepository(db, settings)
}
