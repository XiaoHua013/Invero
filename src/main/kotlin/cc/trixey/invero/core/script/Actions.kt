package cc.trixey.invero.core.script

import cc.trixey.invero.core.Context
import cc.trixey.invero.core.compat.bungeecord.Bungees
import cc.trixey.invero.core.compat.eco.HookPlayerPoints
import cc.trixey.invero.core.script.loader.InveroKetherParser
import cc.trixey.invero.common.message.translateFormattedMessage
import org.bukkit.entity.Player
import taboolib.common5.cdouble
import taboolib.common5.cint
import taboolib.module.kether.*
import taboolib.platform.compat.depositBalance
import taboolib.platform.compat.getBalance
import taboolib.platform.compat.withdrawBalance
import taboolib.platform.util.onlinePlayers
import java.util.concurrent.CompletableFuture

/**
 * Invero
 * cc.trixey.invero.core.script.Actions
 *
 * @author Arasple
 * @since 2023/1/24 22:51
 */
@InveroKetherParser(["parse"])
internal fun actionParser() = scriptParser {
    val str = it.nextParsedAction()
    actionTake {
        run(str).str { s ->
            val context = contextVar<Context?>("@context")?.variables ?: variables().toMap()
            val player = player()

            s.translateFormattedMessage(player, context)
        }
    }
}

/*
connect <serverName> for <playerName>
 */
@InveroKetherParser(["connect", "bungee"])
internal fun actionConnect() = combinationParser {
    it.group(
        text(),
        command("for", then = action()).option().defaultsTo(null)
    ).apply(it) { server, player ->
        future {
            if (player == null) {
                (session()?.viewer?.get<Player>()
                    ?: player()).let { player -> CompletableFuture.completedFuture(Bungees.connect(player, server)) }
                    ?: CompletableFuture.completedFuture(null)
            } else {
                newFrame(player).run<Any>().thenApply { playerId ->
                    onlinePlayers
                        .find { p -> p.name == playerId }
                        ?.let { p -> Bungees.connect(p, server) }
                }
            }
        }
    }
}

/**
 * eco
 * eco get
 * eco take 200
 * eco give 200
 * eco set 200
 */
@InveroKetherParser(["eco", "money", "vault"])
internal fun actionEco() = scriptParser {
    if (!it.hasNext()) actionNow { player().getBalance() }
    else {
        val method = it.nextToken()
        if (method == null || method == "get" || method == "balance") actionNow { player().getBalance() }
        else {
            val parsedAction = it.nextParsedAction()
            actionNow {
                val amount = newFrame(parsedAction).run<String>().getNow("0").cdouble
                when (method) {
                    "has" -> player().getBalance() >= amount
                    "take" -> player().withdrawBalance(amount)
                    "give" -> player().depositBalance(amount)
                    "set" -> {
                        val currentBalance = player().getBalance()
                        if (currentBalance > amount) {
                            player().withdrawBalance(currentBalance - amount)
                        } else if (currentBalance < amount) {
                            player().depositBalance(amount - currentBalance)
                        }
                        true
                    }
                    else -> error("Unknown eco method: $method")
                }
            }
        }
    }
}

@InveroKetherParser(["playerpoints", "points", "point"])
internal fun actionPoints() = scriptParser {
    if (!it.hasNext()) actionNow { HookPlayerPoints.look(player()) }
    else {
        val method = it.nextToken()
        if (method == null || method == "get" || method == "balance") actionNow {
            HookPlayerPoints.look(player()) ?: -1
        }
        else {
            val parsedAction = it.nextParsedAction()
            actionNow {
                val amount = newFrame(parsedAction).run<String>().getNow("0").cint
                when (method) {
                    "has" -> (HookPlayerPoints.look(player()) ?: 0) >= amount
                    "take" -> HookPlayerPoints.take(player(), amount)
                    "give" -> HookPlayerPoints.add(player(), amount)
                    "set" -> {
                        val currentPoints = HookPlayerPoints.look(player()) ?: 0
                        if (currentPoints > amount) {
                            HookPlayerPoints.take(player(), currentPoints - amount)
                        } else if (currentPoints < amount) {
                            HookPlayerPoints.add(player(), amount - currentPoints)
                        }
                        true
                    }
                    else -> error("Unknown eco method: $method")
                }
            }
        }
    }
}