package com.agentmod;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.EyesFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.util.Identifier;

public class AgentRenderer extends MobEntityRenderer<AgentEntity, AgentModel> {
    private static final Identifier TEXTURE = Identifier.of(AgentMod.ID, "textures/entity/agent.png");

    public AgentRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new AgentModel(ctx.getPart(EntityModelLayers.PLAYER)), 0.5f);
        addFeature(new Eyes(this));
        addFeature(new HeldItemFeatureRenderer<>(this, ctx.getHeldItemRenderer()));   // mostra o bloco/item na mao
    }

    @Override
    public Identifier getTexture(AgentEntity entity) { return TEXTURE; }

    /** Sentado: desce o modelo para as pernas encostarem no chao (o corpo fica a 0,5 bloco do chao na pose em pe). */
    private static final double SIT_DROP = 0.5;

    @Override
    public void render(AgentEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();
        if (entity.isSitting()) matrices.translate(0.0, -SIT_DROP, 0.0);
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
        matrices.pop();
    }

    /** Olhos que brilham no escuro. */
    private static class Eyes extends EyesFeatureRenderer<AgentEntity, AgentModel> {
        private static final RenderLayer LAYER =
                RenderLayer.getEyes(Identifier.of(AgentMod.ID, "textures/entity/agent_eyes.png"));

        Eyes(FeatureRendererContext<AgentEntity, AgentModel> ctx) { super(ctx); }

        @Override
        public RenderLayer getEyesTexture() { return LAYER; }
    }
}
