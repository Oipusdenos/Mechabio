package com.agentmod;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;

/** Mesmo modelo do jogador, com poses extras: sentado e "carregando" (braços à frente). */
public class AgentModel extends PlayerEntityModel<AgentEntity> {
    public AgentModel(ModelPart root) { super(root, false); }

    @Override
    public void setAngles(AgentEntity e, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
        super.setAngles(e, limbAngle, limbDistance, age, headYaw, headPitch);
        if (e.isSitting()) {
            rightLeg.pitch = -1.4f; leftLeg.pitch = -1.4f;
            rightLeg.yaw = 0.25f;   leftLeg.yaw = -0.25f;
            rightArm.pitch = -0.3f; leftArm.pitch = -0.3f;
        } else if (e.isCarrying()) {
            rightArm.pitch = -0.9f; leftArm.pitch = -0.9f;
        }
        leftPants.copyTransform(leftLeg);   rightPants.copyTransform(rightLeg);
        leftSleeve.copyTransform(leftArm);  rightSleeve.copyTransform(rightArm);
    }
}
