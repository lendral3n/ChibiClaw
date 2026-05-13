package com.chibiclaw.di

import com.chibiclaw.agent.tools.Tool
import com.chibiclaw.agent.tools.impl.AwaitUserTool
import com.chibiclaw.agent.tools.impl.IntentOpenTool
import com.chibiclaw.agent.tools.impl.MemoryRecallTool
import com.chibiclaw.agent.tools.impl.MemoryRememberTool
import com.chibiclaw.agent.tools.impl.SystemActionTool
import com.chibiclaw.agent.tools.impl.WaitTool
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey

/**
 * Phase 1 tools binding @IntoMap.
 *
 * Phase 3 akan tambah: a11y_click, a11y_type, a11y_describe_screen, a11y_scroll,
 *                      shizuku_exec, shizuku_force_stop, shizuku_grant_permission,
 *                      messaging, intent_send, world_get_notifications.
 * Phase 4: escalate_to_cloud.
 * Phase 5: vision_tap, vision_describe, vision_extract_text,
 *           world_get_installed_apps, world_get_location, world_get_schedule.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ToolsModule {

    @Binds @IntoMap @StringKey("wait")
    abstract fun bindWaitTool(impl: WaitTool): Tool

    @Binds @IntoMap @StringKey("await_user")
    abstract fun bindAwaitUserTool(impl: AwaitUserTool): Tool

    @Binds @IntoMap @StringKey("intent_open")
    abstract fun bindIntentOpenTool(impl: IntentOpenTool): Tool

    @Binds @IntoMap @StringKey("system_action")
    abstract fun bindSystemActionTool(impl: SystemActionTool): Tool

    @Binds @IntoMap @StringKey("memory_remember")
    abstract fun bindMemoryRememberTool(impl: MemoryRememberTool): Tool

    @Binds @IntoMap @StringKey("memory_recall")
    abstract fun bindMemoryRecallTool(impl: MemoryRecallTool): Tool
}
