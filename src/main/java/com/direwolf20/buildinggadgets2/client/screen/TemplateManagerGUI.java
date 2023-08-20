/**
 * Parts of this class were adapted from code written by TTerrag for the Chisel mod: https://github.com/Chisel-Team/Chisel
 * Chisel is Open Source and distributed under GNU GPL v2
 */

package com.direwolf20.buildinggadgets2.client.screen;

import com.direwolf20.buildinggadgets2.BuildingGadgets2;
import com.direwolf20.buildinggadgets2.client.renderer.VBORenderer;
import com.direwolf20.buildinggadgets2.common.blockentities.TemplateManagerBE;
import com.direwolf20.buildinggadgets2.common.containers.TemplateManagerContainer;
import com.direwolf20.buildinggadgets2.common.network.PacketHandler;
import com.direwolf20.buildinggadgets2.common.network.packets.PacketRequestCopyData;
import com.direwolf20.buildinggadgets2.common.network.packets.PacketSendCopyDataToServer;
import com.direwolf20.buildinggadgets2.common.network.packets.PacketUpdateTemplateManager;
import com.direwolf20.buildinggadgets2.common.worlddata.BG2Data;
import com.direwolf20.buildinggadgets2.common.worlddata.BG2DataClient;
import com.direwolf20.buildinggadgets2.util.GadgetNBT;
import com.direwolf20.buildinggadgets2.util.datatypes.StatePos;
import com.direwolf20.buildinggadgets2.util.datatypes.Template;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.widget.ExtendedButton;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.UUID;

public class TemplateManagerGUI extends AbstractContainerScreen<TemplateManagerContainer> {
    private static final ResourceLocation background = new ResourceLocation(BuildingGadgets2.MODID, "textures/gui/template_manager.png");

    private final Rect2i panel = new Rect2i((8 - 20), 12, 136, 80);
    private boolean panelClicked;
    private int clickButton, clickX, clickY;
    private float initRotX, initRotY, initZoom, initPanX, initPanY;
    private float momentumX, momentumY;
    private float rotX = 0, rotY = 0, zoom = 1;
    private float panX = 0, panY = 0;

    private EditBox nameField;
    private Button buttonSave, buttonLoad, buttonCopy, buttonPaste;

    private final TemplateManagerBE be;
    private final TemplateManagerContainer container;

    public TemplateManagerGUI(TemplateManagerContainer container, Inventory playerInventory, Component title) {
        super(container, playerInventory, Component.literal(""));

        this.container = container;
        this.be = container.getTe();
    }

    @Override
    public void init() {
        super.init();
        this.nameField = new EditBox(this.font, (this.leftPos - 20) + 8, topPos - 5, imageWidth - 16, this.font.lineHeight + 3, Component.translatable("buildinggadgets2.screen.namefieldtext"));

        int x = (leftPos - 20) + 180;
        buttonSave = new ExtendedButton(x, topPos + 17, 60, 20, Component.translatable("buildinggadgets2.buttons.save"), (button) -> {
            onSave();
        });
        buttonLoad = addRenderableWidget(Button.builder(Component.translatable("buildinggadgets2.buttons.load"), b -> onLoad()).pos(x, topPos + 39).size(60, 20).build());
        buttonCopy = addRenderableWidget(Button.builder(Component.translatable("buildinggadgets2.buttons.copy"), b -> onCopy()).pos(x, topPos + 66).size(60, 20).build());
        buttonPaste = addRenderableWidget(Button.builder(Component.translatable("buildinggadgets2.buttons.paste"), b -> onPaste()).pos(x, topPos + 89).size(60, 20).build());

        addRenderableWidget(buttonSave);
        this.nameField.setMaxLength(50);
        this.nameField.setVisible(true);
        addRenderableWidget(nameField);
        updateClientData(0);
        updateClientData(1);
    }

    public void updateClientData(int slot) {
        ItemStack itemStack = container.getSlot(slot).getItem();
        if (itemStack.isEmpty() || !GadgetNBT.hasCopyUUID(itemStack))
            return; //If the gadget hasn't copied anything yet, lets just bail out now!
        UUID itemUUID = GadgetNBT.getUUID(itemStack);
        UUID copyUUID = GadgetNBT.getCopyUUID(itemStack);
        UUID dataClientUUID = BG2DataClient.getCopyUUID(itemUUID);
        if (dataClientUUID != null && dataClientUUID.equals(copyUUID)) //If the Cache'd UUID of the copy/paste matches whats on the item, we don't need to request an update
            return;
        //Request an update for this item from the server, it'll be stored in the BG2DataClient class
        PacketHandler.sendToServer(new PacketRequestCopyData(itemUUID, copyUUID));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
        this.renderPanel(guiGraphics);
    }

    private void renderPanel(GuiGraphics guiGraphics) {
        double scale = getMinecraft().getWindow().getGuiScale();
        ItemStack gadget = container.getSlot(0).getItem();
        if (gadget.isEmpty()) return;
        ArrayList<StatePos> statePosCache = BG2DataClient.getLookupFromUUID(GadgetNBT.getUUID(gadget));
        if (statePosCache == null || statePosCache.isEmpty()) return;

        BlockPos startPos = statePosCache.get(0).pos;
        BlockPos endPos = statePosCache.get(statePosCache.size() - 1).pos;

        float lengthX = Math.abs(startPos.getX() - endPos.getX());
        float lengthY = Math.abs(startPos.getY() - endPos.getY());
        float lengthZ = Math.abs(startPos.getZ() - endPos.getZ());

        final float maxW = 6 * 16;
        final float maxH = 11 * 16;

        float overW = Math.max(lengthX * 16 - maxW, lengthZ * 16 - maxW);
        float overH = lengthY * 16 - maxH;

        float sc = 1;
        float zoomScale = 1;

        if (overW > 0 && overW >= overH) {
            sc = maxW / (overW + maxW);
            zoomScale = overW / 40;
        } else if (overH > 0 && overH >= overW) {
            sc = maxH / (overH + maxH);
            zoomScale = overH / 40;
        }

        int x1 = (int) Math.round((leftPos + panel.getX()) * scale);
        int y1 = (int) Math.round(getMinecraft().getWindow().getHeight() - (topPos + panel.getY() + panel.getHeight()) * scale);
        int x2 = (int) Math.round(panel.getWidth() * scale);
        int y2 = (int) Math.round(panel.getHeight() * scale);


        RenderSystem.viewport(x1, y1, x2, y2); //The viewport is like a mini world where things get drawn
        RenderSystem.backupProjectionMatrix();

        float fov = 60.0F;  // or whatever field of view you want
        float aspectRatio = (float) width / height;  // width and height of your GUI or the "viewport" you want to use
        float near = 0.1F;
        float far = 1000.0F;

        Matrix4f projectionMatrix = new Matrix4f();
        projectionMatrix.setPerspective((float) Math.toRadians(fov), aspectRatio, near, far);
        RenderSystem.setProjectionMatrix(projectionMatrix, VertexSorting.ORTHOGRAPHIC_Z); //This is needed to switch to 3d rendering instead of 2d for the screen

        PoseStack poseStack = RenderSystem.getModelViewStack();
        poseStack.pushPose();
        poseStack.setIdentity();
        poseStack.translate(-lengthZ / 2 + panX, -lengthY / 2 - panY, -lengthZ + zoom); //Move the objects in the world being drawn inside the viewport around
        poseStack.mulPose(new Quaternionf().setAngleAxis(rotX / 180 * (float) Math.PI, 1, 0, 0)); //Rotate
        poseStack.mulPose(new Quaternionf().setAngleAxis(rotY / 180 * (float) Math.PI, 0, 1, 0)); //Rotate
        RenderSystem.applyModelViewMatrix();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, false); //Clear the depth buffer so it can draw where it is

        VBORenderer.drawRender2(poseStack, BlockPos.ZERO, Minecraft.getInstance().player, container.getSlot(0).getItem()); //Draw VBO

        poseStack.popPose();

        RenderSystem.applyModelViewMatrix();
        RenderSystem.viewport(0, 0, getMinecraft().getWindow().getWidth(), getMinecraft().getWindow().getHeight());
        RenderSystem.restoreProjectionMatrix();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, false); //Clear the depth buffer so it can draw where it is
        RenderSystem.applyModelViewMatrix();
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int mouseX, int mouseY) {
        renderBackground(guiGraphics);

        guiGraphics.blit(background, leftPos - 20, topPos - 12, 0, 0, imageWidth, imageHeight + 25);
        guiGraphics.blit(background, (leftPos - 20) + imageWidth, topPos + 8, imageWidth + 3, 30, 71, imageHeight);

        if (!buttonCopy.isHovered() && !buttonPaste.isHovered()) {
            if (buttonLoad.isHovered())
                guiGraphics.blit(background, (leftPos + imageWidth) - 44, topPos + 38, imageWidth, 0, 17, 24);
            else
                guiGraphics.blit(background, (leftPos + imageWidth) - 44, topPos + 38, imageWidth + 17, 0, 16, 24);
        }

        this.nameField.render(guiGraphics, mouseX, mouseY, partialTicks);
    }

    private void resetViewport() {
        rotX = 0;
        rotY = 0;
        zoom = 1;
        momentumX = 0;
        momentumY = 0;
        panX = 0;
        panY = 0;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (panel.contains((int) mouseX - leftPos, (int) mouseY - topPos)) {
            clickButton = mouseButton;
            panelClicked = true;
            clickX = (int) getMinecraft().mouseHandler.xpos();
            clickY = (int) getMinecraft().mouseHandler.ypos();
        }

        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int state) {
        panelClicked = false;
        initRotX = rotX;
        initRotY = rotY;
        initPanX = panX;
        initPanY = panY;
        initZoom = zoom;

        return super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public boolean keyPressed(int p_keyPressed_1_, int p_keyPressed_2_, int p_keyPressed_3_) {
        if (p_keyPressed_1_ == 256) {
            this.onClose();
            return true;
        }

        return this.nameField.isFocused() ? this.nameField.keyPressed(p_keyPressed_1_, p_keyPressed_2_, p_keyPressed_3_) : super.keyPressed(p_keyPressed_1_, p_keyPressed_2_, p_keyPressed_3_);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (panelClicked) {
            if (clickButton == 0) {
                float prevRotX = rotX;
                float prevRotY = rotY;
                rotX = initRotX - ((int) getMinecraft().mouseHandler.ypos() - clickY);
                rotY = initRotY + ((int) getMinecraft().mouseHandler.xpos() - clickX);
                //momentumX = rotX - prevRotX;
                //momentumY = rotY - prevRotY;
            } else if (clickButton == 1) {
                panX = initPanX + ((int) getMinecraft().mouseHandler.xpos() - clickX) / 8f;
                panY = initPanY + ((int) getMinecraft().mouseHandler.ypos() - clickY) / 8f;
            } else if (clickButton == 2) {
                resetViewport();
            }
        }

        rotX += momentumX;
        rotY += momentumY;
        float momentumDampening = 0.98f;
        momentumX *= momentumDampening;
        momentumY *= momentumDampening;

        if (!nameField.isFocused() && nameField.getValue().isEmpty())
            guiGraphics.drawString(font, Component.translatable("buildinggadgets2.screen.templateplaceholder"), nameField.getX() - leftPos + 4, (nameField.getY() + 2) - topPos, -10197916);

        if (buttonSave.isHovered() || buttonLoad.isHovered() || buttonPaste.isHovered())
            drawSlotOverlay(guiGraphics, buttonLoad.isHovered() ? container.getSlot(0) : container.getSlot(1));


    }

    private void drawSlotOverlay(GuiGraphics guiGraphics, Slot slot) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 1000);
        guiGraphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, -1660903937);
        guiGraphics.pose().popPose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        zoom = initZoom + ((float) scrollDelta * 2);
        if (zoom < -200) zoom = -200;
        if (zoom > 5000) zoom = 5000;

        return super.mouseScrolled(mouseX, mouseY, scrollDelta);
    }

    @Override
    protected void containerTick() {
        super.containerTick();

        nameField.tick();
        if (!panelClicked) {
            initRotX = rotX;
            initRotY = rotY;
            initZoom = zoom;
            initPanX = panX;
            initPanY = panY;
        }
    }

    private void rename(ItemStack stack) {
        if (nameField.getValue().isEmpty())
            return;
        //TODO Implement Naming
    }

    private void onSave() {
        PacketHandler.sendToServer(new PacketUpdateTemplateManager(be.getBlockPos(), 0));
    }

    private void onLoad() {
        PacketHandler.sendToServer(new PacketUpdateTemplateManager(be.getBlockPos(), 1));
    }

    private void onCopy() {
        ItemStack templateStack = container.getSlot(1).getItem();
        if (templateStack.isEmpty()) return; //Todo messaging
        UUID templateUUID = GadgetNBT.getUUID(templateStack);
        ArrayList<StatePos> statePosCache = BG2DataClient.getLookupFromUUID(templateUUID);
        if (statePosCache == null || statePosCache.isEmpty()) return;
        Template template = new Template(nameField.getValue(), statePosCache);
        try {
            String json = template.toJson();
            getMinecraft().keyboardHandler.setClipboard(json);
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    private void onPaste() {
        assert getMinecraft().player != null;

        String CBString = getMinecraft().keyboardHandler.getClipboard();
        ArrayList<StatePos> statePosArrayList = new ArrayList<>();
        try {
            Template template = new Template(CBString);
            if (template.statePosArrayList.equals("")) return;
            CompoundTag deserializedNBT = TagParser.parseTag(template.statePosArrayList);
            statePosArrayList = BG2Data.statePosListFromNBTMapArray(deserializedNBT);
        } catch (CommandSyntaxException e) {
            // Handle the exception if the string isn't a valid NBT
            return;
        }
        if (statePosArrayList.isEmpty())
            return;
        //Todo JSON validations, Older JSON Versions
        CompoundTag serverTag = BG2Data.statePosListToNBTMapArray(statePosArrayList);
        PacketHandler.sendToServer(new PacketSendCopyDataToServer(serverTag));
    }

    public class GuiCamera extends Camera {
        public GuiCamera() {
        }

        @Override
        public void setPosition(double p_90585_, double p_90586_, double p_90587_) {
            super.setPosition(p_90585_, p_90586_, p_90587_);
        }
    }
}
