package com.agentmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class AgentMod implements ModInitializer {
    public static final String ID = "agent";

    public static final EntityType<AgentEntity> AGENT = Registry.register(
            Registries.ENTITY_TYPE, Identifier.of(ID, "agent"),
            EntityType.Builder.create(AgentEntity::new, SpawnGroup.CREATURE)
                    .dimensions(0.6f, 1.8f).maxTrackingRange(10).build("agent"));

    public static final Item AGENT_EGG = Registry.register(Registries.ITEM, Identifier.of(ID, "agent_spawn_egg"),
            new SpawnEggItem(AGENT, 0xB87333, 0x3FB5A0, new Item.Settings()));

    public static final Item PATH_WAND = Registry.register(Registries.ITEM, Identifier.of(ID, "path_wand"),
            new PathWandItem(new Item.Settings().maxCount(1)));

    @Override
    public void onInitialize() {
        FabricDefaultAttributeRegistry.register(AGENT, AgentEntity.createAgentAttributes());
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(e -> e.add(AGENT_EGG));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(e -> e.add(PATH_WAND));
        CommandRegistrationCallback.EVENT.register(AgentCommands::register);
    }
}
