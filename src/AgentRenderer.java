package com.agentmod;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.EyesFeatureRenderer;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.util.Identifier;

public class AgentRenderer extends MobEntityRenderer<AgentEntity, AgentModel> {
    private static final Identifier TEXTURE = Identifier.of(AgentMod.ID, "textures/entity/agent.png");

    public AgentRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new AgentModel(ctx.getPart(EntityModelLayers.PLAYER)), 0.5f);
        addFeature(new Eyes(this));
    }

    @Override
    public Identifier getTexture(AgentEntity entity) { return TEXTURE; }

    /** Olhos que brilham no escuro. */
    private static class Eyes extends EyesFeatureRenderer<AgentEntity, AgentModel> {
        private static final RenderLayer LAYER =
                RenderLayer.getEyes(Identifier.of(AgentMod.ID, "textures/entity/agent_eyes.png"));

        Eyes(FeatureRendererContext<AgentEntity, AgentModel> ctx) { super(ctx); }

        @Override
        public RenderLayer getEyesTexture() { return LAYER; }
    }
}
