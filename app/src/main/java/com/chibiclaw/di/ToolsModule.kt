package com.chibiclaw.di

import com.chibiclaw.agent.tools.Tool
import com.chibiclaw.agent.tools.impl.A11yClickTool
import com.chibiclaw.agent.tools.impl.A11yDescribeScreenTool
import com.chibiclaw.agent.tools.impl.A11yScrollTool
import com.chibiclaw.agent.tools.impl.A11yTypeTool
import com.chibiclaw.agent.tools.impl.AwaitUserTool
import com.chibiclaw.agent.tools.impl.EscalateToolHandler
import com.chibiclaw.agent.tools.impl.IntentOpenTool
import com.chibiclaw.agent.tools.impl.IntentSendTool
import com.chibiclaw.agent.tools.impl.MemoryRecallTool
import com.chibiclaw.agent.tools.impl.MemoryRememberTool
import com.chibiclaw.agent.tools.impl.MessagingTool
import com.chibiclaw.agent.tools.impl.ShizukuExecTool
import com.chibiclaw.agent.tools.impl.ShizukuForceStopTool
import com.chibiclaw.agent.tools.impl.ShizukuGrantPermissionTool
import com.chibiclaw.agent.tools.impl.SystemActionTool
import com.chibiclaw.agent.tools.impl.WaitTool
import com.chibiclaw.agent.tools.impl.WorldGetNotificationsTool
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey

/**
 * Tools binding @IntoMap.
 *
 * Phase 1: wait, await_user, intent_open, system_action, memory_remember, memory_recall.
 * Phase 3: a11y_click, a11y_type, a11y_describe_screen, a11y_scroll,
 *          shizuku_exec, shizuku_force_stop, shizuku_grant_permission,
 *          messaging, intent_send, world_get_notifications.
 * Phase 4: escalate_to_cloud (TBD).
 * Phase 5: vision_tap, vision_describe, vision_extract_text,
 *          world_get_installed_apps, world_get_location, world_get_schedule (TBD).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ToolsModule {

    // ── Phase 1 ─────────────────────────────────────────────────────────────
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

    // ── Phase 3: Accessibility ──────────────────────────────────────────────
    @Binds @IntoMap @StringKey("a11y_click")
    abstract fun bindA11yClickTool(impl: A11yClickTool): Tool

    @Binds @IntoMap @StringKey("a11y_type")
    abstract fun bindA11yTypeTool(impl: A11yTypeTool): Tool

    @Binds @IntoMap @StringKey("a11y_describe_screen")
    abstract fun bindA11yDescribeScreenTool(impl: A11yDescribeScreenTool): Tool

    @Binds @IntoMap @StringKey("a11y_scroll")
    abstract fun bindA11yScrollTool(impl: A11yScrollTool): Tool

    // ── Phase 3: Shizuku privileged ─────────────────────────────────────────
    @Binds @IntoMap @StringKey("shizuku_exec")
    abstract fun bindShizukuExecTool(impl: ShizukuExecTool): Tool

    @Binds @IntoMap @StringKey("shizuku_force_stop")
    abstract fun bindShizukuForceStopTool(impl: ShizukuForceStopTool): Tool

    @Binds @IntoMap @StringKey("shizuku_grant_permission")
    abstract fun bindShizukuGrantPermissionTool(impl: ShizukuGrantPermissionTool): Tool

    // ── Phase 3: World / Messaging ──────────────────────────────────────────
    @Binds @IntoMap @StringKey("messaging")
    abstract fun bindMessagingTool(impl: MessagingTool): Tool

    @Binds @IntoMap @StringKey("intent_send")
    abstract fun bindIntentSendTool(impl: IntentSendTool): Tool

    @Binds @IntoMap @StringKey("world_get_notifications")
    abstract fun bindWorldGetNotificationsTool(impl: WorldGetNotificationsTool): Tool

    // ── Phase 4: Cloud escalation ───────────────────────────────────────────
    @Binds @IntoMap @StringKey("escalate_to_cloud")
    abstract fun bindEscalateToolHandler(impl: EscalateToolHandler): Tool
}
