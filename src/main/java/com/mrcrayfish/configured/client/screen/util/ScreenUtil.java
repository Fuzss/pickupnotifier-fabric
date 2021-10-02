package com.mrcrayfish.configured.client.screen.util;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;

public class ScreenUtil {

    public static void renderCustomBackground(Screen screen, ResourceLocation background, int vOffset) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, background);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        float size = 32.0F;
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        builder.vertex(0.0D, screen.height, 0.0D).uv(0.0F, screen.height / size + vOffset).color(64, 64, 64, 255).endVertex();
        builder.vertex(screen.width, screen.height, 0.0D).uv(screen.width / size, screen.height / size + vOffset).color(64, 64, 64, 255).endVertex();
        builder.vertex(screen.width, 0.0D, 0.0D).uv(screen.width / size, vOffset).color(64, 64, 64, 255).endVertex();
        builder.vertex(0.0D, 0.0D, 0.0D).uv(0.0F, vOffset).color(64, 64, 64, 255).endVertex();
        tesselator.end();
    }
}
