package com.yogpc.qp.machines.advquarry

import com.mojang.blaze3d.matrix.MatrixStack
import com.yogpc.qp.QuarryPlus
import com.yogpc.qp.machines.base.{Area, IHandleButton, ScreenUtil}
import com.yogpc.qp.packet.PacketHandler
import com.yogpc.qp.packet.advquarry.AdvActionMessage
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.inventory.ContainerScreen
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.util.math.ChunkPos
import net.minecraft.util.text.{ITextComponent, StringTextComponent}
import net.minecraft.util.{Direction, ResourceLocation}
import net.minecraftforge.api.distmarker.{Dist, OnlyIn}

@OnlyIn(Dist.CLIENT)
class GuiAdvQuarry(c: ContainerAdvQuarry, i: PlayerInventory, t: ITextComponent) extends ContainerScreen[ContainerAdvQuarry](c, i, t) with IHandleButton {
  val tile: TileAdvQuarry = getContainer.tile
  val LOCATION = new ResourceLocation(QuarryPlus.modID, "textures/gui/chunkdestroyer.png")

  //7,15 to 168,70 box : 162, 56

  override def render(matrixStack: MatrixStack, mouseX: Int, mouseY: Int, partialTicks: Float): Unit = { // render
    this.renderBackground(matrixStack) // back ground
    super.render(matrixStack, mouseX, mouseY, partialTicks) // super.render
    this.renderHoveredTooltip(matrixStack, mouseX, mouseY) // render tooltip
  }

  override def drawGuiContainerBackgroundLayer(matrix: MatrixStack, partialTicks: Float, mouseX: Int, mouseY: Int): Unit = {
    ScreenUtil.color4f()
    this.getMinecraft.getTextureManager.bindTexture(LOCATION)
    this.blit(matrix, guiLeft, guiTop, 0, 0, xSize, ySize)
  }

  override def init(): Unit = {
    super.init()
    val plus = new StringTextComponent("+")
    val minus = new StringTextComponent("-")
    addButton(new IHandleButton.Button(0, guiLeft + 98, guiTop + 16, 10, 8, plus, this))
    addButton(new IHandleButton.Button(1, guiLeft + 68, guiTop + 16, 10, 8, minus, this))
    addButton(new IHandleButton.Button(2, guiLeft + 98, guiTop + 62, 10, 8, plus, this))
    addButton(new IHandleButton.Button(3, guiLeft + 68, guiTop + 62, 10, 8, minus, this))
    addButton(new IHandleButton.Button(4, guiLeft + 38, guiTop + 39, 10, 8, plus, this))
    addButton(new IHandleButton.Button(5, guiLeft + 8, guiTop + 39, 10, 8, minus, this))
    addButton(new IHandleButton.Button(6, guiLeft + 158, guiTop + 39, 10, 8, plus, this))
    addButton(new IHandleButton.Button(7, guiLeft + 128, guiTop + 39, 10, 8, minus, this))

    addButton(new IHandleButton.Button(8, guiLeft + 108, guiTop + 58, 60, 12, new StringTextComponent("No Frame"), this))
    addButton(new IHandleButton.Button(9, guiLeft + 8, guiTop + 58, 60, 12, new StringTextComponent("Modules"), this))
  }

  private def range = tile.area

  override def actionPerformed(button: IHandleButton.Button): Unit = {
    if (button.id == 8) {
      if (tile.action == AdvQuarryWork.waiting) {
        PacketHandler.sendToServer(AdvActionMessage.create(tile, AdvActionMessage.Actions.QUICK_START))
      }
    } else if (button.id == 9) {
      PacketHandler.sendToServer(AdvActionMessage.create(tile, AdvActionMessage.Actions.MODULE_INV))
      //      onClose()
    } else if (tile.action == AdvQuarryWork.waiting) {
      val direction = Direction.byIndex(button.id / 2 + 2)
      val increase = if (button.id % 2 == 0) 1 else -1
      val shift = Screen.hasShiftDown
      val ctrl = Screen.hasControlDown
      val t = (if (shift && ctrl) 1024 else if (shift) 256 else if (ctrl) 64 else 16) * increase

      if (range != Area.zeroArea) {
        val newRange =
          if (direction.getAxis == Direction.Axis.X) {
            if (direction.getAxisDirection == Direction.AxisDirection.POSITIVE) {
              val e = range.xMax
              if (range.xMin < e + t) range.copy(xMax = e + t) else range
            } else {
              val e = range.xMin
              if (range.xMax > e - t) range.copy(xMin = e - t) else range
            }
          } else if (direction.getAxis == Direction.Axis.Z) {
            if (direction.getAxisDirection == Direction.AxisDirection.POSITIVE) {
              val e = range.zMax
              if (range.zMin < e + t) range.copy(zMax = e + t) else range
            } else {
              val e = range.zMin
              if (range.zMax > e - t) range.copy(zMin = e - t) else range
            }
          } else range
        tile.setArea(newRange)
        PacketHandler.sendToServer(AdvActionMessage.create(tile, AdvActionMessage.Actions.CHANGE_RANGE, tile.area.toNBT))
      }
    }
  }

  override def drawGuiContainerForegroundLayer(matrix: MatrixStack, mouseX: Int, mouseY: Int): Unit = {
    super.drawGuiContainerForegroundLayer(matrix, mouseX, mouseY)
    if (range != Area.zeroArea) {
      val chunkPos = new ChunkPos(tile.getPos)
      val north: Double = chunkPos.getZStart - range.zMin
      val south: Double = range.zMax - chunkPos.getZEnd
      val east: Double = range.xMax - chunkPos.getXEnd
      val west: Double = chunkPos.getXStart - range.xMin
      this.font.drawString(matrix, (north / 16).toString, 79, 17, 0x404040)
      this.font.drawString(matrix, (south / 16).toString, 79, 63, 0x404040)
      this.font.drawString(matrix, (west / 16).toString, 19, 40, 0x404040)
      this.font.drawString(matrix, (east / 16).toString, 139, 40, 0x404040)
    }
  }
}
