package com.yogpc.qp.machines.advquarry

import com.yogpc.qp.machines.base.EnergyUsage
import com.yogpc.qp.{Config, QuarryPlus}
import net.minecraft.block.state.IBlockState
import net.minecraft.util.math.BlockPos
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.fml.loading.FMLEnvironment

trait Reason {
  def isEnergyIssue: Boolean

  def usage: Option[EnergyUsage] = None

  override def toString: String

  def print(): Unit = QuarryPlus.LOGGER.info(toString)
}

object Reason {

  private[this] final val Nano = 1000000000l
  private[this] final val toNano = 1000000000l

  def apply(energyUsage: EnergyUsage, required: Double, amount: Double): Reason =
    new EnergyReasonImpl(energyUsage, (required * toNano).toLong, (amount * toNano).toLong)

  def apply(energyUsage: EnergyUsage, required: Double, amount: Double, index: Int): Reason =
    new EnergyReasonImpl(energyUsage, (required * toNano).toLong, (amount * toNano).toLong, Some(index))

  def apply(pos: BlockPos, state: IBlockState): Reason = new BreakCanceledImpl(pos, state)

  def apply(pos: BlockPos, index: Int) = new AllAirImpl(pos, index)

  def printNonEnergy[T]: Reason => Option[T] = r => {
    if (Config.common.debug && FMLEnvironment.dist == Dist.CLIENT && !r.isEnergyIssue) {
      r.print()
    }
    None
  }

  def print[T]: Reason => Option[T] = r => {
    if (Config.common.debug && FMLEnvironment.dist == Dist.CLIENT) {
      r.print()
    }
    None
  }

  private[advquarry] class EnergyReasonImpl(energyUsage: EnergyUsage, required: Long, amount: Long, index: Option[Int] = None) extends Reason {
    override def isEnergyIssue: Boolean = true

    override def usage: Option[EnergyUsage] = Some(energyUsage)

    override def toString: String = {
      val iString = index.fold("")(i => s" Index = $i")
      s"Action of $energyUsage required ${required * 10 / Nano} RF but machine has ${amount * 10 / Nano} RF." + iString
    }
  }

  private[advquarry] class BreakCanceledImpl(pos: BlockPos, state: IBlockState) extends Reason {
    override def isEnergyIssue: Boolean = false

    override def toString: String = s"Breaking $state at ${pos.getX}, ${pos.getY}, ${pos.getZ} was canceled."
  }

  private[advquarry] class AllAirImpl(pos: BlockPos, index: Int) extends Reason {
    override def isEnergyIssue: Boolean = false

    override def toString: String = s"x = ${pos.getX}, z = ${pos.getZ} has no blocks. index = $index"

    override def print(): Unit = ()
  }

}