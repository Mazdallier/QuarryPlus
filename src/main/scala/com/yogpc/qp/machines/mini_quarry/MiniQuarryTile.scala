package com.yogpc.qp.machines.mini_quarry

import java.util

import cats.implicits._
import com.mojang.serialization.Dynamic
import com.yogpc.qp._
import com.yogpc.qp.compat.InvUtils
import com.yogpc.qp.integration.ftbchunks.QuarryChunkProtectionManager
import com.yogpc.qp.machines.base._
import com.yogpc.qp.machines.modules.{IModuleItem, ItemFuelModule}
import com.yogpc.qp.machines.quarry.QuarryFakePlayer
import com.yogpc.qp.machines.{PowerManager, TranslationKeys}
import com.yogpc.qp.packet.{PacketHandler, TileMessage}
import com.yogpc.qp.utils.Holder
import net.minecraft.block.Block
import net.minecraft.entity.player.{PlayerEntity, PlayerInventory}
import net.minecraft.inventory.container.{Container, INamedContainerProvider}
import net.minecraft.inventory.{IInventory, InventoryHelper, ItemStackHelper}
import net.minecraft.item.ItemStack
import net.minecraft.nbt.{CompoundNBT, NBTDynamicOps}
import net.minecraft.network.play.server.SUpdateTileEntityPacket
import net.minecraft.state.properties.BlockStateProperties
import net.minecraft.util.math.{AxisAlignedBB, BlockPos}
import net.minecraft.util.text.ITextComponent
import net.minecraft.util.{Direction, Hand, NonNullList, ResourceLocation}
import net.minecraft.world.server.ServerWorld
import net.minecraftforge.api.distmarker.{Dist, OnlyIn}
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.util.Constants.NBT
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.common.{ForgeHooks, MinecraftForge}
import net.minecraftforge.event.world.BlockEvent

import scala.collection.AbstractIterator
import scala.jdk.CollectionConverters._

class MiniQuarryTile extends APowerTile(Holder.miniQuarryType)
  with EnchantmentHolder.EnchantmentProvider
  with IEnchantableTile
  with IRemotePowerOn
  with IChunkLoadTile
  with INamedContainerProvider
  with IDebugSender {
  private final var enchantments = EnchantmentHolder.noEnch
  private final var area = Area.zeroArea
  private final var targets: LazyList[BlockPos] = LazyList.empty
  private final val tools = NonNullList.withSize(5, ItemStack.EMPTY)
  private final var preDirection: Direction = Direction.UP
  final var rs = false
  final var renderBox = false
  final var blackList: Set[QuarryBlackList.Entry] = MiniQuarryTile.defaultBlackList
  final var whiteList: Set[QuarryBlackList.Entry] = Set()
  private[this] final val dropItem = (item: ItemStack) => {
    val rest = InvUtils.injectToNearTile(world, pos, item)
    InventoryHelper.spawnItemStack(world, pos.getX + 0.5, pos.getY + 1, pos.getZ + 0.5, rest)
  }

  override protected def workInTick(): Unit = {
    if (world.getGameTime % MiniQuarryTile.interval(enchantments.efficiency) == 0 &&
      PowerManager.useEnergy(this, MiniQuarryTile.e(enchantments.unbreaking), EnergyUsage.MINI_QUARRY)) {
      // Work
      val toolsWithoutModule = tools.asScala.filterNot(_.getItem.isInstanceOf[IModuleItem])

      @scala.annotation.tailrec
      def work(poses: LazyList[BlockPos]): LazyList[BlockPos] = {
        poses match {
          case LazyList.#::(head, tail) =>
            val world = getDiggingWorld
            val state = world.getBlockState(head)
            if (blackList.exists(_.test(state, world, head)) ||
              state.getBlockHardness(world, head) < 0 ||
              (whiteList.nonEmpty && !whiteList.exists(_.test(state, world, head)))) {
              // Unbreakable
              work(tail)
            } else {
              // Check if block harvest-able.
              val fakePlayer = QuarryFakePlayer.get(world, head)
              val canHarvest = toolsWithoutModule.exists { tool =>
                fakePlayer.setHeldItem(Hand.MAIN_HAND, tool)
                val event = new BlockEvent.BreakEvent(world, head, state, fakePlayer)
                MinecraftForge.EVENT_BUS.post(event)
                !event.isCanceled && (ForgeHooks.canHarvestBlock(state, fakePlayer, world, head) || ForgeHooks.isToolEffective(world, head, tool))
              }
              fakePlayer.setHeldItem(Hand.MAIN_HAND, ItemStack.EMPTY)
              // Use effective tool
              toolsWithoutModule.find(tool => ForgeHooks.isToolEffective(world, head, tool))
                .foreach(fakePlayer.setHeldItem(Hand.MAIN_HAND, _))
              if (canHarvest) {
                // Remove block
                val drops = Block.getDrops(state, world, head, world.getTileEntity(head), fakePlayer, fakePlayer.getHeldItemMainhand)
                drops.asScala.foreach(dropItem)
                val count = if (ForgeHooks.isToolEffective(world, head, fakePlayer.getHeldItemMainhand)) 1 else 4
                for (_ <- 0 until count)
                  fakePlayer.getHeldItemMainhand.onBlockDestroyed(world, state, head, fakePlayer)
                world.removeBlock(head, false)
                playSound(state, world, head)
                tail
              } else {
                // Skip this block
                tail
              }
            }
          case _ => LazyList.empty
        }
      }

      if (toolsWithoutModule.nonEmpty) {
        targets = work(targets)
        updateWorkingState()
      }
    }
  }

  override protected def getEnergyInTick(): Unit = {
    tools.asScala.map(i => (i.getItem, i)).collectFirst { case (f: ItemFuelModule, s) => f.getFuelModule(s) }.foreach {
      f => f.invoke(IModule.Tick(this))
    }
  }

  def getDiggingWorld: ServerWorld = {
    if (!super.getWorld.isRemote) {
      this.area.getWorld(super.getWorld.asInstanceOf[ServerWorld])
    } else {
      throw new IllegalStateException("Tried to get server world in client.")
    }
  }

  def gotRSPulse(): Unit = {
    if (isWorking) {
      startWaiting()
    } else {
      startWorking()
    }
  }

  def getInv: IInventory = Inv

  @OnlyIn(Dist.CLIENT)
  def renderAreaBox: Boolean = getBlockState.get(QPBlock.WORKING) && renderBox

  override protected def isWorking: Boolean = targets.nonEmpty

  override def getEnchantmentHolder: EnchantmentHolder = enchantments

  override def G_ReInit(): Unit = {
    PowerManager.configureQuarryWork(this, 0, 0, 0)
  }

  override def getEnchantments: util.Map[ResourceLocation, Integer] =
    EnchantmentHolder.getEnchantmentMap(enchantments).collect(enchantCollector).asJava

  override def setEnchantment(id: ResourceLocation, level: Short): Unit = enchantments =
    EnchantmentHolder.updateEnchantment(enchantments, id, level)

  override def setArea(area: Area): Unit = this.area = area

  override def startWorking(): Unit = {
    val facing = getBlockState.get(BlockStateProperties.FACING)
    val maybeMarkers = Area.getMarkersOnDirection(List(facing.getOpposite, facing.rotateY(), facing.rotateYCCW()), world, pos, ignoreHasLink = true)
    val areas = maybeMarkers.map(m => Area.posToArea(m.min(), m.max(), world.getDimensionKey) -> m)
      .collectFirst(t => t)
    areas match {
      case Some((newArea, m)) =>
        area = newArea
        m.removeFromWorldWithItem().asScala.foreach(dropItem)
        if (!world.isRemote) PacketHandler.sendToClient(TileMessage.create(this), world)
      case _ =>
    }
    QuarryChunkProtectionManager.sendProtectionNotification(QuarryFakePlayer.get(getWorld.asInstanceOf[ServerWorld], getPos)).accept(this)
    updateTargets(facing.getOpposite)
  }

  private def updateTargets(d: Direction): Unit = {
    if (area == Area.zeroArea) {
      targets = LazyList.empty
    } else {
      preDirection = d
      val poses = for {
        y <- LazyList.from(area.yMax.to(area.yMin, -1))
        (x, z) <- MiniQuarryTile.makeTargetsXZ(area, d) to LazyList
      } yield new BlockPos(x, y, z)
      targets = poses
    }
  }

  override def startWaiting(): Unit = {
    targets = LazyList.empty
    updateWorkingState()
  }

  override def getArea: Area = area

  override def createMenu(id: Int, i: PlayerInventory, player: PlayerEntity): Container = new MiniQuarryContainer(id, player, pos)

  override def getDebugName: String = TranslationKeys.mini_quarry

  override def getDebugMessages: util.List[_ <: ITextComponent] = Seq(
    s"Area: $area}",
    s"TargetSize: ${targets.size}",
  ).map(toComponentString).asJava

  override def getDisplayName: ITextComponent = super.getDisplayName

  override def remove(): Unit = {
    super[IChunkLoadTile].releaseTicket()
    super.remove()
  }

  override def read(nbt: CompoundNBT): Unit = {
    super.read(nbt)
    this.area = Area.areaLoad(nbt.getCompound("area"))
    this.enchantments = EnchantmentHolder.enchantmentHolderLoad(nbt, "enchantments")
    this.preDirection = Direction.byName(nbt.getString("preDirection"))
    val working = nbt.contains("head")
    if (working) {
      updateTargets(preDirection)
      val head = BlockPos.fromLong(nbt.getLong("head"))
      this.targets = targets.dropWhile(_ == head)
    }
    ItemStackHelper.loadAllItems(nbt.getCompound("tools"), tools)
    this.blackList = nbt.getList("blackList", NBT.TAG_COMPOUND).asScala
      .map(n => QuarryBlackList.readEntry(new Dynamic(NBTDynamicOps.INSTANCE, n))).toSet
    this.whiteList = nbt.getList("whiteList", NBT.TAG_COMPOUND).asScala
      .map(n => QuarryBlackList.readEntry(new Dynamic(NBTDynamicOps.INSTANCE, n))).toSet
    this.rs = nbt.getBoolean("rs")
    this.renderBox = nbt.getBoolean("renderBox")
  }

  override def write(nbt: CompoundNBT): CompoundNBT = {
    nbt.put("area", area.toNBT)
    nbt.put("enchantments", enchantments.toNBT)
    nbt.putString("preDirection", preDirection.getString)
    targets.headOption.foreach(p => nbt.putLong("head", p.toLong))
    nbt.put("tools", ItemStackHelper.saveAllItems(new CompoundNBT(), tools))
    nbt.put("blackList", NBTDynamicOps.INSTANCE.createList(blackList.asJava.stream().map(QuarryBlackList.writeEntry(_, NBTDynamicOps.INSTANCE))))
    nbt.put("whiteList", NBTDynamicOps.INSTANCE.createList(whiteList.asJava.stream().map(QuarryBlackList.writeEntry(_, NBTDynamicOps.INSTANCE))))
    nbt.putBoolean("rs", rs)
    nbt.putBoolean("renderBox", renderBox)
    super.write(nbt)
  }

  override def getRenderBoundingBox: AxisAlignedBB = {
    if (area != Area.zeroArea) Area.areaBox(area)
    else super.getRenderBoundingBox
  }

  override def getMaxRenderDistanceSquared: Double = {
    if (area != Area.zeroArea) Area.areaLengthSq(area)
    else super.getMaxRenderDistanceSquared
  }

  override protected def enabledByRS = true

  override def getCapability[T](cap: Capability[T], side: Direction): LazyOptional[T] = {
    Cap.asJava(Cap.make(cap, this, IRemotePowerOn.Cap.REMOTE_CAPABILITY()) orElse Cap.dummyItemOrFluid(cap) orElse super.getCapability(cap, side).asScala)
  }

  override def getUpdatePacket: SUpdateTileEntityPacket = null

  private object Inv extends IInventory {
    override def getSizeInventory: Int = tools.size()

    override def isEmpty: Boolean = tools.stream().allMatch(_.isEmpty)

    override def getStackInSlot(index: Int): ItemStack = tools.get(index)

    override def decrStackSize(index: Int, count: Int): ItemStack = ItemStackHelper.getAndSplit(tools, index, count)

    override def removeStackFromSlot(index: Int): ItemStack = ItemStackHelper.getAndRemove(tools, index)

    override def setInventorySlotContents(index: Int, stack: ItemStack): Unit = tools.set(index, stack)

    override def markDirty(): Unit = MiniQuarryTile.this.markDirty()

    override def isUsableByPlayer(player: PlayerEntity): Boolean = true

    override def clear(): Unit = tools.clear()

    override def getInventoryStackLimit: Int = 1
  }

}

object MiniQuarryTile {
  final val SYMBOL = Symbol("MiniQuarry")
  final val defaultBlackList = Set(QuarryBlackList.Air, QuarryBlackList.Fluids)

  def e(unbreaking: Int): Long = APowerTile.FEtoMicroJ * 20 / (unbreaking + 1)

  def interval(efficiency: Int): Int = {
    if (efficiency < 0) return 100
    efficiency match {
      case 0 => 40
      case 1 => 30
      case 2 => 20
      case 3 => 10
      case 4 => 5
      case 5 => 2
      case _ => 1
    }
  }

  def makeTargetsXZ(area: Area, direction: Direction): Iterator[(Int, Int)] = {
    val (start, end) = direction match {
      case Direction.NORTH | Direction.DOWN | Direction.UP => (area.xMax, area.zMin) -> (area.xMin, area.zMax)
      case Direction.SOUTH => (area.xMin, area.zMax) -> (area.xMax, area.zMin)
      case Direction.WEST => (area.xMin, area.zMin) -> (area.xMax, area.zMax)
      case Direction.EAST => (area.xMax, area.zMax) -> (area.xMin, area.zMin)
    }
    val vec@(vx, vz) = end |-| start
    val (vec1, vec2) = direction.getAxis match {
      case Direction.Axis.X => vec.bimap(_.sign, _ => 0) -> vec.bimap(_ => 0, _.sign)
      case Direction.Axis.Z => vec.bimap(_ => 0, _.sign) -> vec.bimap(_.sign, _ => 0)
      case _ => (0, 1) -> (-1, 0)
    }
    val dot1 = vx * vec1._1 + vz * vec1._2
    val dot2 = vx * vec2._1 + vz * vec2._2
    new AbstractIterator[(Int, Int)] {
      var count1 = 0
      var count2 = 0
      var end = false

      override def hasNext: Boolean = !end

      override def next(): (Int, Int) = {
        val result = start |+| (vec1 combineN count1) |+| (vec2 combineN count2)
        if (count1 + 1 > dot1) {
          count1 = 0
          count2 += 1
          if (count2 == dot2 + 1) end = true
        } else {
          count1 += 1
        }
        result
      }
    }
  }
}
