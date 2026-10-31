package com.agentmod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.item.Item;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** /agent mode <follow|stay|collect>  |  /agent fetch <item> [qtd]  |  /agent give */
public class AgentCommands {
    public static void register(CommandDispatcher<ServerCommandSource> d, CommandRegistryAccess access,
                                CommandManager.RegistrationEnvironment env) {
        d.register(CommandManager.literal("agent")
                .then(CommandManager.literal("mode")
                        .then(CommandManager.argument("mode", StringArgumentType.word())
                                .suggests((c, b) -> CommandSource.suggestMatching(List.of("follow", "stay", "collect"), b))
                                .executes(AgentCommands::mode)))
                .then(CommandManager.literal("fetch")
                        .then(CommandManager.argument("item", ItemStackArgumentType.itemStack(access))
                                .executes(c -> fetch(c, 64))
                                .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 576))
                                        .executes(c -> fetch(c, IntegerArgumentType.getInteger(c, "count"))))))
                .then(CommandManager.literal("give").executes(AgentCommands::give)));
    }

    private static AgentEntity find(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
        return p.getServerWorld()
                .getEntitiesByClass(AgentEntity.class, p.getBoundingBox().expand(64),
                        a -> p.getUuid().equals(a.getOwnerUuid()))
                .stream().min(Comparator.comparingDouble(a -> a.squaredDistanceTo(p))).orElse(null);
    }

    private static int msg(CommandContext<ServerCommandSource> c, String s, int r) {
        c.getSource().sendFeedback(() -> Text.literal(s), false);
        return r;
    }

    private static int mode(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        AgentEntity a = find(c);
        if (a == null) return msg(c, "Nenhum agente seu num raio de 64 blocos.", 0);
        String m = StringArgumentType.getString(c, "mode").toLowerCase(Locale.ROOT);
        switch (m) {
            case "follow" -> a.setMode(AgentEntity.Mode.FOLLOW);
            case "stay" -> a.setMode(AgentEntity.Mode.STAY);
            case "collect" -> a.setMode(AgentEntity.Mode.COLLECT);
            default -> { return msg(c, "Modos: follow, stay, collect", 0); }
        }
        return msg(c, "Modo: " + m, 1);
    }

    private static int fetch(CommandContext<ServerCommandSource> c, int count) throws CommandSyntaxException {
        AgentEntity a = find(c);
        if (a == null) return msg(c, "Nenhum agente seu num raio de 64 blocos.", 0);
        Item item = ItemStackArgumentType.getItemStackArgument(c, "item").getItem();
        boolean ok = a.startFetch(c.getSource().getPlayerOrThrow().getUuid(), item, count);
        return msg(c, ok ? "Agente indo buscar no baú." : "Nenhum baú com esse item num raio de 16 blocos do agente.", ok ? 1 : 0);
    }

    private static int give(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        AgentEntity a = find(c);
        if (a == null) return msg(c, "Nenhum agente seu num raio de 64 blocos.", 0);
        a.giveAllTo(c.getSource().getPlayerOrThrow());
        return msg(c, "Itens entregues.", 1);
    }
}
