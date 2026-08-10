package com.refreshrate.control.util

import com.refreshrate.control.core.SwitchGeneration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 全局唯一的刷新率切换协调器。
 *
 * 手动切换(PlatformBridge)、无障碍服务 apply/restore、post-restore watchdog
 * 全部经过同一个串行执行器与同一个取消代号(SwitchGeneration)运行:
 * - 串行执行:任何时刻只有一个切换任务在写 RootUtils,避免并发写不同目标。
 * - 单一取消代号:新请求必定淘汰旧请求,排队中的旧任务在写入前检查代号即被丢弃。
 */
object RefreshSwitchCoordinator {
    private val generation = SwitchGeneration()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "refresh-switch").apply { isDaemon = true }
    }

    /** 当前代号。 */
    fun currentGeneration(): Long = generation.currentValue()

    /** 递增代号并返回新代号(淘汰旧请求)。 */
    fun nextGeneration(): Long = generation.next()

    /** 是否仍是最新代号(未被新请求淘汰)。 */
    fun isCurrent(gen: Long): Boolean = generation.isCurrent(gen)

    /**
     * 提交一个切换任务。先递增代号(淘汰排队/运行中的旧任务),再串行执行。
     * block 内通过 [isCancelled] 检查本任务是否已被更新的请求淘汰;
     * 若任务在排队期间即被淘汰,直接跳过不执行。
     */
    fun submit(name: String, block: (generation: Long, isCancelled: (Long) -> Boolean) -> Unit) {
        val gen = generation.next()
        executor.execute {
            if (!generation.isCurrent(gen)) {
                RuntimeLog.appendGlobal("SwitchCoordinator", "task=$name stale skipped gen=$gen")
                return@execute
            }
            try {
                block(gen) { g -> !generation.isCurrent(g) }
            } catch (e: Exception) {
                RuntimeLog.appendGlobal("SwitchCoordinator", "task=$name gen=$gen failed=${e.message}")
            }
        }
    }

    /**
     * 串行执行但不递增取消代号(watchdog 类辅助任务使用):
     * 若提交时已有更新的请求,本任务直接跳过;执行期间若新请求到来,同样被淘汰。
     * 不会淘汰队列中/运行中的其他任务。
     */
    fun submitWithoutBump(name: String, block: (generation: Long, isCancelled: (Long) -> Boolean) -> Unit) {
        val gen = generation.currentValue()
        executor.execute {
            if (!generation.isCurrent(gen)) {
                RuntimeLog.appendGlobal("SwitchCoordinator", "task=$name stale skipped gen=$gen")
                return@execute
            }
            try {
                block(gen) { g -> !generation.isCurrent(g) }
            } catch (e: Exception) {
                RuntimeLog.appendGlobal("SwitchCoordinator", "task=$name gen=$gen failed=${e.message}")
            }
        }
    }

    /** 可取消的 sleep:在等待期间定期检查取消标志。返回 false 表示被取消/中断。 */
    fun sleepMillis(ms: Long, isCancelled: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < deadline) {
            if (isCancelled()) return false
            try {
                Thread.sleep(minOf(100L, deadline - System.currentTimeMillis()))
            } catch (e: InterruptedException) {
                return false
            }
        }
        return !isCancelled()
    }
}
